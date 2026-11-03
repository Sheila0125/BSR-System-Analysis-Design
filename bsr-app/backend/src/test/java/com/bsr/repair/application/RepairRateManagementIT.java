package com.bsr.repair.application;

import static com.bsr.repair.application.RepairTestData.CONTRACTOR_ID;
import static com.bsr.repair.application.RepairTestData.CONTRACTOR2_ID;
import static com.bsr.repair.application.RepairTestData.EQUIPMENT_ID;
import static com.bsr.repair.application.RepairTestData.STORE_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

import com.bsr.repair.domain.RepairRatePeriod;
import com.bsr.schema.IsolatedTestDatabaseInitializer;
import com.bsr.platform.error.ApiException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest(properties = {
    "spring.profiles.active=test",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@ContextConfiguration(initializers = IsolatedTestDatabaseInitializer.class)
class RepairRateManagementIT {

    @Autowired RepairContractorService service;
    @Autowired RepairTaskService tasks;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean RepairManagementPersistence persistenceSpy;

    @BeforeEach
    void setUp() {
        RepairTestData.cleanup(jdbc);
        RepairTestData.insert(jdbc);
    }

    @AfterEach
    void tearDown() {
        RepairTestData.cleanup(jdbc);
        jdbc.update("DELETE FROM repair_contractor WHERE email IN ('new-workshop@test.invalid','updated@test.invalid')");
    }

    @Test
    @DisplayName("AC-UC15/AC-BR39 closed interval rates reject shared boundary but allow next day")
    void AC_UC15_BR39_closedIntervalOverlap() {
        service.addRate(RepairTestData.managerOnly(), STORE_ID,
                new RepairRatePeriod(CONTRACTOR_ID, LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 31), new BigDecimal("100.00")));
        assertThrows(RepairOperationException.class, () -> service.addRate(
                RepairTestData.managerOnly(), STORE_ID,
                new RepairRatePeriod(CONTRACTOR_ID, LocalDate.of(2026, 1, 31),
                        null, new BigDecimal("150.00"))));
        service.addRate(RepairTestData.managerOnly(), STORE_ID,
                new RepairRatePeriod(CONTRACTOR_ID, LocalDate.of(2026, 2, 1),
                        null, new BigDecimal("150.00")));
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM repair_rate WHERE contractor_id=?", Integer.class, CONTRACTOR_ID));
    }

    @Test
    @DisplayName("AC-BR39 two MySQL connections cannot commit overlapping first rates")
    void AC_BR39_twoConnectionRateCompetition() throws Exception {
        CountDownLatch firstInserted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicBoolean firstCall = new AtomicBoolean(true);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (firstCall.getAndSet(false)) {
                firstInserted.countDown();
                await(releaseFirst, Duration.ofSeconds(10));
            }
            return result;
        }).when(persistenceSpy).insertRate(any(RepairRatePeriod.class), any());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.addRate(RepairTestData.managerOnly(), STORE_ID,
                    new RepairRatePeriod(CONTRACTOR_ID, LocalDate.of(2026, 1, 1),
                            null, new BigDecimal("100.00"))));
            await(firstInserted, Duration.ofSeconds(10));
            var second = executor.submit(() -> service.addRate(RepairTestData.managerOnly(), STORE_ID,
                    new RepairRatePeriod(CONTRACTOR_ID, LocalDate.of(2026, 2, 1),
                            null, new BigDecimal("150.00"))));
            assertThrows(TimeoutException.class, () -> second.get(300, TimeUnit.MILLISECONDS));
            releaseFirst.countDown();
            first.get(10, TimeUnit.SECONDS);
            assertEquals(RepairOperationException.Code.RATE_OVERLAP,
                    ((RepairOperationException) assertThrows(ExecutionException.class,
                            () -> second.get(10, TimeUnit.SECONDS)).getCause()).code());
        } finally {
            releaseFirst.countDown();
        }
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM repair_rate WHERE contractor_id=?", Integer.class, CONTRACTOR_ID));
    }

    @Test
    @DisplayName("AC-BR39 new rate fills only matching empty labor snapshots and restores pending charge")
    void AC_BR39_backfillOnlyEmptySnapshots() {
        long taskId = tasks.create(RepairTestData.employee(), STORE_ID, EQUIPMENT_ID,
                "Binding damaged", CONTRACTOR_ID).id();
        var contractor = RepairTestData.contractor(CONTRACTOR_ID);
        tasks.start(contractor, taskId);
        tasks.addLabor(contractor, taskId, LocalDate.of(2026, 1, 31),
                new BigDecimal("0.25"), "Earlier repair", null);
        tasks.addLabor(contractor, taskId, LocalDate.of(2026, 2, 1),
                new BigDecimal("0.25"), "Later repair", null);
        tasks.submit(contractor, taskId);
        tasks.accept(RepairTestData.employee(), taskId);

        service.addRate(RepairTestData.managerOnly(), STORE_ID,
                new RepairRatePeriod(CONTRACTOR_ID, LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 31), new BigDecimal("100.01")));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM repair_labor WHERE repair_task_id=? AND labor_amount IS NULL",
                Integer.class, taskId));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM repair_service_charge WHERE repair_task_id=?",
                Integer.class, taskId));
        service.addRate(RepairTestData.managerOnly(), STORE_ID,
                new RepairRatePeriod(CONTRACTOR_ID, LocalDate.of(2026, 2, 1),
                        null, new BigDecimal("200.01")));
        assertEquals(new BigDecimal("75.00"), jdbc.queryForObject(
                "SELECT service_amount FROM repair_service_charge WHERE repair_task_id=?",
                BigDecimal.class, taskId));
        assertEquals(new BigDecimal("25.00"), jdbc.queryForObject(
                "SELECT labor_amount FROM repair_labor WHERE repair_task_id=? AND repair_date='2026-01-31'",
                BigDecimal.class, taskId));
        assertEquals("PENDING_CHARGE", jdbc.queryForObject(
                "SELECT task_status FROM repair_task WHERE repair_task_id=?", String.class, taskId));
        tasks.confirmCharge(RepairTestData.employee(), taskId);
        assertEquals("COMPLETED", jdbc.queryForObject(
                "SELECT task_status FROM repair_task WHERE repair_task_id=?", String.class, taskId));
    }

    @Test
    @DisplayName("AC-UC15 manager cannot deactivate a contractor with unfinished tasks")
    void AC_UC15_deactivationConstraint() {
        tasks.create(RepairTestData.employee(), STORE_ID, EQUIPMENT_ID,
                "Damaged", CONTRACTOR_ID);
        assertThrows(RepairOperationException.class,
                () -> service.deactivate(RepairTestData.managerOnly(), STORE_ID, CONTRACTOR_ID));
        assertEquals("ACTIVE", jdbc.queryForObject(
                "SELECT business_status FROM repair_contractor WHERE contractor_id=?",
                String.class, CONTRACTOR_ID));
        service.deactivate(RepairTestData.managerOnly(), STORE_ID, CONTRACTOR2_ID);
        assertEquals("INACTIVE", jdbc.queryForObject(
                "SELECT business_status FROM repair_contractor WHERE contractor_id=?",
                String.class, CONTRACTOR2_ID));
    }

    @Test
    @DisplayName("AC-UC15 manager creates and updates a contractor without changing identity or historical rows")
    void AC_UC15_createAndUpdateContractor() {
        var created = service.create(RepairTestData.managerOnly(), STORE_ID,
                "New Workshop", "Lars", "new-workshop@test.invalid", "+47 12345678");
        assertEquals(true, created.active());
        var updated = service.update(RepairTestData.managerOnly(), STORE_ID, created.id(),
                "Updated Workshop", "Lars", "updated@test.invalid", null);
        assertEquals(created.id(), updated.id());
        assertEquals(created.number(), updated.number());
        assertEquals("Updated Workshop", jdbc.queryForObject(
                "SELECT contractor_name FROM repair_contractor WHERE contractor_id=?",
                String.class, created.id()));
        assertFalse(service.list(RepairTestData.managerOnly(), STORE_ID).isEmpty());
    }

    @Test
    @DisplayName("AC-UC15 ordinary employee cannot maintain contractor rates")
    void AC_UC15_employeeCannotSetRate() {
        assertThrows(ApiException.class, () -> service.addRate(RepairTestData.employee(),
                STORE_ID, new RepairRatePeriod(CONTRACTOR_ID, LocalDate.of(2026, 1, 1),
                        null, new BigDecimal("100.00"))));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM repair_rate WHERE contractor_id=?",
                Integer.class, CONTRACTOR_ID));
    }

    private static void await(CountDownLatch latch, Duration timeout) {
        try {
            if (!latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new AssertionError("Timed out waiting for rate test barrier");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for rate test barrier", interrupted);
        }
    }
}
