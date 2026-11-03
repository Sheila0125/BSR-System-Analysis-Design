package com.bsr.storetransfer.store.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bsr.platform.error.ApiException;
import com.bsr.schema.IsolatedTestDatabaseInitializer;
import com.bsr.storetransfer.coordination.api.GuardedStoreDeactivation;
import com.bsr.storetransfer.store.api.StoreSnapshot;
import java.time.LocalTime;
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
class StoreAdministrationIT {
    @Autowired private StoreAdministrationService service;
    @Autowired private GuardedStoreDeactivation deactivation;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        com.bsr.storetransfer.transfer.application.TransferTestData.cleanup(jdbc);
        com.bsr.storetransfer.transfer.application.TransferTestData.insert(jdbc);
    }

    @AfterEach
    void cleanup() {
        com.bsr.storetransfer.transfer.application.TransferTestData.cleanup(jdbc);
    }

    @Test
    @DisplayName("AC-UC14 manager creates active store and updates only base details")
    void managerCreatesActiveStoreWithoutGrantAndUpdatesOnlyDetails() {
        StoreSnapshot created = service.create(
                com.bsr.storetransfer.transfer.application.TransferTestData.manager(),
                37001L, "S-T07-NEW", "T07 New Store", "Hill Road",
                LocalTime.of(9, 0), LocalTime.of(21, 0));
        assertTrue(created.active());
        assertEquals("S-T07-NEW", created.storeNumber());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM role_grant WHERE store_id=?",
                Integer.class, created.storeId()));
        assertEquals("T07 New Store", jdbc.queryForObject(
                "SELECT store_name FROM store WHERE store_id=?", String.class, created.storeId()));

        StoreSnapshot updated = service.update(
                com.bsr.storetransfer.transfer.application.TransferTestData.manager(),
                37001L, "Renamed Source", "New Address",
                LocalTime.of(8, 30), LocalTime.of(20, 30));
        assertEquals("S-T07-01", updated.storeNumber());
        assertEquals("Renamed Source", service.get(
                com.bsr.storetransfer.transfer.application.TransferTestData.manager(), 37001L).storeName());
        assertTrue(updated.active());
        assertEquals("ACTIVE", jdbc.queryForObject(
                "SELECT business_status FROM store WHERE store_id=37001", String.class));
    }

    @Test
    @DisplayName("AC-UC14 rejects invalid hours and actors outside explicit store scope")
    void invalidHoursAndOutOfScopeActorCannotWrite() {
        ApiException duplicate = assertThrows(ApiException.class, () -> service.create(
                com.bsr.storetransfer.transfer.application.TransferTestData.manager(),
                37001L, " S-T07-01 ", "Duplicate", null,
                LocalTime.of(8, 0), LocalTime.of(20, 0)));
        assertEquals("STORE_NUMBER_EXISTS", duplicate.code());
        assertThrows(ApiException.class, () -> service.update(
                com.bsr.storetransfer.transfer.application.TransferTestData.manager(),
                37001L, "Invalid", null, LocalTime.of(22, 0), LocalTime.of(8, 0)));
        assertThrows(ApiException.class, () -> service.update(
                com.bsr.storetransfer.transfer.application.TransferTestData.managerAtSourceOnly(),
                37002L, "No Access", null, LocalTime.of(8, 0), LocalTime.of(20, 0)));
        assertThrows(ApiException.class, () -> service.create(
                com.bsr.storetransfer.transfer.application.TransferTestData.sourceEmployee(),
                37001L, "S-T07-NEW", "No Access", null,
                LocalTime.of(8, 0), LocalTime.of(20, 0)));
        assertEquals("T07 Source", jdbc.queryForObject(
                "SELECT store_name FROM store WHERE store_id=37001", String.class));
        assertEquals("T07 Destination", jdbc.queryForObject(
                "SELECT store_name FROM store WHERE store_id=37002", String.class));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM store WHERE store_number='S-T07-NEW'", Integer.class));
    }

    @Test
    @DisplayName("AC-UC14 an unblocked store stops through the T09 guard and retains identity")
    void unblockedStoreStopsThroughGuard() {
        StoreSnapshot stopped = deactivation.deactivate(
                com.bsr.storetransfer.transfer.application.TransferTestData.manager(), 37001L);
        assertFalse(stopped.active());
        assertEquals("INACTIVE", jdbc.queryForObject(
                "SELECT business_status FROM store WHERE store_id=37001", String.class));
        assertFalse(service.get(com.bsr.storetransfer.transfer.application.TransferTestData.manager(),
                37001L).storeName().isBlank());
    }
}
