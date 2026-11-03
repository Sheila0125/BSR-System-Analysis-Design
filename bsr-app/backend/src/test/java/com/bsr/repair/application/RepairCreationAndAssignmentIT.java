package com.bsr.repair.application;

import static com.bsr.repair.application.RepairTestData.CONTRACTOR_ID;
import static com.bsr.repair.application.RepairTestData.CONTRACTOR2_ID;
import static com.bsr.repair.application.RepairTestData.EMPLOYEE_ACCOUNT_ID;
import static com.bsr.repair.application.RepairTestData.EQUIPMENT_ID;
import static com.bsr.repair.application.RepairTestData.STORE_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;


import com.bsr.platform.error.ApiException;
import com.bsr.repair.domain.RepairTaskStatus;
import com.bsr.repair.domain.RepairTask;
import com.bsr.schema.IsolatedTestDatabaseInitializer;
import java.time.Duration;
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
class RepairCreationAndAssignmentIT {

    @Autowired RepairTaskService service;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean RepairPersistence persistenceSpy;

    @BeforeEach
    void setUp() {
        RepairTestData.cleanup(jdbc);
        RepairTestData.insert(jdbc);
    }

    @AfterEach
    void tearDown() {
        RepairTestData.cleanup(jdbc);
    }

    @Test
    @DisplayName("AC-BR22 two MySQL connections competing for first repair task leave exactly one task")
    void AC_BR22_twoConnectionFirstTaskCompetition() throws Exception {
        CountDownLatch firstInserted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicBoolean firstCall = new AtomicBoolean(true);
        doAnswer(invocation -> {
            Object saved = invocation.callRealMethod();
            if (firstCall.getAndSet(false)) {
                firstInserted.countDown();
                await(releaseFirst, Duration.ofSeconds(10));
            }
            return saved;
        }).when(persistenceSpy).insertTask(anyString(), anyLong(), anyLong(), anyLong(),
                any(), anyString(), any(RepairTask.class));

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.create(RepairTestData.employee(), STORE_ID,
                    EQUIPMENT_ID, "First fault", null));
            await(firstInserted, Duration.ofSeconds(10));
            var second = executor.submit(() -> {
                secondStarted.countDown();
                return service.create(RepairTestData.employee(), STORE_ID,
                        EQUIPMENT_ID, "Competing fault", null);
            });
            await(secondStarted, Duration.ofSeconds(10));
            assertThrows(TimeoutException.class, () -> second.get(300, TimeUnit.MILLISECONDS));
            assertFalse(first.isDone());
            releaseFirst.countDown();
            first.get(10, TimeUnit.SECONDS);
            ExecutionException conflict = assertThrows(ExecutionException.class,
                    () -> second.get(10, TimeUnit.SECONDS));
            assertEquals(RepairOperationException.Code.UNFINISHED_TASK_EXISTS,
                    assertInstanceOf(RepairOperationException.class, conflict.getCause()).code());
        } finally {
            releaseFirst.countDown();
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM repair_task WHERE equipment_id=?",
                Integer.class, EQUIPMENT_ID));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_event WHERE actor_account_id=? AND action_code='REPAIR_TASK_CREATED'
                """, Integer.class, EMPLOYEE_ACCOUNT_ID));
    }

    @Test
    @DisplayName("AC-UC07/AC-BR21 employee creates one unassigned task with same-transaction success audit")
    void AC_UC07_BR21_createUnassignedTask() {
        var task = service.create(RepairTestData.employee(), STORE_ID, EQUIPMENT_ID,
                "Broken binding", null);
        assertEquals(RepairTaskStatus.UNASSIGNED, task.status());
        assertEquals(EQUIPMENT_ID, task.equipmentId());
        assertEquals(STORE_ID, task.serviceStoreId());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM repair_task WHERE equipment_id=? AND task_status='UNASSIGNED'",
                Integer.class, EQUIPMENT_ID));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_event
                WHERE actor_account_id=? AND object_type='REPAIR_TASK' AND action_code='REPAIR_TASK_CREATED'
                """, Integer.class, EMPLOYEE_ACCOUNT_ID));
        assertEquals("WAITING_REPAIR", jdbc.queryForObject(
                "SELECT equipment_status FROM equipment WHERE equipment_id=?", String.class, EQUIPMENT_ID));
    }

    @Test
    @DisplayName("AC-BR22 pending-charge or any other unfinished task blocks a second task")
    void AC_BR22_duplicateUnfinishedTaskConflicts() {
        service.create(RepairTestData.employee(), STORE_ID, EQUIPMENT_ID, "Broken binding", null);
        RepairOperationException conflict = assertThrows(RepairOperationException.class,
                () -> service.create(RepairTestData.employee(), STORE_ID, EQUIPMENT_ID,
                        "Another repair", CONTRACTOR_ID));
        assertEquals(RepairOperationException.Code.UNFINISHED_TASK_EXISTS, conflict.code());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM repair_task WHERE equipment_id=?",
                Integer.class, EQUIPMENT_ID));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_event WHERE actor_account_id=? AND action_code='REPAIR_TASK_CREATED'
                """, Integer.class, EMPLOYEE_ACCOUNT_ID));
    }

    @Test
    @DisplayName("AC-UC07/AC-BR23 employee can assign and reassign pending work without changing the equipment")
    void AC_UC07_BR23_assignAndReassign() {
        var created = service.create(RepairTestData.employee(), STORE_ID, EQUIPMENT_ID,
                "Broken binding", null);
        var assigned = service.assign(RepairTestData.employee(), created.id(), CONTRACTOR_ID);
        assertEquals(RepairTaskStatus.PENDING, assigned.status());
        assertEquals(CONTRACTOR_ID, assigned.currentContractorId());

        var reassigned = service.assign(RepairTestData.employee(), created.id(), CONTRACTOR2_ID);
        assertEquals(RepairTaskStatus.PENDING, reassigned.status());
        assertEquals(CONTRACTOR2_ID, reassigned.currentContractorId());
        assertEquals(CONTRACTOR2_ID, jdbc.queryForObject(
                "SELECT current_contractor_id FROM repair_task WHERE repair_task_id=?",
                Long.class, created.id()));
        assertEquals("WAITING_REPAIR", jdbc.queryForObject(
                "SELECT equipment_status FROM equipment WHERE equipment_id=?", String.class, EQUIPMENT_ID));
        assertEquals(2, jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_event WHERE actor_account_id=? AND action_code='REPAIR_TASK_ASSIGNED'
                """, Integer.class, EMPLOYEE_ACCOUNT_ID));
    }

    @Test
    @DisplayName("AC-BR39 an inactive contractor cannot receive an unfinished task")
    void AC_BR39_inactiveContractorCannotBeAssigned() {
        var created = service.create(RepairTestData.employee(), STORE_ID, EQUIPMENT_ID,
                "Broken binding", null);
        jdbc.update("UPDATE repair_contractor SET business_status='INACTIVE' WHERE contractor_id=?",
                CONTRACTOR_ID);
        assertThrows(com.bsr.repair.contractor.api.ContractorAccessException.class,
                () -> service.assign(RepairTestData.employee(), created.id(), CONTRACTOR_ID));
        assertEquals("UNASSIGNED", jdbc.queryForObject(
                "SELECT task_status FROM repair_task WHERE repair_task_id=?", String.class, created.id()));
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_event WHERE actor_account_id=? AND action_code='REPAIR_TASK_ASSIGNED'
                """, Integer.class, EMPLOYEE_ACCOUNT_ID));
    }

    @Test
    @DisplayName("AC-UC07 manager-only identity cannot use the employee repair action")
    void AC_UC07_managerDoesNotInheritEmployeePermission() {
        ApiException denied = assertThrows(ApiException.class,
                () -> service.create(RepairTestData.managerOnly(), STORE_ID, EQUIPMENT_ID,
                        "Broken binding", null));
        assertEquals("ACTION_NOT_AUTHORIZED", denied.code());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM repair_task WHERE equipment_id=?",
                Integer.class, EQUIPMENT_ID));
    }

    private static void await(CountDownLatch latch, Duration timeout) {
        try {
            if (!latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new AssertionError("Timed out waiting for repair test barrier");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for repair test barrier", interrupted);
        }
    }
}
