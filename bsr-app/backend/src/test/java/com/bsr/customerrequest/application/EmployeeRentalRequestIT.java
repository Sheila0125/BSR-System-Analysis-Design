package com.bsr.customerrequest.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bsr.customerrequest.application.CustomerRequestService.Submission;
import com.bsr.customerrequest.domain.RentalRequestItem;
import com.bsr.identity.api.ActiveRoleGrant;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.IdentityType;
import com.bsr.identity.api.RoleCode;
import com.bsr.platform.error.ApiException;
import com.bsr.schema.IsolatedTestDatabaseInitializer;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
    "spring.profiles.active=test",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@ContextConfiguration(initializers = IsolatedTestDatabaseInitializer.class)
@Transactional
class EmployeeRentalRequestIT {

    private static final long STORE_ID = 26301L;
    private static final long OTHER_STORE_ID = 26302L;
    private static final long CUSTOMER_ID = 26303L;
    private static final long EMPLOYEE_ID = 26304L;

    @Autowired
    private CustomerRequestService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void insertActorsAndStores() {
        insertStore(STORE_ID, "S-EMP-RQ-1");
        insertStore(OTHER_STORE_ID, "S-EMP-RQ-2");
        jdbcTemplate.update(
                """
                INSERT INTO customer (
                    customer_id, customer_number, full_name, email, phone,
                    height_estimated, weight_estimated, boot_size_estimated
                ) VALUES (?, 'C-EMP-RQ', 'Employee Queue Customer',
                    'employee-queue-customer@test.invalid', '+47000026303', FALSE, FALSE, FALSE)
                """,
                CUSTOMER_ID);
        jdbcTemplate.update(
                "INSERT INTO employee (employee_id, employee_number, full_name, email, phone, business_status) VALUES (?, 'E-EMP-RQ', 'Queue Employee', 'employee-queue@test.invalid', '+47000026304', 'ACTIVE')",
                EMPLOYEE_ID);
        jdbcTemplate.update(
                "INSERT INTO account (account_id, login_name, password_hash, enabled, customer_id, employee_id, contractor_id) VALUES (?, 'queue-customer', '{noop}unused', TRUE, ?, NULL, NULL)",
                26305L,
                CUSTOMER_ID);
        jdbcTemplate.update(
                "INSERT INTO account (account_id, login_name, password_hash, enabled, customer_id, employee_id, contractor_id) VALUES (?, 'queue-employee', '{noop}unused', TRUE, NULL, ?, NULL)",
                26306L,
                EMPLOYEE_ID);
    }

    @Test
    @DisplayName("AC-UC04/AC-BR05 employee queue uses controlled locked actions and writes success audits")
    void AC_UC04_BR05_queueAndControlledActions() {
        long needsInfoId = submit(STORE_ID, "BEGINNER");
        long acceptedId = submit(STORE_ID, "INTERMEDIATE");
        long unavailableId = submit(STORE_ID, "ADVANCED");
        submit(OTHER_STORE_ID, "BEGINNER");

        assertEquals(
                List.of(needsInfoId, acceptedId, unavailableId),
                service.loadEmployeeQueue(employee(STORE_ID), STORE_ID).stream()
                        .map(CustomerRequestPersistence.StoredRentalRequest::requestId)
                        .toList());

        service.requestMoreInformation(employee(STORE_ID), needsInfoId, "Confirm boot size");
        service.accept(employee(STORE_ID), acceptedId);
        service.accept(employee(STORE_ID), unavailableId);
        service.markUnavailable(employee(STORE_ID), unavailableId);

        assertEquals("NEEDS_INFO", status(needsInfoId));
        assertEquals("ACCEPTED", status(acceptedId));
        assertEquals(EMPLOYEE_ID, acceptedBy(acceptedId));
        assertEquals("UNAVAILABLE", status(unavailableId));

        ApiException conflict = assertThrows(
                ApiException.class,
                () -> service.accept(employee(STORE_ID), needsInfoId));
        assertEquals("RENTAL_REQUEST_STATE_CONFLICT", conflict.code());
        assertEquals("NEEDS_INFO", status(needsInfoId));
        assertEquals(4, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE object_type = 'RENTAL_REQUEST' AND action_code IN ('RENTAL_REQUEST_INFO_REQUESTED', 'RENTAL_REQUEST_ACCEPTED', 'RENTAL_REQUEST_MARKED_UNAVAILABLE') AND result_code = 'SUCCESS'",
                Integer.class));
    }

    @Test
    @DisplayName("AC-BR02 manager-only and cross-store employees cannot read or process the request queue")
    void AC_BR02_explicitEmployeeRoleAndStoreScopeAreRequired() {
        long requestId = submit(OTHER_STORE_ID, "INTERMEDIATE");

        ApiException managerDenied = assertThrows(
                ApiException.class,
                () -> service.loadEmployeeQueue(managerOnly(STORE_ID), STORE_ID));
        assertEquals("ACTION_NOT_AUTHORIZED", managerDenied.code());

        ApiException storeDenied = assertThrows(
                ApiException.class,
                () -> service.accept(employee(STORE_ID), requestId));
        assertEquals("ACTION_NOT_AUTHORIZED", storeDenied.code());
        assertEquals("PENDING", status(requestId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE object_type = 'RENTAL_REQUEST' AND object_id = ? AND action_code = 'RENTAL_REQUEST_ACCEPTED'",
                Integer.class,
                requestId));
    }

    @Test
    @DisplayName("AC-UC04 two supplement cycles retain both reasons and replies without replacing request items")
    void AC_UC04_supplementCyclesAreRetainedAndOwned() {
        long requestId = submit(STORE_ID, "BEGINNER");
        service.requestMoreInformation(employee(STORE_ID), requestId, "Confirm boot size");
        assertEquals("NEEDS_INFO", status(requestId));
        assertEquals("Confirm boot size", service.loadOwnedRequest(customer(), requestId)
                .supplements().getFirst().reason());

        ApiException blank = assertThrows(ApiException.class,
                () -> service.resubmitSupplementedInformation(customer(), requestId, "  "));
        assertEquals("VALIDATION_FAILED", blank.code());
        assertEquals("NEEDS_INFO", status(requestId));

        service.resubmitSupplementedInformation(customer(), requestId, "Measurements updated");
        assertEquals("PENDING", status(requestId));
        service.requestMoreInformation(employee(STORE_ID), requestId, "Confirm pickup date");
        service.resubmitSupplementedInformation(customer(), requestId, "Date confirmed");

        var stored = service.loadOwnedRequest(customer(), requestId);
        assertEquals("PENDING", stored.request().status().name());
        assertEquals(List.of("Confirm boot size", "Confirm pickup date"),
                stored.supplements().stream().map(CustomerRequestPersistence.SupplementCycle::reason).toList());
        assertEquals(List.of("Measurements updated", "Date confirmed"),
                stored.supplements().stream()
                        .map(CustomerRequestPersistence.SupplementCycle::customerResponse).toList());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rental_request_item WHERE request_id = ?", Integer.class, requestId));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE object_id = ? AND action_code = 'RENTAL_REQUEST_INFO_REQUESTED'",
                Integer.class, requestId));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE object_id = ? AND action_code = 'RENTAL_REQUEST_INFO_RESUBMITTED'",
                Integer.class, requestId));
    }

    private long submit(long storeId, String skillLevel) {
        return service.submit(
                customer(),
                new Submission(
                        storeId,
                        LocalDate.of(2026, 12, 20),
                        3,
                        skillLevel,
                        List.of(new RentalRequestItem("SKI", 1, null)),
                        true))
                .requestId();
    }

    private String status(long requestId) {
        return jdbcTemplate.queryForObject(
                "SELECT request_status FROM rental_request WHERE request_id = ?",
                String.class,
                requestId);
    }

    private Long acceptedBy(long requestId) {
        return jdbcTemplate.queryForObject(
                "SELECT accepted_by_employee_id FROM rental_request WHERE request_id = ?",
                Long.class,
                requestId);
    }

    private ActorContext customer() {
        return new ActorContext(
                26305L,
                "queue-customer",
                IdentityType.CUSTOMER,
                CUSTOMER_ID,
                List.of(new ActiveRoleGrant(RoleCode.CUSTOMER, null)));
    }

    private ActorContext employee(long storeId) {
        return new ActorContext(
                26306L,
                "queue-employee",
                IdentityType.EMPLOYEE,
                EMPLOYEE_ID,
                List.of(new ActiveRoleGrant(RoleCode.EMPLOYEE, storeId)));
    }

    private ActorContext managerOnly(long storeId) {
        return new ActorContext(
                26306L,
                "queue-manager",
                IdentityType.EMPLOYEE,
                EMPLOYEE_ID,
                List.of(new ActiveRoleGrant(RoleCode.MANAGER, storeId)));
    }

    private void insertStore(long storeId, String storeNumber) {
        jdbcTemplate.update(
                "INSERT INTO store (store_id, store_number, store_name, address, business_status, opening_time, closing_time) VALUES (?, ?, ?, NULL, 'ACTIVE', '08:00:00', '22:00:00')",
                storeId,
                storeNumber,
                storeNumber);
    }
}
