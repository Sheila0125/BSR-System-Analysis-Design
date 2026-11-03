package com.bsr.customerrequest.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bsr.customerrequest.api.CustomerRequestOperations;
import com.bsr.customerrequest.domain.RentalRequestItem;
import com.bsr.customerrequest.domain.RentalRequestStatus;
import com.bsr.identity.api.ActiveRoleGrant;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.IdentityType;
import com.bsr.identity.api.RoleCode;
import com.bsr.platform.error.ApiException;
import com.bsr.schema.IsolatedTestDatabaseInitializer;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
    "spring.profiles.active=test",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@ContextConfiguration(initializers = IsolatedTestDatabaseInitializer.class)
class CustomerRequestOperationsIT {

    private static final long STORE_ID = 27_101L;
    private static final long OTHER_STORE_ID = 27_102L;
    private static final long CUSTOMER_ID = 27_103L;
    private static final long EMPLOYEE_ID = 27_104L;
    private static final long CUSTOMER_ACCOUNT_ID = 27_105L;
    private static final long EMPLOYEE_ACCOUNT_ID = 27_106L;

    @Autowired
    private CustomerRequestOperations operations;

    @Autowired
    private CustomerRequestService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        cleanup();
        jdbcTemplate.update("""
                INSERT INTO store (store_id, store_number, store_name, business_status, opening_time, closing_time)
                VALUES (?, 'S-CONV-1', 'Conversion Store', 'ACTIVE', '08:00:00', '22:00:00'),
                       (?, 'S-CONV-2', 'Other Conversion Store', 'ACTIVE', '08:00:00', '22:00:00')
                """, STORE_ID, OTHER_STORE_ID);
        jdbcTemplate.update("""
                INSERT INTO customer (customer_id, customer_number, full_name, email, phone,
                    height_estimated, weight_estimated, boot_size_estimated)
                VALUES (?, 'C-CONV-1', 'Conversion Customer', 'conversion@test.invalid', '+47000027103',
                    FALSE, FALSE, FALSE)
                """, CUSTOMER_ID);
        jdbcTemplate.update("""
                INSERT INTO employee (employee_id, employee_number, full_name, email, business_status)
                VALUES (?, 'E-CONV-1', 'Conversion Employee', 'conversion-employee@test.invalid', 'ACTIVE')
                """, EMPLOYEE_ID);
        jdbcTemplate.update("""
                INSERT INTO account (account_id, login_name, password_hash, enabled, customer_id)
                VALUES (?, 'conversion-customer', '{noop}unused', TRUE, ?)
                """, CUSTOMER_ACCOUNT_ID, CUSTOMER_ID);
        jdbcTemplate.update("""
                INSERT INTO account (account_id, login_name, password_hash, enabled, employee_id)
                VALUES (?, 'conversion-employee', '{noop}unused', TRUE, ?)
                """, EMPLOYEE_ACCOUNT_ID, EMPLOYEE_ID);
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    @DisplayName("AC-UC04 request API preserves current skill/items and enforces customer and employee scopes")
    void acUc04SnapshotAndScope() {
        long requestId = submit();
        var owned = operations.loadOwned(customer(), requestId);
        assertEquals("INTERMEDIATE", owned.skillLevel());
        assertEquals(2, owned.items().getFirst().quantity());
        assertEquals(CUSTOMER_ID, operations.verifyCustomer(employee(STORE_ID), STORE_ID,
                "C-CONV-1", null).customerId());

        ApiException denied = assertThrows(ApiException.class,
                () -> operations.accept(employee(OTHER_STORE_ID), requestId));
        assertEquals("ACTION_NOT_AUTHORIZED", denied.code());
        assertEquals(RentalRequestStatus.ACCEPTED, operations.accept(employee(STORE_ID), requestId).status());
        assertEquals("ACCEPTED", status(requestId));
    }

    @Test
    @DisplayName("AC-UC04 accepted request and one matching source contract convert with success audit")
    void acUc04MatchingContractConvertsOnce() {
        long requestId = acceptedRequest();
        long contractId = transaction().execute(ignored -> {
            var locked = operations.lockAcceptedForConversion(employee(STORE_ID), requestId);
            assertEquals(RentalRequestStatus.ACCEPTED, locked.status());
            long created = insertContract(requestId, STORE_ID);
            assertEquals(RentalRequestStatus.CONVERTED,
                    operations.markConverted(employee(STORE_ID), requestId, created).status());
            return created;
        });

        assertEquals("CONVERTED", status(requestId));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM audit_event
                WHERE object_type = 'RENTAL_REQUEST' AND object_id = ?
                  AND action_code = 'RENTAL_REQUEST_CONVERTED' AND result_code = 'SUCCESS'
                """, Integer.class, requestId));
        ApiException duplicate = assertThrows(ApiException.class,
                () -> transaction().execute(ignored ->
                        operations.markConverted(employee(STORE_ID), requestId, contractId)));
        assertEquals("RENTAL_REQUEST_STATE_CONFLICT", duplicate.code());
    }

    @Test
    @DisplayName("AC-BR05 mismatched contract store cannot convert an accepted request")
    void acBr05MismatchedContractDoesNotConvert() {
        long requestId = acceptedRequest();
        ApiException mismatch = assertThrows(ApiException.class,
                () -> transaction().execute(ignored -> {
                    operations.lockAcceptedForConversion(employee(STORE_ID), requestId);
                    long contractId = insertContract(requestId, OTHER_STORE_ID);
                    return operations.markConverted(employee(STORE_ID), requestId, contractId);
                }));

        assertEquals("RENTAL_REQUEST_CONTRACT_MISMATCH", mismatch.code());
        assertEquals("ACCEPTED", status(requestId));
        assertEquals(0, contractCount(requestId));
    }

    @Test
    @DisplayName("AC-BR40 failed conversion audit rolls back contract and request status together")
    void acBr40AuditFailureRollsBackWholeConversion() {
        long requestId = acceptedRequest();
        ActorContext missingAuditAccount = new ActorContext(
                27_199L, "missing-audit-account", IdentityType.EMPLOYEE, EMPLOYEE_ID,
                List.of(new ActiveRoleGrant(RoleCode.EMPLOYEE, STORE_ID)));

        assertThrows(RuntimeException.class, () -> transaction().execute(ignored -> {
            operations.lockAcceptedForConversion(missingAuditAccount, requestId);
            long contractId = insertContract(requestId, STORE_ID);
            operations.markConverted(missingAuditAccount, requestId, contractId);
            return null;
        }));
        assertEquals("ACCEPTED", status(requestId));
        assertEquals(0, contractCount(requestId));
    }

    @Test
    @DisplayName("AC-BR05 two MySQL connections cannot both convert the same accepted request")
    void acBr05ConcurrentConversionSerializesOnRequestRoot() throws Exception {
        long requestId = acceptedRequest();
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> transaction().execute(ignored -> {
                operations.lockAcceptedForConversion(employee(STORE_ID), requestId);
                firstLocked.countDown();
                await(releaseFirst);
                long contractId = insertContract(requestId, STORE_ID);
                operations.markConverted(employee(STORE_ID), requestId, contractId);
                return contractId;
            }));
            assertTrue(firstLocked.await(5, TimeUnit.SECONDS));
            var second = executor.submit(() -> transaction().execute(ignored -> {
                secondStarted.countDown();
                return operations.lockAcceptedForConversion(employee(STORE_ID), requestId);
            }));
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> second.get(250, TimeUnit.MILLISECONDS));

            releaseFirst.countDown();
            first.get(5, TimeUnit.SECONDS);
            ExecutionException conflict = assertThrows(ExecutionException.class,
                    () -> second.get(5, TimeUnit.SECONDS));
            assertTrue(conflict.getCause() instanceof ApiException);
            assertEquals("CONVERTED", status(requestId));
            assertEquals(1, contractCount(requestId));
        } finally {
            releaseFirst.countDown();
        }
    }

    private long acceptedRequest() {
        long requestId = submit();
        operations.accept(employee(STORE_ID), requestId);
        return requestId;
    }

    private long submit() {
        return service.submit(customer(), new CustomerRequestService.Submission(
                STORE_ID, LocalDate.of(2026, 12, 20), 3, "INTERMEDIATE",
                List.of(new RentalRequestItem("SKI", 2, "fit in store")), false)).requestId();
    }

    private long insertContract(long requestId, long storeId) {
        String number = "CONV-" + requestId;
        jdbcTemplate.update("""
                INSERT INTO rental_contract (contract_number, source_request_id, customer_id,
                    created_by_employee_id, service_store_id, contract_date, rental_start_date,
                    rental_days, amount_due, contract_status)
                VALUES (?, ?, ?, ?, ?, '2026-12-18', '2026-12-20', 3, 0.00, 'DRAFT')
                """, number, requestId, CUSTOMER_ID, EMPLOYEE_ID, storeId);
        return jdbcTemplate.queryForObject(
                "SELECT contract_id FROM rental_contract WHERE source_request_id = ?", Long.class, requestId);
    }

    private String status(long requestId) {
        return jdbcTemplate.queryForObject(
                "SELECT request_status FROM rental_request WHERE request_id = ?", String.class, requestId);
    }

    private int contractCount(long requestId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rental_contract WHERE source_request_id = ?", Integer.class, requestId);
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private ActorContext customer() {
        return new ActorContext(CUSTOMER_ACCOUNT_ID, "conversion-customer", IdentityType.CUSTOMER,
                CUSTOMER_ID, List.of(new ActiveRoleGrant(RoleCode.CUSTOMER, null)));
    }

    private ActorContext employee(long storeId) {
        return new ActorContext(EMPLOYEE_ACCOUNT_ID, "conversion-employee", IdentityType.EMPLOYEE,
                EMPLOYEE_ID, List.of(new ActiveRoleGrant(RoleCode.EMPLOYEE, storeId)));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for conversion barrier.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for conversion barrier.", exception);
        }
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM audit_event WHERE object_type = 'RENTAL_REQUEST' AND object_id IN (SELECT request_id FROM rental_request WHERE customer_id = ?)", CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM rental_contract WHERE source_request_id IN (SELECT request_id FROM rental_request WHERE customer_id = ?)", CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM rental_request_item WHERE request_id IN (SELECT request_id FROM rental_request WHERE customer_id = ?)", CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM rental_request WHERE customer_id = ?", CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM account WHERE account_id IN (?, ?)", CUSTOMER_ACCOUNT_ID, EMPLOYEE_ACCOUNT_ID);
        jdbcTemplate.update("DELETE FROM customer WHERE customer_id = ?", CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM employee WHERE employee_id = ?", EMPLOYEE_ID);
        jdbcTemplate.update("DELETE FROM store WHERE store_id IN (?, ?)", STORE_ID, OTHER_STORE_ID);
    }
}
