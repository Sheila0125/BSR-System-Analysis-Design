package com.bsr.customerrequest.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bsr.customerrequest.application.CustomerRequestService.MeasurementUpdate;
import com.bsr.customerrequest.application.CustomerRequestService.Submission;
import com.bsr.customerrequest.domain.RentalRequestItem;
import com.bsr.identity.api.ActiveRoleGrant;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.IdentityType;
import com.bsr.identity.api.RoleCode;
import com.bsr.platform.error.ApiException;
import com.bsr.schema.IsolatedTestDatabaseInitializer;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
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
class CustomerRequestPersistenceIT {

    private static final long STORE_ID = 26001L;
    private static final long CUSTOMER_ID = 26002L;
    private static final long ACCOUNT_ID = 26003L;
    private static final long EQUIPMENT_ID = 26004L;

    @Autowired
    private CustomerRequestService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void insertCustomerAndAvailableEquipment() {
        jdbcTemplate.update(
                "INSERT INTO store (store_id, store_number, store_name, address, business_status, opening_time, closing_time) VALUES (?, 'S-CR-01', 'Customer Request Store', NULL, 'ACTIVE', '08:00:00', '22:00:00')",
                STORE_ID);
        jdbcTemplate.update(
                """
                INSERT INTO customer (
                    customer_id, customer_number, full_name, email, phone, address, birth_year,
                    height_cm, weight_kg, boot_size,
                    height_estimated, weight_estimated, boot_size_estimated
                ) VALUES (?, 'C-CR-01', 'Customer Request Test', 'customer-request@test.invalid',
                    '+47000026002', 'Test Address', 1992, 170.00, 70.00, 42.0, FALSE, FALSE, FALSE)
                """,
                CUSTOMER_ID);
        jdbcTemplate.update(
                "INSERT INTO account (account_id, login_name, password_hash, enabled, customer_id, employee_id, contractor_id) VALUES (?, 'customer-request-test', '{noop}unused', TRUE, ?, NULL, NULL)",
                ACCOUNT_ID,
                CUSTOMER_ID);
        jdbcTemplate.update(
                """
                INSERT INTO equipment (
                    equipment_id, inventory_number, equipment_name, equipment_category, skill_level,
                    home_store_id, current_store_id, equipment_status, purchase_date,
                    purchase_price, current_daily_rate
                ) VALUES (?, 'INV-CR-01', 'Unreserved Test Ski', 'SKI', 'ADVANCED',
                    ?, ?, 'AVAILABLE', '2025-01-01', 1000.00, 99.50)
                """,
                EQUIPMENT_ID,
                STORE_ID,
                STORE_ID);
    }

    @Test
    @DisplayName("AC-WX01/B02 three estimation flags and measurements survive a real MySQL reload")
    void AC_WX01_B02_estimationFlagsSurviveReload() {
        service.updateOwnMeasurements(
                customer(),
                new MeasurementUpdate(
                        new BigDecimal("171.25"), true,
                        new BigDecimal("69.50"), false,
                        new BigDecimal("42.5"), true));
        entityManager.flush();
        entityManager.clear();

        var reloaded = service.loadOwnProfile(customer());

        assertEquals(new BigDecimal("171.25"), reloaded.heightCm());
        assertTrue(reloaded.heightEstimated());
        assertEquals(new BigDecimal("69.50"), reloaded.weightKg());
        assertEquals(new BigDecimal("42.5"), reloaded.bootSize());
        assertTrue(reloaded.bootSizeEstimated());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE object_type = 'CUSTOMER' AND object_id = ? AND action_code = 'CUSTOMER_PROFILE_UPDATED'",
                Integer.class,
                CUSTOMER_ID));
    }

    @Test
    @DisplayName("AC-UC01/AC-BR03/04 duplicate confirmation persists current skill without reserving equipment")
    void AC_UC01_BR03_BR04_submissionReloadDuplicateAndOwnership() {
        var first = service.submit(customer(), submission("ADVANCED", false));
        entityManager.clear();

        var reloaded = service.loadOwnedRequest(customer(), first.requestId());
        assertEquals("ADVANCED", reloaded.request().skillLevel());
        assertEquals(2, reloaded.request().items().getFirst().quantity());
        assertEquals("AVAILABLE", jdbcTemplate.queryForObject(
                "SELECT equipment_status FROM equipment WHERE equipment_id = ?",
                String.class,
                EQUIPMENT_ID));
        assertEquals(STORE_ID, jdbcTemplate.queryForObject(
                "SELECT current_store_id FROM equipment WHERE equipment_id = ?",
                Long.class,
                EQUIPMENT_ID));

        ApiException confirmation = assertThrows(
                ApiException.class,
                () -> service.submit(customer(), submission("BEGINNER", false)));
        assertEquals("DUPLICATE_REQUEST_CONFIRMATION_REQUIRED", confirmation.code());

        var second = service.submit(customer(), submission("BEGINNER", true));
        assertEquals("BEGINNER", second.request().skillLevel());
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rental_request WHERE customer_id = ?",
                Integer.class,
                CUSTOMER_ID));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE object_type = 'RENTAL_REQUEST' AND action_code = 'RENTAL_REQUEST_SUBMITTED'",
                Integer.class));

        ApiException hidden = assertThrows(
                ApiException.class,
                () -> service.loadOwnedRequest(otherCustomer(), first.requestId()));
        assertEquals("RENTAL_REQUEST_NOT_FOUND", hidden.code());
    }

    private Submission submission(String skillLevel, boolean confirmDuplicate) {
        return new Submission(
                STORE_ID,
                LocalDate.of(2026, 12, 20),
                3,
                skillLevel,
                List.of(new RentalRequestItem("SKI", 2, "One shorter pair")),
                confirmDuplicate);
    }

    private ActorContext customer() {
        return new ActorContext(
                ACCOUNT_ID,
                "customer-request-test",
                IdentityType.CUSTOMER,
                CUSTOMER_ID,
                List.of(new ActiveRoleGrant(RoleCode.CUSTOMER, null)));
    }

    private ActorContext otherCustomer() {
        return new ActorContext(
                26999L,
                "other-customer",
                IdentityType.CUSTOMER,
                26998L,
                List.of(new ActiveRoleGrant(RoleCode.CUSTOMER, null)));
    }
}
