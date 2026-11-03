package com.bsr.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bsr.rental.application.RentalService;
import com.bsr.platform.error.ApiException;
import com.bsr.schema.IsolatedTestDatabaseInitializer;
import com.bsr.scheduling.shift.application.ShiftService;
import com.bsr.storetransfer.coordination.api.GuardedStoreDeactivation;
import com.bsr.storetransfer.store.api.StoreAccessException;
import com.bsr.storetransfer.transfer.application.TransferService;
import com.bsr.storetransfer.transfer.application.TransferTestData;
import com.bsr.storetransfer.transfer.domain.TransferType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
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
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {"spring.profiles.active=test", "spring.jpa.hibernate.ddl-auto=validate"})
@ContextConfiguration(initializers = IsolatedTestDatabaseInitializer.class)
class StoreDeactivationConcurrencyIT {
    private enum Creation { RENTAL, TRANSFER, SHIFT }

    @Autowired GuardedStoreDeactivation deactivation;
    @Autowired RentalService rentals;
    @Autowired TransferService transfers;
    @Autowired ShiftService shifts;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void fixture() {
        cleanup();
        TransferTestData.insert(jdbc);
    }

    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM employee_shift WHERE store_id=37001 AND shift_number LIKE 'SH-%'");
        jdbc.update("DELETE FROM rental_charge WHERE contract_id IN (SELECT contract_id FROM rental_contract WHERE source_request_id=57020)");
        jdbc.update("DELETE FROM rental_item WHERE contract_id IN (SELECT contract_id FROM rental_contract WHERE source_request_id=57020)");
        jdbc.update("DELETE FROM rental_contract WHERE source_request_id=57020");
        jdbc.update("DELETE FROM rental_request_item WHERE request_id=57020");
        jdbc.update("DELETE FROM rental_request WHERE request_id=57020");
        jdbc.update("DELETE FROM customer WHERE customer_id=57012");
        TransferTestData.cleanup(jdbc);
    }

    @Test @DisplayName("AC-BR38 two connections: stop first rejects new rental")
    void stopFirstRejectsRental() throws Exception { rentalRequest(); race(Creation.RENTAL, true); }

    @Test @DisplayName("AC-BR38 two connections: new rental first blocks stop")
    void rentalFirstBlocksStop() throws Exception { rentalRequest(); race(Creation.RENTAL, false); }

    @Test @DisplayName("AC-BR38 two connections: stop first rejects new transfer")
    void stopFirstRejectsTransfer() throws Exception { race(Creation.TRANSFER, true); }

    @Test @DisplayName("AC-BR38 two connections: new transfer first blocks stop")
    void transferFirstBlocksStop() throws Exception { race(Creation.TRANSFER, false); }

    @Test @DisplayName("AC-BR38 two connections: stop first rejects new future shift")
    void stopFirstRejectsShift() throws Exception { race(Creation.SHIFT, true); }

    @Test @DisplayName("AC-BR38 two connections: new future shift first blocks stop")
    void shiftFirstBlocksStop() throws Exception { race(Creation.SHIFT, false); }

    private void race(Creation creation, boolean stopFirst) throws Exception {
        CountDownLatch firstFinishedWork = new CountDownLatch(1);
        CountDownLatch releaseFirstCommit = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicLong firstConnection = new AtomicLong();
        AtomicLong secondConnection = new AtomicLong();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> inTransaction(() -> {
                firstConnection.set(jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class));
                if (stopFirst) stop(); else create(creation);
                firstFinishedWork.countDown();
                await(releaseFirstCommit);
            }));
            assertTrue(firstFinishedWork.await(10, TimeUnit.SECONDS), "first transaction did not reach barrier");
            Future<Boolean> second = executor.submit(() -> {
                try {
                    inTransaction(() -> {
                        secondConnection.set(jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class));
                        secondStarted.countDown();
                        if (stopFirst) create(creation); else stop();
                    });
                    return true;
                } catch (RuntimeException conflict) {
                    if (stopFirst) {
                        assertEquals(StoreAccessException.Code.STORE_INACTIVE,
                                assertInstanceOf(StoreAccessException.class, conflict).code());
                    } else {
                        String expected = switch (creation) {
                            case RENTAL -> "STORE_HAS_UNFINISHED_RENTAL";
                            case TRANSFER -> "STORE_HAS_ACTIVE_TRANSFER";
                            case SHIFT -> "STORE_HAS_FUTURE_SHIFT";
                        };
                        assertEquals(expected, assertInstanceOf(ApiException.class, conflict).code());
                    }
                    return false;
                }
            });
            assertTrue(secondStarted.await(10, TimeUnit.SECONDS), "second connection did not reach barrier");
            releaseFirstCommit.countDown();
            first.get(20, TimeUnit.SECONDS);
            assertFalse(second.get(20, TimeUnit.SECONDS), "competing operation must reject after first commit");
            assertNotEquals(firstConnection.get(), secondConnection.get());
            assertEquals(stopFirst ? "INACTIVE" : "ACTIVE", storeStatus());
            assertEquals(stopFirst ? 0 : 1, createdCount(creation));
            assertFalse(storeStatus().equals("INACTIVE") && createdCount(creation) != 0);
        } finally {
            releaseFirstCommit.countDown();
            executor.shutdownNow();
        }
    }

    private void inTransaction(Runnable work) {
        TransactionTemplate transaction = new TransactionTemplate(transactions);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.executeWithoutResult(status -> work.run());
    }

    private void stop() { deactivation.deactivate(TransferTestData.manager(), 37001); }

    private void create(Creation creation) {
        switch (creation) {
            case RENTAL -> rentals.create(TransferTestData.sourceEmployee(), 37001, 57020,
                    List.of(new RentalService.Selection(37009, new BigDecimal("100.00"))));
            case TRANSFER -> transfers.initiate(TransferTestData.manager(), 37001, 37002,
                    TransferType.TEMPORARY, List.of(37009L));
            case SHIFT -> shifts.create(TransferTestData.manager(),
                    LocalDate.now(ZoneId.of("Europe/Oslo")).plusDays(1), 37001, 37005,
                    LocalTime.of(8, 0), LocalTime.of(16, 0));
        }
    }

    private void rentalRequest() {
        jdbc.update("INSERT INTO customer(customer_id,customer_number,full_name,email,phone) VALUES(57012,'C-T09-RACE','Race customer','race-customer@test.invalid','+47000057012')");
        jdbc.update("INSERT INTO rental_request(request_id,request_number,customer_id,service_store_id,accepted_by_employee_id,rental_start_date,rental_days,skill_level,submitted_at,request_status) VALUES(57020,'RR-T09-RACE',57012,37001,37005,?,2,'INTERMEDIATE','2026-10-14 09:00:00','ACCEPTED')",
                LocalDate.now(ZoneId.of("Europe/Oslo")).plusDays(2));
        jdbc.update("INSERT INTO rental_request_item(request_item_id,request_id,equipment_category,quantity) VALUES(57021,57020,'SKI',1)");
    }

    private String storeStatus() {
        return jdbc.queryForObject("SELECT business_status FROM store WHERE store_id=37001", String.class);
    }

    private int createdCount(Creation creation) {
        return switch (creation) {
            case RENTAL -> jdbc.queryForObject("SELECT COUNT(*) FROM rental_contract WHERE source_request_id=57020", Integer.class);
            case TRANSFER -> jdbc.queryForObject("SELECT COUNT(*) FROM equipment_transfer WHERE source_store_id=37001 AND destination_store_id=37002", Integer.class);
            case SHIFT -> jdbc.queryForObject("SELECT COUNT(*) FROM employee_shift WHERE store_id=37001", Integer.class);
        };
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("commit barrier timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
