package com.bsr.scheduling.shift.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bsr.schema.IsolatedTestDatabaseInitializer;
import com.bsr.scheduling.shift.api.FutureShiftProjection;
import com.bsr.scheduling.shift.domain.Shift;
import com.bsr.scheduling.shift.domain.ShiftStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
class JpaShiftPersistenceIT {
    private static final LocalDate DATE = LocalDate.of(2026, 10, 20);
    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 13, 9, 0);

    @Autowired private ShiftPersistence persistence;
    @Autowired private FutureShiftProjection blockers;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("AC-BR35 active same-day lookup excludes self and cancelled history may coexist")
    void activeLookupExcludesSelfAndCancelledHistory() {
        fixture();
        long first = persistence.create(shift("SH-T08-P1", DATE));
        entityManager.clear();
        assertEquals(1, persistence.activeOnDate(49003L, DATE, null).size());
        assertTrue(persistence.activeOnDate(49003L, DATE, first).isEmpty());

        Shift locked = persistence.lock(first).orElseThrow();
        assertEquals(ShiftStatus.ACTIVE, locked.status());
        assertEquals(LocalTime.of(8, 0), locked.start());
        assertEquals(LocalTime.of(16, 0), locked.end());
        persistence.save(locked.cancel("Reschedule", 49003L, AT.plusHours(1), DATE.minusDays(1)));
        assertTrue(persistence.activeOnDate(49003L, DATE, null).isEmpty());

        long second = persistence.create(shift("SH-T08-P2", DATE));
        assertEquals(1, persistence.activeOnDate(49003L, DATE, null).size());
        assertEquals(second, persistence.activeOnDate(49003L, DATE, null).getFirst().id());
        assertEquals(2, persistence.listByEmployee(49003L).size());
        assertEquals(2, persistence.listByStore(49001L).size());
        assertEquals("Reschedule", persistence.find(first).orElseThrow().cancellationReason());
    }

    @Test
    @DisplayName("AC-BR37 only future active shifts enter T09 store blocker projection")
    void futureProjectionOmitsTodayPastAndCancelled() {
        fixture();
        long past = persistence.create(shift("SH-T08-P3", DATE.minusDays(2)));
        long today = persistence.create(shift("SH-T08-P4", DATE.minusDays(1)));
        long future = persistence.create(shift("SH-T08-P5", DATE));
        assertEquals(1, blockers.futureActiveAtStore(49001L, DATE.minusDays(1)).size());
        assertEquals(future, blockers.futureActiveAtStore(49001L, DATE.minusDays(1)).getFirst().shiftId());

        persistence.save(persistence.lock(future).orElseThrow()
                .cancel("Closed", 49003L, AT, DATE.minusDays(1)));
        assertTrue(blockers.futureActiveAtStore(49001L, DATE.minusDays(1)).isEmpty());
        assertFalse(persistence.find(past).isEmpty());
        assertFalse(persistence.find(today).isEmpty());
    }

    private Shift shift(String number, LocalDate date) {
        return Shift.schedule(number, date, 49001L, 49003L,
                LocalTime.of(8, 0), LocalTime.of(16, 0), 49003L, AT);
    }

    private void fixture() {
        jdbc.update("INSERT INTO store(store_id,store_number,store_name,business_status,opening_time,closing_time) VALUES(49001,'S-T08-P1','T08 Store','ACTIVE','08:00:00','22:00:00')");
        jdbc.update("INSERT INTO employee(employee_id,employee_number,full_name,email,business_status) VALUES(49003,'E-T08-P1','T08 Worker','t08-persistence@test.invalid','ACTIVE')");
    }
}
