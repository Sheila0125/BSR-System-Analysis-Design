package com.bsr.storetransfer.transfer.application;

import static com.bsr.storetransfer.transfer.application.TransferTestData.DESTINATION;
import static com.bsr.storetransfer.transfer.application.TransferTestData.EQUIPMENT_A;
import static com.bsr.storetransfer.transfer.application.TransferTestData.EQUIPMENT_B;
import static com.bsr.storetransfer.transfer.application.TransferTestData.EQUIPMENT_C;
import static com.bsr.storetransfer.transfer.application.TransferTestData.SOURCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bsr.equipment.api.EquipmentOperationException;
import com.bsr.equipment.api.EquipmentOperations;
import com.bsr.platform.error.ApiException;
import com.bsr.schema.IsolatedTestDatabaseInitializer;
import com.bsr.storetransfer.transfer.domain.TransferItem;
import com.bsr.storetransfer.transfer.domain.TransferStatus;
import com.bsr.storetransfer.transfer.domain.TransferType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
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
class TransferHandoffIT {
    @Autowired private TransferService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EquipmentOperations equipment;
    @Autowired private PlatformTransactionManager transactions;

    @BeforeEach
    void setup() {
        TransferTestData.cleanup(jdbc);
        TransferTestData.insert(jdbc);
    }

    @AfterEach
    void cleanup() {
        TransferTestData.cleanup(jdbc);
    }

    @Test
    @DisplayName("AC-BR28/AC-BR29 denies unapproved, wrong-store and manager-only handoff")
    void unapprovedWrongStoreAndManagerCannotHandoff() {
        var transfer = initiate(TransferType.TEMPORARY, List.of(EQUIPMENT_A));
        long itemId = itemId(transfer, EQUIPMENT_A);
        assertThrows(ApiException.class,
                () -> service.dispatch(TransferTestData.sourceEmployee(), transfer.id(), itemId));
        service.approve(TransferTestData.manager(), transfer.id());
        assertThrows(ApiException.class,
                () -> service.dispatch(TransferTestData.manager(), transfer.id(), itemId));
        assertThrows(ApiException.class,
                () -> service.dispatch(TransferTestData.destinationEmployee(), transfer.id(), itemId));
        assertThrows(ApiException.class,
                () -> service.receive(TransferTestData.sourceEmployee(), transfer.id(), itemId));
        assertEquals("AVAILABLE", status(EQUIPMENT_A));
        assertEquals("PENDING", dispatchResult(itemId));
        assertEquals(0, auditCount("TRANSFER_ITEM_DISPATCHED"));
    }

    @Test
    @DisplayName("AC-BR30/AC-BR31 keeps three-item partial, exception and retry facts")
    void threeItemsAllowPartialReceiptExceptionRetryAndIdempotentCompletion() {
        var transfer = initiate(TransferType.TEMPORARY, List.of(EQUIPMENT_A, EQUIPMENT_B, EQUIPMENT_C));
        service.approve(TransferTestData.manager(), transfer.id());
        long a = itemId(transfer, EQUIPMENT_A);
        long b = itemId(transfer, EQUIPMENT_B);
        long c = itemId(transfer, EQUIPMENT_C);
        service.dispatch(TransferTestData.sourceEmployee(), transfer.id(), a);
        service.dispatch(TransferTestData.sourceEmployee(), transfer.id(), b);
        assertEquals(TransferStatus.IN_TRANSIT, service.detail(TransferTestData.manager(), transfer.id()).aggregate().status());
        service.receive(TransferTestData.destinationEmployee(), transfer.id(), a);
        assertEquals(TransferStatus.PARTIALLY_RECEIVED, service.detail(TransferTestData.manager(), transfer.id()).aggregate().status());
        service.reportException(TransferTestData.destinationEmployee(), transfer.id(), b,
                TransferItem.Phase.RECEIPT, "Seal damaged");
        assertEquals("IN_TRANSFER", status(EQUIPMENT_B));
        assertEquals("EXCEPTION", receiptResult(b));
        assertEquals(TransferStatus.PARTIALLY_RECEIVED, service.detail(TransferTestData.manager(), transfer.id()).aggregate().status());
        service.receive(TransferTestData.destinationEmployee(), transfer.id(), b);
        assertEquals("Seal damaged", jdbc.queryForObject(
                "SELECT exception_note FROM equipment_transfer_item WHERE transfer_item_id=?", String.class, b));
        service.reportException(TransferTestData.sourceEmployee(), transfer.id(), c,
                TransferItem.Phase.DISPATCH, "Tag missing");
        assertEquals("AVAILABLE", status(EQUIPMENT_C));
        service.dispatch(TransferTestData.sourceEmployee(), transfer.id(), c);
        service.receive(TransferTestData.destinationEmployee(), transfer.id(), c);
        assertEquals(TransferStatus.COMPLETED, service.detail(TransferTestData.manager(), transfer.id()).aggregate().status());
        assertEquals(0, service.activeBlockers(SOURCE).size());

        LocalDateTime receivedAt = jdbc.queryForObject(
                "SELECT received_at FROM equipment_transfer_item WHERE transfer_item_id=?",
                LocalDateTime.class, c);
        int auditBefore = auditCount("TRANSFER_ITEM_RECEIVED");
        service.receive(TransferTestData.destinationEmployee(), transfer.id(), c);
        assertEquals(receivedAt, jdbc.queryForObject(
                "SELECT received_at FROM equipment_transfer_item WHERE transfer_item_id=?",
                LocalDateTime.class, c));
        assertEquals(auditBefore, auditCount("TRANSFER_ITEM_RECEIVED"));
        assertEquals(SOURCE, homeStore(EQUIPMENT_C));
        assertEquals(DESTINATION, currentStore(EQUIPMENT_C));
    }

    @Test
    @DisplayName("AC-BR31 permanent receipt moves home while temporary receipt does not")
    void permanentReceiptMovesHomeAndTemporaryDoesNot() {
        var permanent = initiate(TransferType.PERMANENT, List.of(EQUIPMENT_A));
        service.approve(TransferTestData.manager(), permanent.id());
        long itemId = itemId(permanent, EQUIPMENT_A);
        service.dispatch(TransferTestData.sourceEmployee(), permanent.id(), itemId);
        service.receive(TransferTestData.destinationEmployee(), permanent.id(), itemId);
        assertEquals(DESTINATION, homeStore(EQUIPMENT_A));
        assertEquals(DESTINATION, currentStore(EQUIPMENT_A));
    }

    @Test
    @DisplayName("AC-BR29 stale equipment state cannot create a false dispatch fact")
    void equipmentChangedAfterProposalCannotProduceFalseDispatchFact() {
        var transfer = initiate(TransferType.TEMPORARY, List.of(EQUIPMENT_A));
        service.approve(TransferTestData.manager(), transfer.id());
        long itemId = itemId(transfer, EQUIPMENT_A);
        jdbc.update("UPDATE equipment SET equipment_status='RENTED' WHERE equipment_id=?", EQUIPMENT_A);
        assertThrows(EquipmentOperationException.class,
                () -> service.dispatch(TransferTestData.sourceEmployee(), transfer.id(), itemId));
        assertEquals("PENDING", dispatchResult(itemId));
        assertEquals(0, auditCount("TRANSFER_ITEM_DISPATCHED"));
    }

    @Test
    @DisplayName("AC-BR30/AC-BR31 audit failure rolls back handoff facts and equipment")
    void auditFailureRollsBackPhysicalHandoffAndTransferFact() {
        var transfer = initiate(TransferType.TEMPORARY, List.of(EQUIPMENT_A));
        service.approve(TransferTestData.manager(), transfer.id());
        long itemId = itemId(transfer, EQUIPMENT_A);
        var invalidAccount = new com.bsr.identity.api.ActorContext(37999L, "missing-account",
                TransferTestData.sourceEmployee().identityType(), TransferTestData.SOURCE_EMPLOYEE,
                TransferTestData.sourceEmployee().activeGrants());
        assertThrows(RuntimeException.class,
                () -> service.dispatch(invalidAccount, transfer.id(), itemId));
        assertEquals("AVAILABLE", status(EQUIPMENT_A));
        assertEquals("PENDING", dispatchResult(itemId));
    }

    @Test
    @DisplayName("AC-BR29 rental reservation and transfer dispatch have one valid winner")
    void twoConnectionsRaceRentalReservationAgainstTransferDispatch() throws Exception {
        var transfer = initiate(TransferType.TEMPORARY, List.of(EQUIPMENT_A));
        service.approve(TransferTestData.manager(), transfer.id());
        long itemId = itemId(transfer, EQUIPMENT_A);
        jdbc.update("INSERT INTO customer(customer_id,customer_number,full_name,email,phone,height_estimated,weight_estimated,boot_size_estimated) VALUES(37012,'C-T07-01','T07 Customer','t07-customer@test.invalid','+4700037012',FALSE,FALSE,FALSE)");
        jdbc.update("INSERT INTO rental_contract(contract_id,contract_number,customer_id,created_by_employee_id,service_store_id,contract_date,rental_start_date,rental_days,amount_due,contract_status) VALUES(37013,'RC-T07-RACE',37012,37005,37001,'2026-10-13','2026-10-14',2,200.00,'DRAFT')");
        jdbc.update("INSERT INTO rental_item(rental_item_id,contract_id,equipment_id,daily_rate_snapshot,item_status) VALUES(37014,37013,37009,100.00,'PENDING_ISSUE')");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicLong rentalConnection = new AtomicLong();
        AtomicLong transferConnection = new AtomicLong();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> rental = executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("race start timed out");
                try {
                    return new TransactionTemplate(transactions).execute(tx -> {
                        rentalConnection.set(jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class));
                        equipment.reserveForRental(TransferTestData.sourceEmployee(), 37013L,
                                SOURCE, List.of(EQUIPMENT_A));
                        return true;
                    });
                } catch (EquipmentOperationException conflict) {
                    assertEquals(EquipmentOperationException.Code.EQUIPMENT_STATE_CONFLICT, conflict.code());
                    return false;
                }
            });
            Future<Boolean> dispatch = executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("race start timed out");
                try {
                    return new TransactionTemplate(transactions).execute(tx -> {
                        transferConnection.set(jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class));
                        service.dispatch(TransferTestData.sourceEmployee(), transfer.id(), itemId);
                        return true;
                    });
                } catch (EquipmentOperationException conflict) {
                    assertEquals(EquipmentOperationException.Code.EQUIPMENT_STATE_CONFLICT, conflict.code());
                    return false;
                }
            });
            if (!ready.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("race workers not ready");
            start.countDown();
            boolean rentalWon = rental.get(15, TimeUnit.SECONDS);
            boolean transferWon = dispatch.get(15, TimeUnit.SECONDS);
            assertNotEquals(rentalConnection.get(), transferConnection.get());
            assertNotEquals(rentalWon, transferWon);
            assertEquals(transferWon ? "DISPATCHED" : "PENDING", dispatchResult(itemId));
            assertEquals(transferWon ? "IN_TRANSFER" : "PENDING_ISSUE", status(EQUIPMENT_A));
            assertEquals(transferWon ? 1 : 0, auditCount("TRANSFER_ITEM_DISPATCHED"));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("AC-BR31 concurrent duplicate receipt preserves one original fact and audit")
    void concurrentDuplicateReceiptsKeepOneOriginalFactAndAudit() throws Exception {
        var transfer = initiate(TransferType.TEMPORARY, List.of(EQUIPMENT_A));
        service.approve(TransferTestData.manager(), transfer.id());
        long itemId = itemId(transfer, EQUIPMENT_A);
        service.dispatch(TransferTestData.sourceEmployee(), transfer.id(), itemId);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var receipt = (java.util.concurrent.Callable<TransferViews.TransferRecord>) () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("receipt race timed out");
                return service.receive(TransferTestData.destinationEmployee(), transfer.id(), itemId);
            };
            Future<TransferViews.TransferRecord> first = executor.submit(receipt);
            Future<TransferViews.TransferRecord> second = executor.submit(receipt);
            if (!ready.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("receipt workers not ready");
            start.countDown();
            assertEquals(TransferStatus.COMPLETED, first.get(15, TimeUnit.SECONDS).aggregate().status());
            assertEquals(TransferStatus.COMPLETED, second.get(15, TimeUnit.SECONDS).aggregate().status());
            assertEquals(1, auditCount("TRANSFER_ITEM_RECEIVED"));
            assertEquals("RECEIVED", receiptResult(itemId));
            assertEquals(DESTINATION, currentStore(EQUIPMENT_A));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private TransferViews.TransferRecord initiate(TransferType type, List<Long> equipmentIds) {
        return service.initiate(TransferTestData.manager(), SOURCE, DESTINATION, type, equipmentIds);
    }

    private long itemId(TransferViews.TransferRecord record, long equipmentId) {
        return record.itemIds().get(equipmentId);
    }

    private String status(long equipmentId) {
        return jdbc.queryForObject("SELECT equipment_status FROM equipment WHERE equipment_id=?", String.class, equipmentId);
    }

    private String dispatchResult(long itemId) {
        return jdbc.queryForObject("SELECT dispatch_result FROM equipment_transfer_item WHERE transfer_item_id=?", String.class, itemId);
    }

    private String receiptResult(long itemId) {
        return jdbc.queryForObject("SELECT receipt_result FROM equipment_transfer_item WHERE transfer_item_id=?", String.class, itemId);
    }

    private long homeStore(long equipmentId) {
        return jdbc.queryForObject("SELECT home_store_id FROM equipment WHERE equipment_id=?", Long.class, equipmentId);
    }

    private long currentStore(long equipmentId) {
        return jdbc.queryForObject("SELECT current_store_id FROM equipment WHERE equipment_id=?", Long.class, equipmentId);
    }

    private int auditCount(String action) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action_code=? AND object_type='EQUIPMENT_TRANSFER'", Integer.class, action);
    }
}
