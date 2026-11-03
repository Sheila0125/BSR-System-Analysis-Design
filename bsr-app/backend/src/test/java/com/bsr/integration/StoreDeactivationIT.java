package com.bsr.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bsr.platform.error.ApiException;
import com.bsr.schema.IsolatedTestDatabaseInitializer;
import com.bsr.storetransfer.coordination.api.GuardedStoreDeactivation;
import com.bsr.storetransfer.transfer.application.TransferService;
import com.bsr.storetransfer.transfer.application.TransferTestData;
import com.bsr.storetransfer.transfer.domain.TransferType;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {"spring.profiles.active=test", "spring.jpa.hibernate.ddl-auto=validate"})
@ContextConfiguration(initializers = IsolatedTestDatabaseInitializer.class)
@Transactional
class StoreDeactivationIT {
    @Autowired GuardedStoreDeactivation deactivation;
    @Autowired TransferService transfers;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void fixture() { TransferTestData.insert(jdbc); }

    @Test @DisplayName("AC-UC14/AC-BR38 unblocked authorized store deactivates once with audit")
    void unblockedStoreDeactivatesAndAudits() {
        assertEquals(false, deactivation.deactivate(TransferTestData.manager(), 37001).active());
        assertEquals("INACTIVE", status());
        assertEquals(1, auditCount());
        ApiException repeated = assertThrows(ApiException.class,
                () -> deactivation.deactivate(TransferTestData.manager(), 37001));
        assertEquals("STORE_INACTIVE", repeated.code());
        assertEquals(1, auditCount());
    }

    @Test @DisplayName("AC-BR38 unfinished rental blocks deactivation and terminal contracts do not")
    void rentalBlockerUsesServiceStoreAndNonterminalStatus() {
        jdbc.update("INSERT INTO customer(customer_id,customer_number,full_name,email,phone) VALUES(57012,'C-T09-B','Blocker customer','t09-blocker@test.invalid','+47000057012')");
        rental(57013, "ACTIVE");
        blocked("STORE_HAS_UNFINISHED_RENTAL");
        jdbc.update("UPDATE rental_contract SET contract_status='ENDED' WHERE contract_id=57013");
        assertEquals(false, deactivation.deactivate(TransferTestData.manager(), 37001).active());
    }

    @Test @DisplayName("AC-BR38 unfinished transfer at either store blocks deactivation")
    void transferBlockerUsesBothStores() {
        transfers.initiate(TransferTestData.manager(), 37001, 37002,
                TransferType.TEMPORARY, List.of(37009L));
        blocked("STORE_HAS_ACTIVE_TRANSFER");
        ApiException destination = assertThrows(ApiException.class,
                () -> deactivation.deactivate(TransferTestData.manager(), 37002));
        assertEquals("STORE_HAS_ACTIVE_TRANSFER", destination.code());
        assertEquals("ACTIVE", status());
    }

    @Test @DisplayName("AC-BR38 only future active shift blocks deactivation")
    void futureShiftBlocksButCancelledShiftDoesNot() {
        LocalDate future = LocalDate.now(ZoneId.of("Europe/Oslo")).plusDays(1);
        jdbc.update("INSERT INTO employee_shift(shift_id,shift_number,shift_date,store_id,employee_id,start_time,end_time,shift_status,changed_by_employee_id,changed_at) VALUES(57014,'SH-T09-B',?,37001,37005,'08:00:00','16:00:00','ACTIVE',37003,'2026-10-14 08:00:00')", future);
        blocked("STORE_HAS_FUTURE_SHIFT");
        jdbc.update("UPDATE employee_shift SET shift_status='CANCELLED', cancellation_reason='No work' WHERE shift_id=57014");
        assertEquals(false, deactivation.deactivate(TransferTestData.manager(), 37001).active());
    }

    @Test @DisplayName("AC-UC14 only manager with explicit store grant can deactivate")
    void managerScopeIsRequired() {
        ApiException employee = assertThrows(ApiException.class,
                () -> deactivation.deactivate(TransferTestData.sourceEmployee(), 37001));
        assertEquals(403, employee.status().value());
        ApiException otherStore = assertThrows(ApiException.class,
                () -> deactivation.deactivate(TransferTestData.managerAtSourceOnly(), 37002));
        assertEquals(403, otherStore.status().value());
        assertTrue(status().equals("ACTIVE"));
        assertEquals(0, auditCount());
    }

    private void blocked(String code) {
        ApiException failure = assertThrows(ApiException.class,
                () -> deactivation.deactivate(TransferTestData.manager(), 37001));
        assertEquals(code, failure.code());
        assertEquals("ACTIVE", status());
        assertEquals(0, auditCount());
    }

    private void rental(long id, String state) {
        jdbc.update("INSERT INTO rental_contract(contract_id,contract_number,customer_id,created_by_employee_id,service_store_id,contract_date,rental_start_date,rental_days,amount_due,contract_status) VALUES(?,'RC-T09-B',57012,37003,37001,'2026-10-14','2026-10-20',3,0.00,?)", id, state);
    }

    private String status() {
        return jdbc.queryForObject("SELECT business_status FROM store WHERE store_id=37001", String.class);
    }

    private int auditCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE object_type='STORE' AND object_id=37001 AND action_code='STORE_DEACTIVATED'", Integer.class);
    }
}
