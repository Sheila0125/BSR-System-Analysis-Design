package com.bsr.customerrequest.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

import com.bsr.audit.api.AuditAppender;
import com.bsr.customerrequest.application.CustomerRequestService.Submission;
import com.bsr.customerrequest.application.CustomerRequestService.MeasurementUpdate;
import com.bsr.customerrequest.domain.RentalRequestItem;
import com.bsr.identity.api.ActiveRoleGrant;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.IdentityType;
import com.bsr.identity.api.RoleCode;
import com.bsr.schema.IsolatedTestDatabaseInitializer;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
    "spring.profiles.active=test",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@ContextConfiguration(initializers = IsolatedTestDatabaseInitializer.class)
class CustomerRequestAuditRollbackIT {

    private static final long STORE_ID = 26101L;
    private static final long CUSTOMER_ID = 26102L;
    private static final long EMPLOYEE_ID = 26104L;

    @Autowired
    private CustomerRequestService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private AuditAppender auditAppender;

    @BeforeEach
    void insertCustomer() {
        cleanup();
        jdbcTemplate.update(
                "INSERT INTO store (store_id, store_number, store_name, address, business_status, opening_time, closing_time) VALUES (?, 'S-CR-RB', 'Request Rollback Store', NULL, 'ACTIVE', '08:00:00', '22:00:00')",
                STORE_ID);
        jdbcTemplate.update(
                """
                INSERT INTO customer (
                    customer_id, customer_number, full_name, email, phone,
                    height_estimated, weight_estimated, boot_size_estimated
                ) VALUES (?, 'C-CR-RB', 'Request Rollback Customer',
                    'request-rollback@test.invalid', '+47000026102', FALSE, FALSE, FALSE)
                """,
                CUSTOMER_ID);
        jdbcTemplate.update(
                "INSERT INTO employee (employee_id, employee_number, full_name, email, phone, business_status) VALUES (?, 'E-CR-RB', 'Rollback Employee', 'request-rollback-employee@test.invalid', '+47000026104', 'ACTIVE')",
                EMPLOYEE_ID);
    }

    @AfterEach
    void removeCustomer() {
        cleanup();
    }

    @Test
    @DisplayName("AC-BR40 audit failure rolls back the request and all request items")
    void AC_BR40_auditFailureRollsBackRequestAndItems() {
        doThrow(new IllegalStateException("simulated audit persistence failure"))
                .when(auditAppender)
                .appendSuccess(any(), anyString(), anyLong(), anyString(), anyString());

        assertThrows(IllegalStateException.class, () -> service.submit(
                customer(),
                new Submission(
                        STORE_ID,
                        LocalDate.of(2026, 12, 20),
                        3,
                        "BEGINNER",
                        List.of(new RentalRequestItem("SKI", 1, null)),
                        false)));

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rental_request WHERE customer_id = ?",
                Integer.class,
                CUSTOMER_ID));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rental_request_item item JOIN rental_request request ON request.request_id = item.request_id WHERE request.customer_id = ?",
                Integer.class,
                CUSTOMER_ID));
    }

    @Test
    @DisplayName("AC-BR40 employee decision rolls back when its success audit cannot be stored")
    void AC_BR40_employeeDecisionAuditFailureRollsBackState() {
        long requestId = service.submit(
                customer(),
                new Submission(
                        STORE_ID,
                        LocalDate.of(2026, 12, 20),
                        3,
                        "BEGINNER",
                        List.of(new RentalRequestItem("SKI", 1, null)),
                        false))
                .requestId();
        doThrow(new IllegalStateException("simulated audit persistence failure"))
                .when(auditAppender)
                .appendSuccess(any(), anyString(), anyLong(), anyString(), anyString());

        assertThrows(IllegalStateException.class, () -> service.accept(employee(), requestId));

        assertEquals("PENDING", jdbcTemplate.queryForObject(
                "SELECT request_status FROM rental_request WHERE request_id = ?",
                String.class,
                requestId));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT accepted_by_employee_id FROM rental_request WHERE request_id = ?",
                Long.class,
                requestId));
    }

    @Test
    @DisplayName("AC-UC01/AC-BR40 employee measurement review rolls back when success audit fails")
    void AC_UC01_BR40_measurementReviewAuditFailureRollsBack() {
        doThrow(new IllegalStateException("simulated audit persistence failure"))
                .when(auditAppender)
                .appendSuccess(any(), anyString(), anyLong(), anyString(), anyString());

        assertThrows(IllegalStateException.class, () -> service.reviewCustomerMeasurements(
                employee(), STORE_ID, CUSTOMER_ID, "C-CR-RB",
                new MeasurementUpdate(null, false, null, false, null, false),
                new MeasurementUpdate(new BigDecimal("170.25"), true, null, false, null, false)));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT height_cm FROM customer WHERE customer_id = ?", BigDecimal.class, CUSTOMER_ID));
        assertEquals(false, jdbcTemplate.queryForObject(
                "SELECT height_estimated FROM customer WHERE customer_id = ?", Boolean.class, CUSTOMER_ID));
    }

    @Test
    @DisplayName("AC-UC04/AC-BR40 supplement reason and status roll back together on audit failure")
    void AC_UC04_BR40_supplementRequestAuditFailureRollsBack() {
        long requestId = service.submit(customer(), new Submission(
                STORE_ID, LocalDate.of(2026, 12, 20), 3, "BEGINNER",
                List.of(new RentalRequestItem("SKI", 1, null)), false)).requestId();
        doThrow(new IllegalStateException("simulated audit persistence failure"))
                .when(auditAppender).appendSuccess(any(), anyString(), anyLong(), anyString(), anyString());

        assertThrows(IllegalStateException.class,
                () -> service.requestMoreInformation(employee(), requestId, "Confirm boot size"));
        assertEquals("PENDING", jdbcTemplate.queryForObject(
                "SELECT request_status FROM rental_request WHERE request_id = ?", String.class, requestId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rental_request_supplement WHERE request_id = ?", Integer.class, requestId));
    }

    @Test
    @DisplayName("AC-UC04/AC-BR40 supplement reply and resubmission roll back together on audit failure")
    void AC_UC04_BR40_supplementReplyAuditFailureRollsBack() {
        long requestId = service.submit(customer(), new Submission(
                STORE_ID, LocalDate.of(2026, 12, 20), 3, "BEGINNER",
                List.of(new RentalRequestItem("SKI", 1, null)), false)).requestId();
        service.requestMoreInformation(employee(), requestId, "Confirm boot size");
        doThrow(new IllegalStateException("simulated audit persistence failure"))
                .when(auditAppender).appendSuccess(any(), anyString(), anyLong(), anyString(), anyString());

        assertThrows(IllegalStateException.class,
                () -> service.resubmitSupplementedInformation(customer(), requestId, "Measurements updated"));
        assertEquals("NEEDS_INFO", jdbcTemplate.queryForObject(
                "SELECT request_status FROM rental_request WHERE request_id = ?", String.class, requestId));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT customer_response FROM rental_request_supplement WHERE request_id = ?",
                String.class, requestId));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT responded_at FROM rental_request_supplement WHERE request_id = ?",
                java.time.LocalDateTime.class, requestId));
    }

    private ActorContext customer() {
        return new ActorContext(
                26103L,
                "request-rollback",
                IdentityType.CUSTOMER,
                CUSTOMER_ID,
                List.of(new ActiveRoleGrant(RoleCode.CUSTOMER, null)));
    }

    private ActorContext employee() {
        return new ActorContext(
                26105L,
                "request-rollback-employee",
                IdentityType.EMPLOYEE,
                EMPLOYEE_ID,
                List.of(new ActiveRoleGrant(RoleCode.EMPLOYEE, STORE_ID)));
    }

    private void cleanup() {
        jdbcTemplate.update(
                "DELETE FROM rental_request_supplement WHERE request_id IN (SELECT request_id FROM rental_request WHERE customer_id = ?)",
                CUSTOMER_ID);
        jdbcTemplate.update(
                "DELETE FROM rental_request_item WHERE request_id IN (SELECT request_id FROM rental_request WHERE customer_id = ?)",
                CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM rental_request WHERE customer_id = ?", CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM customer WHERE customer_id = ?", CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM employee WHERE employee_id = ?", EMPLOYEE_ID);
        jdbcTemplate.update("DELETE FROM store WHERE store_id = ?", STORE_ID);
    }
}
