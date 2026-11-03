package com.bsr.repair.application;

import static com.bsr.repair.application.RepairTestData.CONTRACTOR_ID;
import static com.bsr.repair.application.RepairTestData.CONTRACTOR2_ID;
import static com.bsr.repair.application.RepairTestData.EQUIPMENT_ID;
import static com.bsr.repair.application.RepairTestData.STORE_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bsr.repair.domain.RepairTaskStatus;
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

@SpringBootTest(properties = {
    "spring.profiles.active=test",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@ContextConfiguration(initializers = IsolatedTestDatabaseInitializer.class)
class ContractorRepairWorkflowIT {

    @Autowired RepairTaskService service;
    @Autowired JdbcTemplate jdbc;

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
    @DisplayName("AC-UC08/AC-BR23 current contractor starts, saves labor without a rate, and submits")
    void AC_UC08_BR23_missingRateDoesNotDiscardLabor() {
        long taskId = assignedTask();
        var contractor = RepairTestData.contractor(CONTRACTOR_ID);
        assertEquals(RepairTaskStatus.IN_PROGRESS, service.start(contractor, taskId).status());
        service.addLabor(contractor, taskId, LocalDate.of(2026, 10, 1),
                new BigDecimal("0.25"), "Replaced binding", "Checked fit");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM repair_labor WHERE repair_task_id=?",
                Integer.class, taskId));
        assertEquals(CONTRACTOR_ID, jdbc.queryForObject(
                "SELECT contractor_id FROM repair_labor WHERE repair_task_id=?", Long.class, taskId));
        assertEquals(null, jdbc.queryForObject(
                "SELECT labor_amount FROM repair_labor WHERE repair_task_id=?", BigDecimal.class, taskId));
        assertEquals(RepairTaskStatus.PENDING_ACCEPTANCE, service.submit(contractor, taskId).status());
        assertEquals("PENDING_ACCEPTANCE", jdbc.queryForObject(
                "SELECT equipment_status FROM equipment WHERE equipment_id=?", String.class, EQUIPMENT_ID));
    }

    @Test
    @DisplayName("AC-BR24 0.30 hours is rejected, no row or success audit is saved")
    void AC_BR24_invalidDurationIsAtomic() {
        long taskId = assignedTask();
        var contractor = RepairTestData.contractor(CONTRACTOR_ID);
        service.start(contractor, taskId);
        assertThrows(IllegalArgumentException.class, () -> service.addLabor(contractor, taskId,
                LocalDate.of(2026, 10, 1), new BigDecimal("0.30"), "Repair", null));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM repair_labor WHERE repair_task_id=?",
                Integer.class, taskId));
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_event WHERE action_code='REPAIR_LABOR_ADDED'
                """, Integer.class));
    }

    @Test
    @DisplayName("AC-UC08 another contractor cannot start, save labor, or submit an assigned task")
    void AC_UC08_otherContractorDenied() {
        long taskId = assignedTask();
        var other = RepairTestData.contractor(CONTRACTOR2_ID);
        assertThrows(RuntimeException.class, () -> service.start(other, taskId));
        service.start(RepairTestData.contractor(CONTRACTOR_ID), taskId);
        assertThrows(RuntimeException.class, () -> service.addLabor(other, taskId,
                LocalDate.of(2026, 10, 1), new BigDecimal("0.25"), "Repair", null));
        assertThrows(RuntimeException.class, () -> service.submit(other, taskId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM repair_labor WHERE repair_task_id=?",
                Integer.class, taskId));
    }

    @Test
    @DisplayName("AC-BR23 reassignment retains old contractor labor and blocks old owner")
    void AC_BR23_reassignmentRetainsLaborOwner() {
        long taskId = assignedTask();
        var first = RepairTestData.contractor(CONTRACTOR_ID);
        service.start(first, taskId);
        service.addLabor(first, taskId, LocalDate.of(2026, 10, 1),
                new BigDecimal("1.00"), "First repair", null);
        service.assign(RepairTestData.employee(), taskId, CONTRACTOR2_ID);
        assertThrows(RuntimeException.class, () -> service.addLabor(first, taskId,
                LocalDate.of(2026, 10, 2), new BigDecimal("0.25"), "Old owner", null));
        assertEquals(CONTRACTOR_ID, jdbc.queryForObject(
                "SELECT contractor_id FROM repair_labor WHERE repair_task_id=?", Long.class, taskId));
        assertThrows(RepairOperationException.class,
                () -> service.submit(RepairTestData.contractor(CONTRACTOR2_ID), taskId));
        service.addLabor(RepairTestData.contractor(CONTRACTOR2_ID), taskId,
                LocalDate.of(2026, 10, 2), new BigDecimal("0.25"),
                "Second contractor repair", null);
        assertEquals(RepairTaskStatus.PENDING_ACCEPTANCE,
                service.submit(RepairTestData.contractor(CONTRACTOR2_ID), taskId).status());
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM repair_labor WHERE repair_task_id=?
                AND contractor_id=? AND submitted_at IS NULL
                """, Integer.class, taskId, CONTRACTOR_ID));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM repair_labor WHERE repair_task_id=?
                AND contractor_id=? AND submitted_at IS NOT NULL
                """, Integer.class, taskId, CONTRACTOR2_ID));
    }

    @Test
    @DisplayName("AC-UC08 returned repair requires a new completed labor result before resubmission")
    void AC_UC08_reworkRequiresNewLabor() {
        long taskId = assignedTask();
        var contractor = RepairTestData.contractor(CONTRACTOR_ID);
        service.start(contractor, taskId);
        service.addLabor(contractor, taskId, LocalDate.of(2026, 10, 1),
                new BigDecimal("0.25"), "Initial repair", null);
        service.submit(contractor, taskId);
        service.reject(RepairTestData.employee(), taskId, "Still broken");
        service.start(contractor, taskId);
        assertThrows(RepairOperationException.class, () -> service.submit(contractor, taskId));
        service.addLabor(contractor, taskId, LocalDate.of(2026, 10, 2),
                new BigDecimal("0.25"), "Rework", null);
        assertEquals(RepairTaskStatus.PENDING_ACCEPTANCE, service.submit(contractor, taskId).status());
    }

    @Test
    @DisplayName("AC-BR24 labor matches historical rate by contractor and repair date, not request date")
    void AC_BR24_historicalRateSnapshotUsesRepairDate() {
        long taskId = assignedTask();
        jdbc.update("""
                INSERT INTO repair_rate(contractor_id,hourly_rate,effective_from,effective_to,created_at)
                VALUES(?,100.01,'2026-09-01','2026-09-30',CURRENT_TIMESTAMP),
                      (?,200.01,'2026-10-01',NULL,CURRENT_TIMESTAMP),
                      (?,999.00,'2026-10-01',NULL,CURRENT_TIMESTAMP)
                """, CONTRACTOR_ID, CONTRACTOR_ID, CONTRACTOR2_ID);
        var contractor = RepairTestData.contractor(CONTRACTOR_ID);
        service.start(contractor, taskId);
        var old = service.addLabor(contractor, taskId, LocalDate.of(2026, 9, 30),
                new BigDecimal("0.25"), "Earlier repair", null);
        var current = service.addLabor(contractor, taskId, LocalDate.of(2026, 10, 1),
                new BigDecimal("0.25"), "Current repair", null);
        assertEquals(new BigDecimal("25.00"), old.laborAmount());
        assertEquals(new BigDecimal("50.00"), current.laborAmount());
        assertEquals(new BigDecimal("100.01"), old.hourlyRateSnapshot());
        assertEquals(new BigDecimal("200.01"), current.hourlyRateSnapshot());
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM repair_labor WHERE repair_task_id=? AND repair_rate_id IS NOT NULL",
                Integer.class, taskId));
    }

    @Test
    @DisplayName("AC-UC08 inactive contractor account cannot perform an assigned task")
    void AC_UC08_inactiveContractorDenied() {
        long taskId = assignedTask();
        jdbc.update("UPDATE repair_contractor SET business_status='INACTIVE' WHERE contractor_id=?",
                CONTRACTOR_ID);
        assertThrows(RuntimeException.class,
                () -> service.start(RepairTestData.contractor(CONTRACTOR_ID), taskId));
        assertEquals("PENDING", jdbc.queryForObject(
                "SELECT task_status FROM repair_task WHERE repair_task_id=?", String.class, taskId));
    }

    private long assignedTask() {
        return service.create(RepairTestData.employee(), STORE_ID, EQUIPMENT_ID,
                "Binding damaged", CONTRACTOR_ID).id();
    }
}
