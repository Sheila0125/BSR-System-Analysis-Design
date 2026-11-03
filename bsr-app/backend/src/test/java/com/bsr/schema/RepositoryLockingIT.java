package com.bsr.schema;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bsr.scheduling.infrastructure.persistence.EmployeeRepository;
import java.time.Duration;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
    "spring.profiles.active=test",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@ContextConfiguration(initializers = IsolatedTestDatabaseInitializer.class)
class RepositoryLockingIT {

    private static final long EMPLOYEE_ID = 99_001L;
    private static final long STORE_ID = 99_002L;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("AC-BR35 two connections serialize on the employee parent-row lock")
    void AC_BR35_employeeParentRowLockSerializesTwoConnections() throws Exception {
        jdbcTemplate.update("DELETE FROM employee WHERE employee_id = ?", EMPLOYEE_ID);
        jdbcTemplate.update(
                """
                INSERT INTO employee (
                    employee_id, employee_number, full_name, email, phone, business_status
                ) VALUES (?, ?, ?, ?, NULL, 'ACTIVE')
                """,
                EMPLOYEE_ID,
                "E-LOCK-99001",
                "Lock Fixture Employee",
                "employee-lock-99001@fixture.invalid");

        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch secondAboutToLock = new CountDownLatch(1);
        CountDownLatch secondLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> first = executor.submit(() -> inReadCommittedTransaction(() -> {
                assertTrue(employeeRepository.findByIdForUpdate(EMPLOYEE_ID).isPresent());
                firstLocked.countDown();
                await(releaseFirst, Duration.ofSeconds(10));
            }));

            assertTrue(firstLocked.await(10, java.util.concurrent.TimeUnit.SECONDS));

            Future<?> second = executor.submit(() -> inReadCommittedTransaction(() -> {
                secondAboutToLock.countDown();
                assertTrue(employeeRepository.findByIdForUpdate(EMPLOYEE_ID).isPresent());
                secondLocked.countDown();
            }));

            assertTrue(secondAboutToLock.await(10, java.util.concurrent.TimeUnit.SECONDS));
            assertFalse(secondLocked.await(300, java.util.concurrent.TimeUnit.MILLISECONDS));
            releaseFirst.countDown();
            first.get(10, java.util.concurrent.TimeUnit.SECONDS);
            second.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(secondLocked.await(1, java.util.concurrent.TimeUnit.SECONDS));
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS));
            jdbcTemplate.update("DELETE FROM employee WHERE employee_id = ?", EMPLOYEE_ID);
        }
    }

    @Test
    @DisplayName("AC-BR35 concurrent first-shift insertion commits exactly one active shift")
    void AC_BR35_parentLockProtectsTheEmptyShiftSet() throws Exception {
        deleteShiftRaceFixture();
        insertShiftRaceParents();

        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch secondAboutToLock = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> first = executor.submit(() -> inReadCommittedTransactionWithResult(() -> {
                assertTrue(employeeRepository.findByIdForUpdate(EMPLOYEE_ID).isPresent());
                assertEquals(0L, countActiveShifts());
                firstLocked.countDown();
                await(releaseFirst, Duration.ofSeconds(10));
                insertActiveShift(99_101L, "SH-RACE-99101");
                return true;
            }));

            assertTrue(firstLocked.await(10, java.util.concurrent.TimeUnit.SECONDS));
            Future<Boolean> second = executor.submit(() -> inReadCommittedTransactionWithResult(() -> {
                secondAboutToLock.countDown();
                assertTrue(employeeRepository.findByIdForUpdate(EMPLOYEE_ID).isPresent());
                if (countActiveShifts() > 0L) {
                    return false;
                }
                insertActiveShift(99_102L, "SH-RACE-99102");
                return true;
            }));

            assertTrue(secondAboutToLock.await(10, java.util.concurrent.TimeUnit.SECONDS));
            releaseFirst.countDown();
            assertTrue(first.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertFalse(second.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1L, countActiveShifts());
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS));
            deleteShiftRaceFixture();
        }
    }

    private void inReadCommittedTransaction(Runnable work) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.executeWithoutResult(status -> work.run());
    }

    private <T> T inReadCommittedTransactionWithResult(java.util.concurrent.Callable<T> work) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return transaction.execute(status -> {
            try {
                return work.call();
            } catch (Exception exception) {
                throw new IllegalStateException("Concurrent integration transaction failed", exception);
            }
        });
    }

    private long countActiveShifts() {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM employee_shift
                WHERE employee_id = ?
                  AND shift_date = ?
                  AND shift_status = 'ACTIVE'
                """,
                Long.class,
                EMPLOYEE_ID,
                Date.valueOf(LocalDate.of(2026, 3, 1)));
        return count == null ? 0L : count;
    }

    private void insertShiftRaceParents() {
        jdbcTemplate.update(
                """
                INSERT INTO employee (
                    employee_id, employee_number, full_name, email, phone, business_status
                ) VALUES (?, ?, ?, ?, NULL, 'ACTIVE')
                """,
                EMPLOYEE_ID,
                "E-RACE-99001",
                "Shift Race Employee",
                "employee-race-99001@fixture.invalid");
        jdbcTemplate.update(
                """
                INSERT INTO store (
                    store_id, store_number, store_name, address,
                    business_status, opening_time, closing_time
                ) VALUES (?, ?, ?, NULL, 'ACTIVE', '08:00:00', '22:00:00')
                """,
                STORE_ID,
                "S-RACE-99002",
                "Shift Race Store");
    }

    private void insertActiveShift(long id, String number) {
        jdbcTemplate.update(
                """
                INSERT INTO employee_shift (
                    shift_id, shift_number, shift_date, store_id, employee_id,
                    start_time, end_time, shift_status,
                    changed_by_employee_id, changed_at, cancellation_reason
                ) VALUES (?, ?, ?, ?, ?, '09:00:00', '17:00:00', 'ACTIVE', ?, ?, NULL)
                """,
                id,
                number,
                Date.valueOf(LocalDate.of(2026, 3, 1)),
                STORE_ID,
                EMPLOYEE_ID,
                EMPLOYEE_ID,
                Timestamp.valueOf(LocalDateTime.of(2026, 2, 20, 9, 0)));
    }

    private void deleteShiftRaceFixture() {
        jdbcTemplate.update("DELETE FROM employee_shift WHERE employee_id = ?", EMPLOYEE_ID);
        jdbcTemplate.update("DELETE FROM store WHERE store_id = ?", STORE_ID);
        jdbcTemplate.update("DELETE FROM employee WHERE employee_id = ?", EMPLOYEE_ID);
    }

    private static void await(CountDownLatch latch, Duration timeout) {
        try {
            assertTrue(latch.await(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating database lock test", exception);
        }
    }
}
