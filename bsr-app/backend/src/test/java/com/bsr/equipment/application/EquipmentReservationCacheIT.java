package com.bsr.equipment.application;

import static org.junit.jupiter.api.Assertions.*;
import static com.bsr.equipment.application.EquipmentTestData.*;

import com.bsr.equipment.api.EquipmentOperations;
import com.bsr.identity.api.ActiveRoleGrant;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.IdentityType;
import com.bsr.identity.api.RoleCode;
import com.bsr.schema.IsolatedTestDatabaseInitializer;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
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
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties={"spring.profiles.active=test","spring.jpa.hibernate.ddl-auto=validate"})
@ContextConfiguration(initializers=IsolatedTestDatabaseInitializer.class)
class EquipmentReservationCacheIT {
    @Autowired EquipmentOperations operations;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @BeforeEach void setup() { EquipmentTestData.cleanup(jdbc); EquipmentTestData.insert(jdbc); }
    @AfterEach void cleanup() { EquipmentTestData.cleanup(jdbc); }

    @Test @DisplayName("AC-UC04/BR15 cached AVAILABLE is refreshed after a competing reservation commits")
    void acUc04LockedRowRefreshesPreviouslyCachedAvailability() throws Exception {
        var reserved = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var cached = new CountDownLatch(1);
        long[] connections = new long[2];
        var actor = new ActorContext(ACCOUNT_ID,"equipment-test-employee",IdentityType.EMPLOYEE,
                EMPLOYEE_ID,List.of(new ActiveRoleGrant(RoleCode.EMPLOYEE,SOURCE_STORE_ID)));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> transaction().execute(status -> {
                connections[0]=jdbc.queryForObject("SELECT CONNECTION_ID()",Long.class);
                operations.reserveForRental(actor,CONTRACT_ID,SOURCE_STORE_ID,List.of(CONCURRENT_EQUIPMENT_ID));
                reserved.countDown(); await(release); return true;
            }));
            await(reserved);
            var second = executor.submit(() -> transaction().execute(status -> {
                connections[1]=jdbc.queryForObject("SELECT CONNECTION_ID()",Long.class);
                assertEquals(1,operations.selectAvailable(actor,SOURCE_STORE_ID,null,null).size());
                cached.countDown();
                return operations.reserveForRental(actor,CONTRACT_ID,SOURCE_STORE_ID,List.of(CONCURRENT_EQUIPMENT_ID));
            }));
            try { await(cached); assertThrows(TimeoutException.class,()->second.get(250,TimeUnit.MILLISECONDS)); }
            finally { release.countDown(); }
            assertTrue(first.get(10,TimeUnit.SECONDS));
            assertThrows(ExecutionException.class,()->second.get(10,TimeUnit.SECONDS));
            assertNotEquals(connections[0],connections[1]);
        } finally { release.countDown(); }
        assertEquals("PENDING_ISSUE",jdbc.queryForObject("SELECT equipment_status FROM equipment WHERE equipment_id=?",String.class,CONCURRENT_EQUIPMENT_ID));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE object_type='EQUIPMENT' AND object_id=? AND action_code='EQUIPMENT_RESERVED'",Integer.class,CONCURRENT_EQUIPMENT_ID));
    }
    private TransactionTemplate transaction() {
        var template=new TransactionTemplate(manager);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED); return template;
    }
    private static void await(CountDownLatch latch) {
        try { if(!latch.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Reservation barrier timed out"); }
        catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
}
