package com.bsr.equipment.application;

import static com.bsr.equipment.application.EquipmentTestData.ACCOUNT_ID;
import static com.bsr.equipment.application.EquipmentTestData.CONCURRENT_EQUIPMENT_ID;
import static com.bsr.equipment.application.EquipmentTestData.CONTRACT_ID;
import static com.bsr.equipment.application.EquipmentTestData.EMPLOYEE_ID;
import static com.bsr.equipment.application.EquipmentTestData.RENTAL_ITEM_ID;
import static com.bsr.equipment.application.EquipmentTestData.RETURN_EQUIPMENT_ID;
import static com.bsr.equipment.application.EquipmentTestData.RETURN_STORE_ID;
import static com.bsr.equipment.application.EquipmentTestData.SOURCE_STORE_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bsr.equipment.api.EquipmentOperationException;
import com.bsr.equipment.api.EquipmentOperations;
import com.bsr.equipment.api.InspectionOperations;
import com.bsr.equipment.api.InspectionOutcome;
import com.bsr.equipment.api.EquipmentSnapshot;
import com.bsr.equipment.domain.EquipmentStatus;
import com.bsr.equipment.domain.InspectionResult;
import com.bsr.identity.api.ActiveRoleGrant;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.IdentityType;
import com.bsr.identity.api.RoleCode;
import com.bsr.platform.error.ApiException;
import com.bsr.schema.IsolatedTestDatabaseInitializer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
class EquipmentOperationsIT {

    @Autowired
    private EquipmentOperations equipmentOperations;

    @Autowired
    private InspectionOperations inspectionOperations;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void insertEquipmentFixture() {
        EquipmentTestData.cleanup(jdbcTemplate);
        EquipmentTestData.insert(jdbcTemplate);
    }

    @Test
    @DisplayName("AC-UC07 owner API locks waiting-repair equipment and rejects an invalid starting state")
    void AC_UC07_repairTaskCreationRequiresLockedWaitingEquipment() {
        jdbcTemplate.update("UPDATE equipment SET equipment_status='WAITING_REPAIR' WHERE equipment_id=?", CONCURRENT_EQUIPMENT_ID);
        EquipmentSnapshot snapshot = transaction().execute(s ->
                equipmentOperations.lockForRepairTaskCreation(employee(), CONCURRENT_EQUIPMENT_ID));
        assertEquals(EquipmentStatus.WAITING_REPAIR, snapshot.status());
        assertEquals(SOURCE_STORE_ID, snapshot.currentStoreId());

        jdbcTemplate.update("UPDATE equipment SET equipment_status='AVAILABLE' WHERE equipment_id=?", CONCURRENT_EQUIPMENT_ID);
        EquipmentOperationException conflict = assertThrows(EquipmentOperationException.class, () ->
                transaction().execute(s -> equipmentOperations.lockForRepairTaskCreation(employee(), CONCURRENT_EQUIPMENT_ID)));
        assertEquals(EquipmentOperationException.Code.EQUIPMENT_STATE_CONFLICT, conflict.code());
        assertEquals("AVAILABLE", jdbcTemplate.queryForObject(
                "SELECT equipment_status FROM equipment WHERE equipment_id=?", String.class, CONCURRENT_EQUIPMENT_ID));
    }

    @Test
    @DisplayName("AC-UC09 repair acceptance exposes public outcome and records real equipment inspection")
    void AC_UC09_publicRepairAcceptanceOutcome() {
        long repairTaskId = EquipmentTestData.REPAIR_TASK_ID;
        jdbcTemplate.update("UPDATE equipment SET equipment_status='PENDING_ACCEPTANCE' WHERE equipment_id=?", CONCURRENT_EQUIPMENT_ID);
        jdbcTemplate.update("""
                INSERT INTO repair_task(repair_task_id,repair_task_number,equipment_id,requested_by_employee_id,
                    service_store_id,request_date,fault_description,task_status)
                VALUES(?,'RT-EQ-PUBLIC',?,?,?,'2026-10-01','Repair inspection fixture','PENDING_ACCEPTANCE')
                """, repairTaskId, CONCURRENT_EQUIPMENT_ID, EMPLOYEE_ID, SOURCE_STORE_ID);

        var inspected = transaction().execute(s -> inspectionOperations.inspectRepairAcceptance(
                employee(), repairTaskId, CONCURRENT_EQUIPMENT_ID, InspectionOutcome.NORMAL, null));
        assertEquals(EquipmentStatus.AVAILABLE, inspected.equipment().status());
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM equipment_inspection
                WHERE repair_task_id=? AND equipment_id=? AND inspection_result='NORMAL'
                """, Integer.class, repairTaskId, CONCURRENT_EQUIPMENT_ID));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM audit_event WHERE actor_account_id=? AND action_code='EQUIPMENT_REPAIR_INSPECTED'
                """, Integer.class, ACCOUNT_ID));
    }

    @Test
    @DisplayName("AC-UC04 draft replacement locks old and new together; failure cannot release old")
    void draftReplacementIsAtomic() {
        jdbcTemplate.update("UPDATE equipment SET current_store_id=?,equipment_status='RENTED' WHERE equipment_id=?",SOURCE_STORE_ID,RETURN_EQUIPMENT_ID);
        transaction().executeWithoutResult(s -> equipmentOperations.reserveForRental(employee(),CONTRACT_ID,SOURCE_STORE_ID,List.of(CONCURRENT_EQUIPMENT_ID)));
        assertThrows(EquipmentOperationException.class, () -> transaction().execute(s ->
                equipmentOperations.replaceBeforeIssue(employee(),CONTRACT_ID,SOURCE_STORE_ID,List.of(CONCURRENT_EQUIPMENT_ID),List.of(RETURN_EQUIPMENT_ID))));
        assertEquals("PENDING_ISSUE",jdbcTemplate.queryForObject("SELECT equipment_status FROM equipment WHERE equipment_id=?",String.class,CONCURRENT_EQUIPMENT_ID));
        assertEquals(0,jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_event WHERE actor_account_id=? AND action_code='EQUIPMENT_DRAFT_REPLACED'",Integer.class,ACCOUNT_ID));
        jdbcTemplate.update("UPDATE equipment SET equipment_status='AVAILABLE' WHERE equipment_id=?",RETURN_EQUIPMENT_ID);
        var replaced=transaction().execute(s -> equipmentOperations.replaceBeforeIssue(employee(),CONTRACT_ID,SOURCE_STORE_ID,List.of(CONCURRENT_EQUIPMENT_ID),List.of(RETURN_EQUIPMENT_ID)));
        assertEquals(2,replaced.size());
        assertEquals("AVAILABLE",jdbcTemplate.queryForObject("SELECT equipment_status FROM equipment WHERE equipment_id=?",String.class,CONCURRENT_EQUIPMENT_ID));
        assertEquals("PENDING_ISSUE",jdbcTemplate.queryForObject("SELECT equipment_status FROM equipment WHERE equipment_id=?",String.class,RETURN_EQUIPMENT_ID));
        assertEquals(2,jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_event WHERE actor_account_id=? AND action_code='EQUIPMENT_DRAFT_REPLACED'",Integer.class,ACCOUNT_ID));
    }

    @AfterEach
    void removeEquipmentFixture() {
        EquipmentTestData.cleanup(jdbcTemplate);
    }

    @Test
    @DisplayName("AC-UC05 customer rejection releases only incoming equipment of the owned rejected adjustment")
    void customerRejectionReleaseIsRelationshipScoped() {
        transaction().executeWithoutResult(s -> equipmentOperations.reserveForRental(employee(),CONTRACT_ID,SOURCE_STORE_ID,List.of(CONCURRENT_EQUIPMENT_ID)));
        jdbcTemplate.update("INSERT INTO rental_change(rental_change_id,change_number,contract_id,change_type,change_status,handled_by_employee_id,effective_date,amount_delta,changed_at,requested_store_id) VALUES(28013,'CG-EQ-REJECT',?,'ADD','REJECTED',?,'2026-10-01',100.00,'2026-10-01 09:00:00',?)",CONTRACT_ID,EMPLOYEE_ID,SOURCE_STORE_ID);
        jdbcTemplate.update("INSERT INTO rental_change_item(rental_change_id,rental_item_id,effect_code) VALUES(28013,?,'ADD')",EquipmentTestData.CONCURRENT_RENTAL_ITEM_ID);
        var owned=new ActorContext(EquipmentTestData.CUSTOMER_ACCOUNT_ID,"equipment-test-customer",IdentityType.CUSTOMER,EquipmentTestData.CUSTOMER_ID,List.of(new ActiveRoleGrant(RoleCode.CUSTOMER,null)));
        var unowned=new ActorContext(EquipmentTestData.CUSTOMER_ACCOUNT_ID,"equipment-test-customer",IdentityType.CUSTOMER,EquipmentTestData.CUSTOMER_ID+100,List.of(new ActiveRoleGrant(RoleCode.CUSTOMER,null)));
        assertThrows(EquipmentOperationException.class,()->transaction().execute(s -> equipmentOperations.releaseRejectedAddition(unowned,CONTRACT_ID,28013,SOURCE_STORE_ID,List.of(CONCURRENT_EQUIPMENT_ID))));
        jdbcTemplate.update("UPDATE rental_change SET change_status='PROCESSING' WHERE rental_change_id=28013");
        assertThrows(EquipmentOperationException.class,()->transaction().execute(s -> equipmentOperations.releaseRejectedAddition(owned,CONTRACT_ID,28013,SOURCE_STORE_ID,List.of(CONCURRENT_EQUIPMENT_ID))));
        jdbcTemplate.update("UPDATE rental_change SET change_status='REJECTED' WHERE rental_change_id=28013");
        transaction().executeWithoutResult(s -> equipmentOperations.releaseRejectedAddition(owned,CONTRACT_ID,28013,SOURCE_STORE_ID,List.of(CONCURRENT_EQUIPMENT_ID)));
        assertEquals("AVAILABLE",jdbcTemplate.queryForObject("SELECT equipment_status FROM equipment WHERE equipment_id=?",String.class,CONCURRENT_EQUIPMENT_ID));
        assertEquals(1,jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_event WHERE actor_account_id=? AND action_code='EQUIPMENT_REJECTION_RELEASED'",Integer.class,EquipmentTestData.CUSTOMER_ACCOUNT_ID));
    }

    @Test
    @DisplayName("AC-UC10/AC-BR13 return inspection persists append-only fact, location and success audit")
    void AC_UC10_BR13_returnInspectionPersistsFactLocationAndAudit() {
        var inspected = transaction().execute(status -> inspectionOperations.inspectRentalReturn(
                employee(),
                RENTAL_ITEM_ID,
                RETURN_EQUIPMENT_ID,
                InspectionOutcome.NORMAL,
                "  no fault found  "));

        assertEquals(EquipmentStatus.AVAILABLE, inspected.equipment().status());
        assertEquals(SOURCE_STORE_ID, inspected.equipment().homeStoreId());
        assertEquals(RETURN_STORE_ID, inspected.equipment().currentStoreId());
        assertEquals("no fault found", inspected.faultDescription());
        assertEquals("AVAILABLE", jdbcTemplate.queryForObject(
                "SELECT equipment_status FROM equipment WHERE equipment_id = ?",
                String.class,
                RETURN_EQUIPMENT_ID));
        assertEquals(SOURCE_STORE_ID, jdbcTemplate.queryForObject(
                "SELECT home_store_id FROM equipment WHERE equipment_id = ?",
                Long.class,
                RETURN_EQUIPMENT_ID));
        assertEquals(RETURN_STORE_ID, jdbcTemplate.queryForObject(
                "SELECT current_store_id FROM equipment WHERE equipment_id = ?",
                Long.class,
                RETURN_EQUIPMENT_ID));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM equipment_inspection WHERE equipment_id = ? AND rental_item_id = ? AND inspection_type = 'RETURN' AND inspection_result = 'NORMAL'",
                Integer.class,
                RETURN_EQUIPMENT_ID,
                RENTAL_ITEM_ID));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE object_type = 'EQUIPMENT' AND object_id = ? AND action_code = 'EQUIPMENT_RETURN_INSPECTED'",
                Integer.class,
                RETURN_EQUIPMENT_ID));

        var available = equipmentOperations.selectAvailable(
                employee(), RETURN_STORE_ID, "SKI", "INTERMEDIATE");
        assertEquals(List.of(RETURN_EQUIPMENT_ID), available.stream().map(item -> item.equipmentId()).toList());
    }

    @Test
    @DisplayName("AC-BR13 inspection rejects a rental item that belongs to different equipment")
    void AC_BR13_inspectionRejectsMismatchedEquipmentReference() {
        jdbcTemplate.update(
                "UPDATE equipment SET equipment_status = 'RETURN_INSPECTION' WHERE equipment_id = ?",
                CONCURRENT_EQUIPMENT_ID);

        EquipmentOperationException mismatch = assertThrows(
                EquipmentOperationException.class,
                () -> transaction().executeWithoutResult(status -> inspectionOperations.inspectReturn(
                        employee(),
                        RENTAL_ITEM_ID,
                        CONCURRENT_EQUIPMENT_ID,
                        InspectionResult.NORMAL,
                        null)));

        assertEquals(
                EquipmentOperationException.Code.EQUIPMENT_REFERENCE_CONFLICT,
                mismatch.code());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM equipment_inspection WHERE equipment_id = ?",
                Integer.class,
                CONCURRENT_EQUIPMENT_ID));
    }

    @Test
    @DisplayName("AC-BR14 available-equipment query rejects a store outside explicit employee scope")
    void AC_BR14_availableQueryRequiresExplicitStoreScope() {
        ActorContext sourceOnly = new ActorContext(
                ACCOUNT_ID,
                "equipment-test-employee",
                IdentityType.EMPLOYEE,
                EMPLOYEE_ID,
                List.of(new ActiveRoleGrant(RoleCode.EMPLOYEE, SOURCE_STORE_ID)));

        ApiException denied = assertThrows(
                ApiException.class,
                () -> equipmentOperations.selectAvailable(
                        sourceOnly, RETURN_STORE_ID, "SKI", "INTERMEDIATE"));

        assertEquals("ACTION_NOT_AUTHORIZED", denied.code());
    }

    @Test
    @DisplayName("AC-BR40 two real MySQL transactions serialize on one equipment row and only one reserves")
    void AC_BR40_sameEquipmentReservationCompetition() throws Exception {
        CountDownLatch firstMutated = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> transaction().executeWithoutResult(status -> {
                equipmentOperations.reserveForRental(
                        employee(), CONTRACT_ID, SOURCE_STORE_ID, List.of(CONCURRENT_EQUIPMENT_ID));
                firstMutated.countDown();
                await(releaseFirst, Duration.ofSeconds(10));
            }));
            await(firstMutated, Duration.ofSeconds(10));

            Future<?> second = executor.submit(() -> {
                secondStarted.countDown();
                transaction().executeWithoutResult(status -> equipmentOperations.reserveForRental(
                        employee(), CONTRACT_ID, SOURCE_STORE_ID, List.of(CONCURRENT_EQUIPMENT_ID)));
            });
            await(secondStarted, Duration.ofSeconds(10));
            assertThrows(TimeoutException.class, () -> second.get(300, TimeUnit.MILLISECONDS));
            assertFalse(first.isDone());

            releaseFirst.countDown();
            first.get(10, TimeUnit.SECONDS);
            ExecutionException conflict = assertThrows(
                    ExecutionException.class,
                    () -> second.get(10, TimeUnit.SECONDS));
            EquipmentOperationException operationException = assertInstanceOf(
                    EquipmentOperationException.class,
                    conflict.getCause());
            assertEquals(
                    EquipmentOperationException.Code.EQUIPMENT_STATE_CONFLICT,
                    operationException.code());
        } finally {
            releaseFirst.countDown();
        }

        assertEquals("PENDING_ISSUE", jdbcTemplate.queryForObject(
                "SELECT equipment_status FROM equipment WHERE equipment_id = ?",
                String.class,
                CONCURRENT_EQUIPMENT_ID));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE object_type = 'EQUIPMENT' AND object_id = ? AND action_code = 'EQUIPMENT_RESERVED'",
                Integer.class,
                CONCURRENT_EQUIPMENT_ID));
    }

    private ActorContext employee() {
        return new ActorContext(
                ACCOUNT_ID,
                "equipment-test-employee",
                IdentityType.EMPLOYEE,
                EMPLOYEE_ID,
                List.of(
                        new ActiveRoleGrant(RoleCode.EMPLOYEE, SOURCE_STORE_ID),
                        new ActiveRoleGrant(RoleCode.EMPLOYEE, RETURN_STORE_ID)));
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private static void await(CountDownLatch latch, Duration timeout) {
        try {
            if (!latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new AssertionError("Timed out while waiting for the concurrency barrier.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for the concurrency barrier.", exception);
        }
    }
}
