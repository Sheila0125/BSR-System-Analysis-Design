package com.bsr.acceptance;

import static org.junit.jupiter.api.Assertions.*;

import com.bsr.customerrequest.application.CustomerRequestService;
import com.bsr.customerrequest.domain.RentalRequestItem;
import com.bsr.equipment.api.InspectionOutcome;
import com.bsr.identity.api.*;
import com.bsr.platform.error.ApiException;
import com.bsr.rental.application.*;
import com.bsr.rental.domain.*;
import com.bsr.schema.IsolatedTestDatabaseInitializer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(properties = {"spring.profiles.active=test", "spring.jpa.hibernate.ddl-auto=validate"})
@ContextConfiguration(initializers = IsolatedTestDatabaseInitializer.class)
class RentalLifecycleAcceptanceIT {
    @Autowired CustomerRequestService requests;
    @Autowired RentalService rentals;
    @Autowired TransactionService payments;
    @Autowired ChangeRequestService changes;
    @Autowired AdjustmentService adjustments;
    @Autowired JdbcTemplate jdbc;

    private final ActorContext employee = new ActorContext(41004, "acceptance-employee", IdentityType.EMPLOYEE,
            41003, List.of(new ActiveRoleGrant(RoleCode.EMPLOYEE, 41001L)));
    private final ActorContext customer = new ActorContext(41005, "acceptance-customer", IdentityType.CUSTOMER,
            41002, List.of(new ActiveRoleGrant(RoleCode.CUSTOMER, null)));

    @BeforeEach void seed() {
        cleanup();
        jdbc.update("INSERT INTO store(store_id,store_number,store_name,business_status,opening_time,closing_time) VALUES(41001,'A10-S','Acceptance store','ACTIVE','08:00','22:00')");
        jdbc.update("INSERT INTO customer(customer_id,customer_number,full_name,email,phone) VALUES(41002,'A10-C','Fictional customer','a10-c@test.invalid','+47000041002')");
        jdbc.update("INSERT INTO employee(employee_id,employee_number,full_name,email,business_status) VALUES(41003,'A10-E','Fictional employee','a10-e@test.invalid','ACTIVE')");
        jdbc.update("INSERT INTO account(account_id,login_name,password_hash,enabled,employee_id) VALUES(41004,'acceptance-employee','{noop}unused',TRUE,41003)");
        jdbc.update("INSERT INTO account(account_id,login_name,password_hash,enabled,customer_id) VALUES(41005,'acceptance-customer','{noop}unused',TRUE,41002)");
        jdbc.update("INSERT INTO role_grant(account_id,role_code,store_id,valid_from) VALUES(41004,'EMPLOYEE',41001,'2020-01-01'),(41005,'CUSTOMER',NULL,'2020-01-01')");
        jdbc.update("""
            INSERT INTO equipment(equipment_id,inventory_number,equipment_name,equipment_category,skill_level,
                home_store_id,current_store_id,equipment_status,purchase_date,purchase_price,current_daily_rate)
            VALUES(41006,'A10-SKI','Acceptance ski','SKI','INTERMEDIATE',41001,41001,'AVAILABLE','2025-01-01',900,99.50),
                  (41007,'A10-BOOT','Acceptance boot','BOOT','INTERMEDIATE',41001,41001,'AVAILABLE','2025-01-01',400,40.00)
            """);
    }

    @Test
    @DisplayName("AC-UC01 AC-UC04 AC-UC05 AC-UC06 AC-BR17 AC-BR19 AC-BR20 real request-to-settlement preserves attempts, returns and pending refunds")
    void requestToFinalSettlement() {
        var request = requests.submit(customer, new CustomerRequestService.Submission(41001,
                LocalDate.now(ZoneId.of("Europe/Oslo")), 3, "INTERMEDIATE",
                List.of(new RentalRequestItem("SKI", 1, null), new RentalRequestItem("BOOT", 1, null)), false));
        assertEquals(2, count("SELECT COUNT(*) FROM equipment WHERE equipment_id IN(41006,41007) AND equipment_status='AVAILABLE'"));
        assertEquals("A10-C", requests.findCustomerForEmployee(employee, 41001, "A10-C", null).customerNumber());
        requests.accept(employee, request.requestId());
        var contract = rentals.create(employee, 41001, request.requestId(), List.of(
                new RentalService.Selection(41006, new BigDecimal("99.50")),
                new RentalService.Selection(41007, new BigDecimal("40.00"))));
        assertEquals(new BigDecimal("418.50"), contract.contract().amountDue());
        rentals.submit(employee, contract.id());
        rentals.decide(customer, contract.id(), true);
        var confirmed = rentals.read(customer, contract.id());
        long charge = confirmed.charges().getFirst().id();
        var items = confirmed.items().stream().map(RentalPersistence.StoredItem::id).toList();
        assertEquals("RENTAL_STATE_CONFLICT", assertThrows(ApiException.class,
                () -> rentals.issue(employee, contract.id(), items)).code());

        payments.record(employee, result(charge, "A10-UNKNOWN", "418.50", ExternalTransactionResult.Status.PENDING_CONFIRMATION));
        payments.record(employee, result(charge, "A10-FAILED", "418.50", ExternalTransactionResult.Status.FAILED));
        assertEquals(RentalContractStatus.PENDING_PAYMENT, rentals.read(customer, contract.id()).contract().status());
        assertEquals(new BigDecimal("418.50"), rentals.read(customer, contract.id()).charges().getFirst().amount());
        var successful = result(charge, "A10-PAID", "418.50", ExternalTransactionResult.Status.SUCCEEDED);
        long transactionId = payments.record(employee, successful).id();
        assertEquals(transactionId, payments.record(employee, successful).id());
        assertEquals(3, payments.list(customer, charge).size());
        assertEquals(new BigDecimal("418.50"), jdbc.queryForObject("SELECT SUM(amount) FROM payment_transaction WHERE rental_charge_id=? AND transaction_status='SUCCEEDED'", BigDecimal.class, charge));
        rentals.issue(employee, contract.id(), items);

        var boot = confirmed.items().stream().filter(i -> i.equipmentId() == 41007).findFirst().orElseThrow();
        var ski = confirmed.items().stream().filter(i -> i.equipmentId() == 41006).findFirst().orElseThrow();
        rentals.returnItems(employee, contract.id(), 41001, List.of(new RentalService.ReturnItem(boot.id(), InspectionOutcome.NORMAL, null)));
        assertEquals(1, rentals.read(customer, contract.id()).charges().size(), "Ordinary early return does not create a refund");
        var change = changes.submit(customer, contract.id(), 41001, RentalChangeRequest.Type.REMOVE, "Formal future-day reduction");
        adjustments.receive(employee, change.id(), List.of(new RentalService.ReturnItem(ski.id(), InspectionOutcome.NORMAL, null)));
        assertEquals(new BigDecimal("-199.00"), adjustments.quote(employee, change.id(), List.of()).amountDelta());
        adjustments.decide(customer, change.id(), true);
        var reduced = rentals.read(customer, contract.id());
        var refund = reduced.charges().stream().filter(c -> c.type().equals("REFUND")).findFirst().orElseThrow();
        assertEquals(new BigDecimal("219.50"), reduced.contract().amountDue());
        assertEquals(RentalContractStatus.PENDING_SETTLEMENT, reduced.contract().status());
        int inspections = count("SELECT COUNT(*) FROM equipment_inspection WHERE equipment_id IN(41006,41007)");
        payments.record(employee, result(refund.id(), "A10-REFUND-FAIL", "199.00", ExternalTransactionResult.Status.FAILED));
        var failed = rentals.read(customer, contract.id());
        assertEquals(RentalContractStatus.PENDING_SETTLEMENT, failed.contract().status());
        assertTrue(failed.items().stream().allMatch(i -> i.status().equals("RETURNED") && i.returnedAt() != null));
        assertEquals("PENDING_REFUND", failed.charges().stream().filter(c -> c.id() == refund.id()).findFirst().orElseThrow().status());
        assertEquals(inspections, count("SELECT COUNT(*) FROM equipment_inspection WHERE equipment_id IN(41006,41007)"));
        assertEquals(2, count("SELECT COUNT(*) FROM equipment WHERE equipment_id IN(41006,41007) AND equipment_status='AVAILABLE'"));
        payments.record(employee, result(refund.id(), "A10-REFUNDED", "199.00", ExternalTransactionResult.Status.SUCCEEDED));
        var ended = rentals.read(customer, contract.id());
        assertEquals(RentalContractStatus.ENDED, ended.contract().status());
        assertEquals(2, ended.items().size());
        assertEquals(2, payments.list(customer, refund.id()).size());
        assertEquals(5, count("SELECT COUNT(*) FROM audit_event WHERE actor_account_id=41004 AND action_code='EXTERNAL_RESULT_RECORDED'"));
    }

    private ExternalTransactionResult result(long charge, String externalId, String amount, ExternalTransactionResult.Status status) {
        return new ExternalTransactionResult(charge, externalId.contains("REFUND") ? ExternalTransactionResult.Type.REFUND : ExternalTransactionResult.Type.PAYMENT,
                externalId, new BigDecimal(amount), status, status == ExternalTransactionResult.Status.SUCCEEDED ? LocalDateTime.of(2026,10,20,8,0) : null);
    }
    private int count(String sql) { return jdbc.queryForObject(sql, Integer.class); }

    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM audit_event WHERE actor_account_id IN(41004,41005)");
        jdbc.update("DELETE FROM payment_transaction WHERE external_transaction_id LIKE 'A10-%'");
        jdbc.update("DELETE FROM rental_charge WHERE contract_id IN(SELECT contract_id FROM rental_contract WHERE customer_id=41002)");
        jdbc.update("DELETE FROM rental_change_item WHERE rental_change_id IN(SELECT rental_change_id FROM rental_change WHERE contract_id IN(SELECT contract_id FROM rental_contract WHERE customer_id=41002))");
        jdbc.update("DELETE FROM rental_change WHERE contract_id IN(SELECT contract_id FROM rental_contract WHERE customer_id=41002)");
        jdbc.update("DELETE FROM equipment_inspection WHERE equipment_id IN(41006,41007)");
        jdbc.update("DELETE FROM rental_item WHERE contract_id IN(SELECT contract_id FROM rental_contract WHERE customer_id=41002)");
        jdbc.update("DELETE FROM rental_contract WHERE customer_id=41002");
        jdbc.update("DELETE FROM rental_request_item WHERE request_id IN(SELECT request_id FROM rental_request WHERE customer_id=41002)");
        jdbc.update("DELETE FROM rental_request WHERE customer_id=41002");
        jdbc.update("DELETE FROM equipment WHERE equipment_id IN(41006,41007)");
        jdbc.update("DELETE FROM role_grant WHERE account_id IN(41004,41005)");
        jdbc.update("DELETE FROM account WHERE account_id IN(41004,41005)");
        jdbc.update("DELETE FROM customer WHERE customer_id=41002");
        jdbc.update("DELETE FROM employee WHERE employee_id=41003");
        jdbc.update("DELETE FROM store WHERE store_id=41001");
    }
}
