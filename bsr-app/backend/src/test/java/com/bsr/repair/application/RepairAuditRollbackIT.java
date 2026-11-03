package com.bsr.repair.application;

import static com.bsr.repair.application.RepairTestData.EQUIPMENT_ID;
import static com.bsr.repair.application.RepairTestData.STORE_ID;
import static com.bsr.repair.application.RepairTestData.CONTRACTOR_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import com.bsr.audit.api.AuditAppender;
import com.bsr.schema.IsolatedTestDatabaseInitializer;
import java.math.BigDecimal;
import java.time.LocalDate;
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
class RepairAuditRollbackIT {

    @Autowired RepairTaskService service;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean AuditAppender audit;

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
    @DisplayName("AC-UC07 audit failure rolls back task creation but leaves preexisting equipment fact")
    void AC_UC07_auditFailureRollsBackCreation() {
        doThrow(new IllegalStateException("audit unavailable"))
                .when(audit).appendSuccess(any(), eq("REPAIR_TASK"), anyLong(),
                        eq("REPAIR_TASK_CREATED"), anyString());
        assertThrows(IllegalStateException.class,
                () -> service.create(RepairTestData.employee(), STORE_ID, EQUIPMENT_ID,
                        "Broken binding", null));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM repair_task WHERE equipment_id=?",
                Integer.class, EQUIPMENT_ID));
        assertEquals("WAITING_REPAIR", jdbc.queryForObject(
                "SELECT equipment_status FROM equipment WHERE equipment_id=?", String.class, EQUIPMENT_ID));
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_event WHERE actor_account_id=? AND action_code='REPAIR_TASK_CREATED'
                """, Integer.class, RepairTestData.EMPLOYEE_ACCOUNT_ID));
    }

    @Test
    @DisplayName("AC-UC09 acceptance audit failure rolls back task, inspection and equipment together")
    void AC_UC09_acceptanceAuditFailureRollsBackAllCurrentChanges() {
        long taskId = service.create(RepairTestData.employee(), STORE_ID, EQUIPMENT_ID,
                "Broken binding", CONTRACTOR_ID).id();
        var contractor = RepairTestData.contractor(CONTRACTOR_ID);
        service.start(contractor, taskId);
        service.addLabor(contractor, taskId, LocalDate.of(2026, 10, 1),
                new BigDecimal("0.25"), "Repaired", null);
        service.submit(contractor, taskId);
        doThrow(new IllegalStateException("audit unavailable"))
                .when(audit).appendSuccess(any(), eq("REPAIR_TASK"), eq(taskId),
                        eq("REPAIR_TASK_ACCEPTED"), anyString());
        assertThrows(IllegalStateException.class,
                () -> service.accept(RepairTestData.employee(), taskId));
        assertEquals("PENDING_ACCEPTANCE", jdbc.queryForObject(
                "SELECT task_status FROM repair_task WHERE repair_task_id=?", String.class, taskId));
        assertEquals("PENDING_ACCEPTANCE", jdbc.queryForObject(
                "SELECT equipment_status FROM equipment WHERE equipment_id=?", String.class, EQUIPMENT_ID));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM equipment_inspection WHERE repair_task_id=?",
                Integer.class, taskId));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM repair_service_charge WHERE repair_task_id=?",
                Integer.class, taskId));
    }
}
