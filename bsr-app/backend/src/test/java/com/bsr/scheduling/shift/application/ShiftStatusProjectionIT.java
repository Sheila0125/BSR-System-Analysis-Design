package com.bsr.scheduling.shift.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bsr.schema.IsolatedTestDatabaseInitializer;
import com.bsr.scheduling.shift.api.ShiftStatusProjection;
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
class ShiftStatusProjectionIT {
    @Autowired ShiftStatusProjection statuses;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("AC-UC16 shift status counts include cancelled history only at its own store")
    void shiftStatusCountsAreScoped() {
        jdbc.update("INSERT INTO store(store_id,store_number,store_name,business_status,opening_time,closing_time) VALUES "
                + "(59401,'S-OP-S1','Shift one','ACTIVE','08:00:00','22:00:00'),"
                + "(59402,'S-OP-S2','Shift two','ACTIVE','08:00:00','22:00:00')");
        jdbc.update("INSERT INTO employee(employee_id,employee_number,full_name,email,business_status) VALUES(59403,'E-OP-S','Shift employee','shift-op@test.invalid','ACTIVE')");
        shift(59411, 59401, "2026-10-20", "ACTIVE");
        shift(59412, 59401, "2026-10-21", "CANCELLED");
        shift(59413, 59402, "2026-10-22", "ACTIVE");

        assertEquals(Map.of("ACTIVE", 1L, "CANCELLED", 1L), statuses.countsAtStore(59401));
        assertEquals(Map.of("ACTIVE", 1L), statuses.countsAtStore(59402));
        assertEquals(Map.of(), statuses.countsAtStore(59404));
    }

    private void shift(long id, long storeId, String date, String status) {
        jdbc.update("INSERT INTO employee_shift(shift_id,shift_number,shift_date,store_id,employee_id,start_time,end_time,shift_status,changed_by_employee_id,changed_at,cancellation_reason) VALUES(?,?,?, ?,59403,'08:00:00','16:00:00',?,59403,'2026-10-14 09:00:00',?)",
                id, "SH-OP-" + id, date, storeId, status,
                status.equals("CANCELLED") ? "Cancelled for test" : null);
    }
}
