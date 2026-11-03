package com.bsr.storetransfer.transfer.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bsr.schema.IsolatedTestDatabaseInitializer;
import com.bsr.storetransfer.transfer.api.TransferStatusProjection;
import java.util.Map;
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
class TransferStatusProjectionIT {
    @Autowired TransferStatusProjection statuses;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("AC-UC16 transfer states include either endpoint once and include completed history")
    void endpointStoreStatusCountsDoNotDoubleCount() {
        jdbc.update("INSERT INTO store(store_id,store_number,store_name,business_status,opening_time,closing_time) VALUES "
                + "(59301,'S-OP-T1','Transfer one','ACTIVE','08:00:00','22:00:00'),"
                + "(59302,'S-OP-T2','Transfer two','ACTIVE','08:00:00','22:00:00'),"
                + "(59303,'S-OP-T3','Transfer three','ACTIVE','08:00:00','22:00:00')");
        jdbc.update("INSERT INTO employee(employee_id,employee_number,full_name,email,business_status) VALUES(59304,'E-OP-T','Transfer employee','transfer-op@test.invalid','ACTIVE')");
        transfer(59311, 59301, 59302, "INITIATED");
        transfer(59312, 59302, 59301, "COMPLETED");
        transfer(59313, 59302, 59303, "INITIATED");

        assertEquals(Map.of("INITIATED", 1L, "COMPLETED", 1L), statuses.countsAtStore(59301));
        assertEquals(Map.of("INITIATED", 2L, "COMPLETED", 1L), statuses.countsAtStore(59302));
        assertEquals(Map.of("INITIATED", 1L), statuses.countsAtStore(59303));
        assertEquals(Map.of(), statuses.countsAtStore(59305));
    }

    private void transfer(long id, long source, long destination, String status) {
        jdbc.update("INSERT INTO equipment_transfer(transfer_id,transfer_number,transfer_type,source_store_id,destination_store_id,initiated_by_employee_id,initiated_at,completed_at,transfer_status) VALUES(?,?,'PERMANENT',?,?,59304,'2026-10-14 09:00:00',?,?)",
                id, "TR-OP-" + id, source, destination,
                status.equals("COMPLETED") ? "2026-10-14 12:00:00" : null, status);
    }
}
