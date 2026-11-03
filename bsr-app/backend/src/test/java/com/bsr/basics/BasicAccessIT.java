package com.bsr.basics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bsr.repair.contractor.api.ContractorAccess;
import com.bsr.repair.contractor.api.ContractorAccessException;
import com.bsr.scheduling.employee.api.EmployeeAccess;
import com.bsr.scheduling.employee.api.EmployeeAccessException;
import com.bsr.schema.IsolatedTestDatabaseInitializer;
import com.bsr.storetransfer.store.api.StoreAccess;
import com.bsr.storetransfer.store.api.StoreAccessException;
import java.time.Duration;
import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
class BasicAccessIT {

    private static final long ACTIVE_ID = 29_001L;
    private static final long INACTIVE_ID = 29_002L;

    @Autowired
    private StoreAccess storeAccess;

    @Autowired
    private EmployeeAccess employeeAccess;

    @Autowired
    private ContractorAccess contractorAccess;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void insertFixtures() {
        cleanup();
        jdbcTemplate.update("""
                INSERT INTO store
                    (store_id, store_number, store_name, address, business_status, opening_time, closing_time)
                VALUES (?, 'S-BASIC-1', 'Basic Active Store', 'Test address', 'ACTIVE', '08:00:00', '22:00:00'),
                       (?, 'S-BASIC-2', 'Basic Inactive Store', NULL, 'INACTIVE', '09:00:00', '17:00:00')
                """, ACTIVE_ID, INACTIVE_ID);
        jdbcTemplate.update("""
                INSERT INTO employee
                    (employee_id, employee_number, full_name, email, phone, business_status)
                VALUES (?, 'E-BASIC-1', 'Active Employee', 'active-employee@basic.invalid', NULL, 'ACTIVE'),
                       (?, 'E-BASIC-2', 'Inactive Employee', 'inactive-employee@basic.invalid', NULL, 'INACTIVE')
                """, ACTIVE_ID, INACTIVE_ID);
        jdbcTemplate.update("""
                INSERT INTO repair_contractor
                    (contractor_id, contractor_number, contractor_name, contact_name, email, phone, business_status)
                VALUES (?, 'C-BASIC-1', 'Active Contractor', 'Active Contact',
                        'active-contractor@basic.invalid', NULL, 'ACTIVE'),
                       (?, 'C-BASIC-2', 'Inactive Contractor', NULL,
                        'inactive-contractor@basic.invalid', NULL, 'INACTIVE')
                """, ACTIVE_ID, INACTIVE_ID);
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM repair_contractor WHERE contractor_id IN (?, ?)", ACTIVE_ID, INACTIVE_ID);
        jdbcTemplate.update("DELETE FROM employee WHERE employee_id IN (?, ?)", ACTIVE_ID, INACTIVE_ID);
        jdbcTemplate.update("DELETE FROM store WHERE store_id IN (?, ?)", ACTIVE_ID, INACTIVE_ID);
    }

    @Test
    void acUc10MinimalSnapshotsPreserveStatusAndStoreHours() {
        var store = storeAccess.findById(ACTIVE_ID).orElseThrow();
        var employee = employeeAccess.findById(INACTIVE_ID).orElseThrow();
        var contractor = contractorAccess.findById(ACTIVE_ID).orElseThrow();

        assertEquals(LocalTime.of(8, 0), store.openingTime());
        assertEquals(LocalTime.of(22, 0), store.closingTime());
        assertTrue(store.active());
        assertFalse(employee.active());
        assertTrue(contractor.active());
    }

    @Test
    void acUc10InactiveParentsCannotBeUsedForNewBusiness() {
        TransactionTemplate transaction = transaction();

        StoreAccessException storeError = assertThrows(StoreAccessException.class,
                () -> transaction.executeWithoutResult(ignored -> storeAccess.lockForUse(INACTIVE_ID)));
        EmployeeAccessException employeeError = assertThrows(EmployeeAccessException.class,
                () -> transaction.executeWithoutResult(ignored -> employeeAccess.lockForScheduling(INACTIVE_ID)));
        ContractorAccessException contractorError = assertThrows(ContractorAccessException.class,
                () -> transaction.executeWithoutResult(ignored -> contractorAccess.lockForAssignment(INACTIVE_ID)));

        assertEquals(StoreAccessException.Code.STORE_INACTIVE, storeError.code());
        assertEquals(EmployeeAccessException.Code.EMPLOYEE_INACTIVE, employeeError.code());
        assertEquals(ContractorAccessException.Code.CONTRACTOR_INACTIVE, contractorError.code());
    }

    @Test
    void acBr15EmployeeParentLockSerializesTheEmptyShiftSet() throws Exception {
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> transaction().executeWithoutResult(ignored -> {
                employeeAccess.lockForScheduling(ACTIVE_ID);
                firstLocked.countDown();
                await(releaseFirst, Duration.ofSeconds(5));
            }));
            assertTrue(firstLocked.await(5, TimeUnit.SECONDS));

            Future<?> second = executor.submit(() -> transaction().executeWithoutResult(ignored -> {
                secondStarted.countDown();
                employeeAccess.lockForScheduling(ACTIVE_ID);
            }));
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            assertThrows(java.util.concurrent.TimeoutException.class,
                    () -> second.get(250, TimeUnit.MILLISECONDS));

            releaseFirst.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void acUc10SharedStoreUseLockBlocksDeactivationButAllowsOtherUse() throws Exception {
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(3)) {
            Future<?> firstUse = executor.submit(() -> transaction().executeWithoutResult(ignored -> {
                storeAccess.lockForUse(ACTIVE_ID);
                firstLocked.countDown();
                await(releaseFirst, Duration.ofSeconds(5));
            }));
            assertTrue(firstLocked.await(5, TimeUnit.SECONDS));

            Future<?> secondUse = executor.submit(() -> transaction()
                    .executeWithoutResult(ignored -> storeAccess.lockForUse(ACTIVE_ID)));
            secondUse.get(2, TimeUnit.SECONDS);

            Future<?> deactivation = executor.submit(() -> transaction()
                    .executeWithoutResult(ignored -> storeAccess.lockForDeactivation(ACTIVE_ID)));
            assertThrows(java.util.concurrent.TimeoutException.class,
                    () -> deactivation.get(250, TimeUnit.MILLISECONDS));

            releaseFirst.countDown();
            firstUse.get(5, TimeUnit.SECONDS);
            deactivation.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void acBr06ContractorRateLockBlocksNewAssignment() throws Exception {
        CountDownLatch rateLocked = new CountDownLatch(1);
        CountDownLatch releaseRate = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> rateMaintenance = executor.submit(() -> transaction().executeWithoutResult(ignored -> {
                contractorAccess.lockForRateChange(ACTIVE_ID);
                rateLocked.countDown();
                await(releaseRate, Duration.ofSeconds(5));
            }));
            assertTrue(rateLocked.await(5, TimeUnit.SECONDS));

            Future<?> assignment = executor.submit(() -> transaction()
                    .executeWithoutResult(ignored -> contractorAccess.lockForAssignment(ACTIVE_ID)));
            assertThrows(java.util.concurrent.TimeoutException.class,
                    () -> assignment.get(250, TimeUnit.MILLISECONDS));

            releaseRate.countDown();
            rateMaintenance.get(5, TimeUnit.SECONDS);
            assignment.get(5, TimeUnit.SECONDS);
        }
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private static void await(CountDownLatch latch, Duration timeout) {
        try {
            if (!latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Timed out waiting for test barrier.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for test barrier.", exception);
        }
    }
}
