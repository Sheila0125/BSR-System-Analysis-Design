package com.bsr.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
    "spring.profiles.active=test",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@ContextConfiguration(initializers = IsolatedTestDatabaseInitializer.class)
class SchemaMigrationIT {

    private static final Set<String> BUSINESS_TABLES = Set.of(
            "account",
            "audit_event",
            "customer",
            "employee",
            "employee_shift",
            "equipment",
            "equipment_inspection",
            "equipment_transfer",
            "equipment_transfer_item",
            "payment_transaction",
            "rental_change",
            "rental_change_item",
            "rental_charge",
            "rental_contract",
            "rental_item",
            "rental_request",
            "rental_request_item",
            "rental_request_supplement",
            "repair_contractor",
            "repair_labor",
            "repair_rate",
            "repair_service_charge",
            "repair_task",
            "role_grant",
            "store");

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test @Transactional
    @DisplayName("INV/PAY V008 preserves legacy external origin and rejects missing, forged or dangling recorders")
    void simulatedPaymentOriginsAreConstrainedAndHistoricalResultsStayExternal() {
        assertTrue(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_transaction WHERE transaction_source='EXTERNAL' AND recorded_by_employee_id IS NOT NULL AND simulated_by_account_id IS NULL",Long.class)>0);
        jdbcTemplate.update("""
                INSERT INTO payment_transaction(payment_transaction_id,rental_charge_id,transaction_type,external_transaction_id,
                    amount,transaction_status,processed_at,recorded_by_employee_id,recorded_at)
                SELECT 9990,rental_charge_id,transaction_type,'EXT-ORIGIN-CHECK',amount,transaction_status,
                    processed_at,recorded_by_employee_id,recorded_at
                FROM payment_transaction WHERE transaction_source='EXTERNAL' LIMIT 1
                """);
        assertCheckViolation("ck_payment_origin_recorder",()->jdbcTemplate.update("UPDATE payment_transaction SET recorded_by_employee_id=NULL WHERE payment_transaction_id=9990"));
        Long accountId=jdbcTemplate.queryForObject("SELECT MIN(account_id) FROM account",Long.class);
        assertCheckViolation("ck_payment_origin_recorder",()->jdbcTemplate.update("UPDATE payment_transaction SET transaction_source='SIMULATED',simulated_by_account_id=? WHERE payment_transaction_id=9990",accountId));
        jdbcTemplate.update("UPDATE payment_transaction SET transaction_source='SIMULATED',simulated_by_account_id=?,recorded_by_employee_id=NULL WHERE payment_transaction_id=9990",accountId);
        assertThrows(DataIntegrityViolationException.class,()->jdbcTemplate.update("UPDATE payment_transaction SET simulated_by_account_id=9223372036854775806 WHERE payment_transaction_id=9990"));
        assertCheckViolation("ck_payment_origin_recorder",()->jdbcTemplate.update("UPDATE payment_transaction SET transaction_source='UNKNOWN' WHERE payment_transaction_id=9990"));
    }

    @Test
    @DisplayName("AC-BR01 V001-V008 migrate once and a second migrate is a no-op")
    void AC_BR01_migrationsProduceTheExactFoundationAndRerunSafely() {
        assertEquals(0, flyway.migrate().migrationsExecuted);

        String databaseVersion = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
        assertTrue(databaseVersion != null && databaseVersion.startsWith("8.0.45"));
        Long successfulMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE",
                Long.class);
        assertEquals(8L, successfulMigrations);

        List<String> tables = jdbcTemplate.queryForList(
                """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                """,
                String.class);
        assertEquals(BUSINESS_TABLES, Set.copyOf(tables));

        assertEquals(58L, countConstraints("FOREIGN KEY"));
        assertEquals(24L, countConstraints("UNIQUE"));
        assertEquals(67L, countConstraints("CHECK"));

        String isolation = jdbcTemplate.queryForObject("SELECT @@transaction_isolation", String.class);
        assertEquals("READ-COMMITTED", isolation);
    }

    @Test
    @DisplayName("AC-BR40 V003-V004 preserve all 24 pre-existing fictional business rows")
    void AC_BR40_historicalFixtureSurvivesTheV003Upgrade() {
        for (String table : BUSINESS_TABLES) {
            if (table.equals("rental_request_supplement")) {
                continue;
            }
            Long rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
            assertTrue(rows != null && rows > 0, table);
        }
        assertEquals(
                "Fixture Customer",
                jdbcTemplate.queryForObject(
                        "SELECT full_name FROM customer WHERE customer_id = 10001",
                        String.class));
    }

    @Test
    @Transactional
    @DisplayName("AC-UC04 supplement cycles keep distinct history and reject invalid reason/reply")
    void AC_UC04_supplementHistoryHasForeignKeysAndSingleReplyPerCycle() {
        insertSupplement(9_501L, 10008L, 1, "Confirm boot sizing", null, null);
        insertSupplement(9_502L, 10008L, 2, "Confirm pickup date", "Updated measurements", "2026-02-01 10:00:00");
        assertEquals(2L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rental_request_supplement WHERE request_id = 10008", Long.class));
        assertThrows(DataIntegrityViolationException.class,
                () -> insertSupplement(9_503L, 10008L, 2, "Duplicate cycle", null, null));
        assertThrows(DataIntegrityViolationException.class,
                () -> insertSupplement(9_504L, 9_999_999L, 3, "Unknown request", null, null));
        assertCheckViolation("ck_rental_request_supplement_reason_present",
                () -> insertSupplement(9_505L, 10008L, 3, "  ", null, null));
        assertCheckViolation("ck_rental_request_supplement_response_pair",
                () -> insertSupplement(9_506L, 10008L, 3, "Need details", "Reply without time", null));
    }

    @Test
    @Transactional
    @DisplayName("AC-BR02 account rejects zero business identities")
    void AC_BR02_accountRequiresExactlyOneBusinessIdentity() {
        assertCheckViolation("ck_account_exactly_one_identity", () -> jdbcTemplate.update(
                """
                INSERT INTO account (
                    account_id, login_name, password_hash, enabled,
                    customer_id, employee_id, contractor_id
                ) VALUES (?, ?, ?, ?, NULL, NULL, NULL)
                """,
                9_001L,
                "invalid-no-identity",
                "not-a-real-password-hash",
                true));

        assertCheckViolation("ck_account_exactly_one_identity", () -> jdbcTemplate.update(
                """
                INSERT INTO account (
                    account_id, login_name, password_hash, enabled,
                    customer_id, employee_id, contractor_id
                ) VALUES (?, ?, ?, ?, ?, ?, NULL)
                """,
                9_002L,
                "invalid-two-identities",
                "not-a-real-password-hash",
                true,
                10001L,
                10002L));
    }

    @Test
    @Transactional
    @DisplayName("AC-BR18 external transaction identifiers are unique")
    void AC_BR18_externalTransactionIdentifierIsUnique() {
        insertCustomer(9_101L, "C-IT-9101", "customer-9101@example.invalid");
        insertEmployee(9_102L, "E-IT-9102", "employee-9102@example.invalid");
        insertStore(9_103L, "S-IT-9103");

        jdbcTemplate.update(
                """
                INSERT INTO rental_contract (
                    contract_id, contract_number, source_request_id, customer_id,
                    created_by_employee_id, service_store_id, contract_date,
                    rental_start_date, rental_days, amount_due, contract_status
                ) VALUES (?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                9_104L,
                "RC-IT-9104",
                9_101L,
                9_102L,
                9_103L,
                Date.valueOf(LocalDate.of(2026, 1, 10)),
                Date.valueOf(LocalDate.of(2026, 1, 11)),
                2,
                new BigDecimal("200.00"),
                "PENDING_PAYMENT");
        jdbcTemplate.update(
                """
                INSERT INTO rental_charge (
                    rental_charge_id, contract_id, rental_change_id,
                    charge_type, amount, charge_status, created_at
                ) VALUES (?, ?, NULL, ?, ?, ?, ?)
                """,
                9_105L,
                9_104L,
                "INITIAL",
                new BigDecimal("200.00"),
                "PENDING",
                Timestamp.valueOf(LocalDateTime.of(2026, 1, 10, 10, 0)));

        insertPaymentTransaction(9_106L, "EXT-IT-UNIQUE-1");
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertPaymentTransaction(9_107L, "EXT-IT-UNIQUE-1"));
    }

    @Test
    @Transactional
    @DisplayName("AC-BR35 cancelled shift history coexists but business hours remain enforced")
    void AC_BR35_cancelledShiftHistoryCoexistsAndHoursAreChecked() {
        insertEmployee(9_201L, "E-IT-9201", "employee-9201@example.invalid");
        insertStore(9_202L, "S-IT-9202");

        insertShift(9_203L, "SH-IT-9203", "09:00:00", "10:00:00", "CANCELLED");
        insertShift(9_204L, "SH-IT-9204", "10:00:00", "11:00:00", "CANCELLED");
        Long historyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM employee_shift WHERE employee_id = ? AND shift_date = ?",
                Long.class,
                9_201L,
                Date.valueOf(LocalDate.of(2026, 2, 1)));
        assertEquals(2L, historyCount);

        assertCheckViolation(
                "ck_shift_business_hours",
                () -> insertShift(9_205L, "SH-IT-9205", "07:59:59", "09:00:00", "ACTIVE"));

        insertShift(9_206L, "SH-IT-9206", "08:00:00", "22:00:00", "ACTIVE");
        assertCheckViolation(
                "ck_shift_business_hours",
                () -> insertShift(9_207L, "SH-IT-9207", "21:00:00", "22:00:01", "ACTIVE"));
        assertCheckViolation(
                "ck_shift_time_order",
                () -> insertShift(9_208L, "SH-IT-9208", "10:00:00", "10:00:00", "ACTIVE"));
    }

    @Test
    @Transactional
    @DisplayName("AC-BR24 repair labor rejects durations outside positive 0.25-hour increments")
    void AC_BR24_repairLaborEnforcesQuarterHourIncrements() {
        assertCheckViolation("ck_repair_labor_quarter_hour", () -> jdbcTemplate.update(
                """
                INSERT INTO repair_labor (
                    repair_labor_id, repair_task_id, repair_date, duration_hours,
                    handling_result, note, repair_rate_id, hourly_rate_snapshot,
                    labor_amount, submitted_at, contractor_id
                ) VALUES (?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?)
                """,
                9_401L,
                10016L,
                Date.valueOf(LocalDate.of(2026, 2, 5)),
                new BigDecimal("0.30"),
                "Invalid integration duration",
                10018L,
                new BigDecimal("100.00"),
                new BigDecimal("30.00"),
                Timestamp.valueOf(LocalDateTime.of(2026, 2, 5, 10, 0)),
                10003L));
    }

    private void assertCheckViolation(String constraintName, Executable operation) {
        DataAccessException exception = assertThrows(DataAccessException.class, operation);
        Throwable cause = exception;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        SQLException sqlException = assertInstanceOf(SQLException.class, cause);
        assertEquals(3819, sqlException.getErrorCode());
        assertTrue(sqlException.getMessage().contains(constraintName), sqlException.getMessage());
    }

    @Test
    @Transactional
    @DisplayName("AC-BR04 rental request rejects a missing customer foreign key")
    void AC_BR04_rentalRequestRejectsMissingCustomer() {
        insertStore(9_302L, "S-IT-9302");
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                """
                INSERT INTO rental_request (
                    request_id, request_number, customer_id, service_store_id,
                    accepted_by_employee_id, rental_start_date, rental_days,
                    skill_level, submitted_at, request_status
                ) VALUES (?, ?, ?, ?, NULL, ?, ?, ?, ?, ?)
                """,
                9_303L,
                "RR-IT-9303",
                9_999_999L,
                9_302L,
                Date.valueOf(LocalDate.of(2026, 3, 1)),
                2,
                "BEGINNER",
                Timestamp.valueOf(LocalDateTime.of(2026, 2, 20, 12, 0)),
                "PENDING"));
    }

    private long countConstraints(String constraintType) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.table_constraints
                WHERE table_schema = DATABASE()
                  AND constraint_type = ?
                """,
                Long.class,
                constraintType);
        assertTrue(count != null);
        return count;
    }

    private void insertCustomer(long id, String number, String email) {
        jdbcTemplate.update(
                """
                INSERT INTO customer (
                    customer_id, customer_number, full_name, email, phone,
                    address, birth_year, height_cm, weight_kg, boot_size,
                    height_estimated, weight_estimated, boot_size_estimated
                ) VALUES (?, ?, ?, ?, ?, NULL, NULL, NULL, NULL, NULL, FALSE, FALSE, FALSE)
                """,
                id,
                number,
                "Integration Customer",
                email,
                "+4700000000");
    }

    private void insertEmployee(long id, String number, String email) {
        jdbcTemplate.update(
                """
                INSERT INTO employee (
                    employee_id, employee_number, full_name, email, phone, business_status
                ) VALUES (?, ?, ?, ?, NULL, 'ACTIVE')
                """,
                id,
                number,
                "Integration Employee",
                email);
    }

    private void insertStore(long id, String number) {
        jdbcTemplate.update(
                """
                INSERT INTO store (
                    store_id, store_number, store_name, address,
                    business_status, opening_time, closing_time
                ) VALUES (?, ?, ?, NULL, 'ACTIVE', '08:00:00', '22:00:00')
                """,
                id,
                number,
                "Integration Store");
    }

    private void insertPaymentTransaction(long id, String externalId) {
        jdbcTemplate.update(
                """
                INSERT INTO payment_transaction (
                    payment_transaction_id, rental_charge_id, transaction_type,
                    external_transaction_id, amount, transaction_status,
                    processed_at, recorded_by_employee_id, recorded_at
                ) VALUES (?, ?, 'PAYMENT', ?, ?, 'SUCCEEDED', ?, ?, ?)
                """,
                id,
                9_105L,
                externalId,
                new BigDecimal("200.00"),
                Timestamp.valueOf(LocalDateTime.of(2026, 1, 10, 11, 0)),
                9_102L,
                Timestamp.valueOf(LocalDateTime.of(2026, 1, 10, 11, 1)));
    }

    private void insertSupplement(long id, long requestId, int cycle, String reason,
            String response, String respondedAt) {
        jdbcTemplate.update(
                """
                INSERT INTO rental_request_supplement (
                    supplement_id, request_id, cycle_number, requested_by_employee_id,
                    reason, requested_at, customer_response, responded_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                requestId,
                cycle,
                10002L,
                reason,
                Timestamp.valueOf(LocalDateTime.of(2026, 2, 1, 9, 0)),
                response,
                respondedAt == null ? null : Timestamp.valueOf(respondedAt));
    }

    private void insertShift(long id, String number, String start, String end, String status) {
        jdbcTemplate.update(
                """
                INSERT INTO employee_shift (
                    shift_id, shift_number, shift_date, store_id, employee_id,
                    start_time, end_time, shift_status,
                    changed_by_employee_id, changed_at, cancellation_reason
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                number,
                Date.valueOf(LocalDate.of(2026, 2, 1)),
                9_202L,
                9_201L,
                java.sql.Time.valueOf(start),
                java.sql.Time.valueOf(end),
                status,
                9_201L,
                Timestamp.valueOf(LocalDateTime.of(2026, 1, 20, 9, 0)),
                "CANCELLED".equals(status) ? "integration history" : null);
    }
}
