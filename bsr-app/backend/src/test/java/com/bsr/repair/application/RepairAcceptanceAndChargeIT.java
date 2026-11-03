package com.bsr.repair.application;

import static com.bsr.repair.application.RepairTestData.CONTRACTOR_ID;
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
class RepairAcceptanceAndChargeIT {

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
    @DisplayName("AC-UC09/AC-BR25 failed acceptance needs a reason and permits rework")
    void AC_UC09_BR25_rejectThenRework() {
        long taskId = submittedTask(false);
        assertThrows(IllegalArgumentException.class,
                () -> service.reject(RepairTestData.employee(), taskId, "  "));
        assertEquals("PENDING_ACCEPTANCE", jdbc.queryForObject(
                "SELECT task_status FROM repair_task WHERE repair_task_id=?", String.class, taskId));
        assertEquals(RepairTaskStatus.RETURNED,
                service.reject(RepairTestData.employee(), taskId, "Still loose").status());
        assertEquals("WAITING_REPAIR", jdbc.queryForObject(
                "SELECT equipment_status FROM equipment WHERE equipment_id=?", String.class, EQUIPMENT_ID));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM equipment_inspection
                WHERE repair_task_id=? AND inspection_result='FAULT'
                """, Integer.class, taskId));
        service.start(RepairTestData.contractor(CONTRACTOR_ID), taskId);
        service.addLabor(RepairTestData.contractor(CONTRACTOR_ID), taskId,
                LocalDate.of(2026, 10, 2), new BigDecimal("0.25"), "Rework", null);
        service.submit(RepairTestData.contractor(CONTRACTOR_ID), taskId);
        assertEquals(RepairTaskStatus.PENDING_CHARGE,
                service.accept(RepairTestData.employee(), taskId).status());
    }

    @Test
    @DisplayName("AC-BR26 accepted repair with historical rate becomes rentable before fee confirmation")
    void AC_BR26_acceptCreatesPendingChargeThenConfirmsOnce() {
        long taskId = submittedTask(true);
        assertEquals(RepairTaskStatus.PENDING_CHARGE,
                service.accept(RepairTestData.employee(), taskId).status());
        assertEquals("AVAILABLE", jdbc.queryForObject(
                "SELECT equipment_status FROM equipment WHERE equipment_id=?", String.class, EQUIPMENT_ID));
        assertEquals(new BigDecimal("12.50"), jdbc.queryForObject(
                "SELECT service_amount FROM repair_service_charge WHERE repair_task_id=?",
                BigDecimal.class, taskId));
        assertEquals("PENDING_CONFIRMATION", jdbc.queryForObject(
                "SELECT charge_status FROM repair_service_charge WHERE repair_task_id=?",
                String.class, taskId));
        assertEquals(RepairTaskStatus.COMPLETED,
                service.confirmCharge(RepairTestData.employee(), taskId).status());
        assertEquals(RepairTaskStatus.COMPLETED,
                service.confirmCharge(RepairTestData.employee(), taskId).status());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM repair_service_charge WHERE repair_task_id=?",
                Integer.class, taskId));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_event WHERE action_code='REPAIR_CHARGE_CONFIRMED'
                """, Integer.class));
    }

    @Test
    @DisplayName("AC-BR26 missing historical rate does not block physical acceptance but blocks fee completion")
    void AC_BR26_missingRateRemainsPendingCharge() {
        long taskId = submittedTask(false);
        service.accept(RepairTestData.employee(), taskId);
        assertEquals("AVAILABLE", jdbc.queryForObject(
                "SELECT equipment_status FROM equipment WHERE equipment_id=?", String.class, EQUIPMENT_ID));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM repair_service_charge WHERE repair_task_id=?",
                Integer.class, taskId));
        assertThrows(RepairOperationException.class,
                () -> service.confirmCharge(RepairTestData.employee(), taskId));
        assertEquals("PENDING_CHARGE", jdbc.queryForObject(
                "SELECT task_status FROM repair_task WHERE repair_task_id=?", String.class, taskId));
    }

    @Test
    @DisplayName("AC-BR26 later charge confirmation never rewrites an already-rented equipment state")
    void AC_BR26_lateChargeLeavesRentalStateIntact() {
        long taskId = submittedTask(true);
        service.accept(RepairTestData.employee(), taskId);
        jdbc.update("UPDATE equipment SET equipment_status='RENTED' WHERE equipment_id=?", EQUIPMENT_ID);
        service.confirmCharge(RepairTestData.employee(), taskId);
        assertEquals("RENTED", jdbc.queryForObject(
                "SELECT equipment_status FROM equipment WHERE equipment_id=?", String.class, EQUIPMENT_ID));
    }

    private long submittedTask(boolean withRate) {
        if (withRate) {
            jdbc.update("""
                    INSERT INTO repair_rate(contractor_id,hourly_rate,effective_from,effective_to,created_at)
                    VALUES(?,50.00,'2026-01-01',NULL,CURRENT_TIMESTAMP)
                    """, CONTRACTOR_ID);
        }
        long taskId = service.create(RepairTestData.employee(), STORE_ID, EQUIPMENT_ID,
                "Damaged binding", CONTRACTOR_ID).id();
        var contractor = RepairTestData.contractor(CONTRACTOR_ID);
        service.start(contractor, taskId);
        service.addLabor(contractor, taskId, LocalDate.of(2026, 10, 1),
                new BigDecimal("0.25"), "Replaced binding", null);
        service.submit(contractor, taskId);
        return taskId;
    }
}
