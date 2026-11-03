package com.bsr.storetransfer.transfer.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bsr.schema.IsolatedTestDatabaseInitializer;
import com.bsr.storetransfer.transfer.domain.TransferAggregate;
import com.bsr.storetransfer.transfer.domain.TransferStatus;
import com.bsr.storetransfer.transfer.domain.TransferType;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
    "spring.profiles.active=test",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@ContextConfiguration(initializers = IsolatedTestDatabaseInitializer.class)
@Transactional
class JpaTransferPersistenceIT {
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 13, 3, 0);

    @Autowired private TransferPersistence persistence;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("AC-BR29/AC-BR30/AC-BR31 persists item facts and active store blockers")
    void createLockAndSavePreservePerItemFactsAndActiveBlockers() {
        fixture();
        TransferAggregate proposed = TransferAggregate.initiate("TR-T07-PERSIST", TransferType.TEMPORARY,
                29001L, 29002L, 29003L, START, List.of(29004L, 29005L));
        long id = persistence.create(proposed);
        assertEquals(new TransferViews.StorePair(29001L, 29002L), persistence.stores(id).orElseThrow());
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM equipment_transfer_item WHERE transfer_id=?", Integer.class, id));

        TransferViews.TransferRecord record = persistence.lock(id).orElseThrow();
        assertEquals(TransferStatus.INITIATED, record.aggregate().status());
        assertEquals(2, record.itemIds().size());
        assertEquals(2, persistence.activeBlockers(29001L).size());
        assertEquals(2, persistence.activeBlockers(29002L).size());

        record.aggregate().approve(29003L, START.plusMinutes(1));
        record.aggregate().dispatch(29004L, 29003L, START.plusMinutes(2));
        record.aggregate().reportException(29005L,
                com.bsr.storetransfer.transfer.domain.TransferItem.Phase.DISPATCH,
                "Not found", 29003L, START.plusMinutes(3));
        persistence.save(record);
        TransferViews.TransferRecord reread = persistence.find(id).orElseThrow();
        assertEquals(TransferStatus.IN_TRANSIT, reread.aggregate().status());
        assertEquals("EXCEPTION", jdbc.queryForObject(
                "SELECT dispatch_result FROM equipment_transfer_item WHERE transfer_id=? AND equipment_id=29005",
                String.class, id));
        assertEquals(29003L, jdbc.queryForObject(
                "SELECT dispatched_by_employee_id FROM equipment_transfer_item WHERE transfer_id=? AND equipment_id=29005",
                Long.class, id));

        reread.aggregate().dispatch(29005L, 29003L, START.plusMinutes(4));
        reread.aggregate().receive(29004L, 29003L, START.plusMinutes(5));
        reread.aggregate().receive(29005L, 29003L, START.plusMinutes(6));
        persistence.save(reread);
        assertEquals(TransferStatus.COMPLETED, persistence.find(id).orElseThrow().aggregate().status());
        assertTrue(persistence.activeBlockers(29001L).isEmpty());
        assertFalse(persistence.listByStore(29001L).isEmpty());
    }

    private void fixture() {
        jdbc.update("INSERT INTO store(store_id,store_number,store_name,business_status,opening_time,closing_time) VALUES(29001,'S-T07-P1','T07 Source','ACTIVE','08:00:00','20:00:00')");
        jdbc.update("INSERT INTO store(store_id,store_number,store_name,business_status,opening_time,closing_time) VALUES(29002,'S-T07-P2','T07 Target','ACTIVE','08:00:00','20:00:00')");
        jdbc.update("INSERT INTO employee(employee_id,employee_number,full_name,email,business_status) VALUES(29003,'E-T07-P1','T07 Tester','t07-persistence@test.invalid','ACTIVE')");
        jdbc.update("INSERT INTO equipment(equipment_id,inventory_number,equipment_name,equipment_category,skill_level,home_store_id,current_store_id,equipment_status,purchase_date,purchase_price,current_daily_rate) VALUES(29004,'INV-T07-P1','Ski A','SKI','INTERMEDIATE',29001,29001,'AVAILABLE','2025-01-01',1000.00,100.00)");
        jdbc.update("INSERT INTO equipment(equipment_id,inventory_number,equipment_name,equipment_category,skill_level,home_store_id,current_store_id,equipment_status,purchase_date,purchase_price,current_daily_rate) VALUES(29005,'INV-T07-P2','Ski B','SKI','INTERMEDIATE',29001,29001,'AVAILABLE','2025-01-01',1000.00,100.00)");
    }
}
