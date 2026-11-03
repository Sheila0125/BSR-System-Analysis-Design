package com.bsr.scheduling.shift.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bsr.identity.api.ActiveRoleGrant;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.IdentityType;
import com.bsr.identity.api.RoleCode;
import com.bsr.platform.error.ApiException;
import com.bsr.schema.IsolatedTestDatabaseInitializer;
import com.bsr.scheduling.employee.api.EmployeeAccessException;
import com.bsr.scheduling.shift.domain.ShiftStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(properties = {
    "spring.profiles.active=test",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@ContextConfiguration(initializers = IsolatedTestDatabaseInitializer.class)
class ShiftManagementIT {
    private static final long SOURCE = 48001L;
    private static final long DESTINATION = 48002L;
    private static final long MANAGER = 48003L;
    private static final long WORKER_A = 48005L;
    private static final long WORKER_B = 48007L;
    private static final LocalTime EIGHT = LocalTime.of(8, 0);
    private static final LocalTime TEN = LocalTime.of(10, 0);

    @Autowired private ShiftService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setup() {
        cleanup();
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("fixtures/scheduling/t08-shifts.sql"));
            return null;
        });
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM audit_event WHERE actor_account_id IN (48004,48006,48008)");
        jdbc.update("DELETE FROM employee_shift WHERE employee_id IN (48003,48005,48007)");
        jdbc.update("DELETE FROM role_grant WHERE account_id IN (48004,48006,48008)");
        jdbc.update("DELETE FROM account WHERE account_id IN (48004,48006,48008)");
        jdbc.update("DELETE FROM employee WHERE employee_id IN (48003,48005,48007)");
        jdbc.update("DELETE FROM store WHERE store_id IN (48001,48002)");
    }

    @Test
    @DisplayName("AC-UC12/AC-BR35 same employee same day conflicts even when hours or stores do not overlap")
    void sameDayConflictAndDifferentDaySuccess() {
        LocalDate date = futureDate();
        var firstShift = service.create(manager(), date, SOURCE, WORKER_A, EIGHT, TEN);
        long first = firstShift.id();
        assertEquals(firstShift.changedAt().truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                jdbc.queryForObject("SELECT DATE_FORMAT(changed_at,'%Y-%m-%d %H:%i:%s') "
                        + "FROM employee_shift WHERE shift_id=?", String.class, first));
        long auditGapSeconds = jdbc.queryForObject("SELECT TIMESTAMPDIFF(SECOND,s.changed_at,a.occurred_at) "
                + "FROM employee_shift s JOIN audit_event a ON a.object_type='EMPLOYEE_SHIFT' "
                + "AND a.object_id=s.shift_id AND a.action_code='SHIFT_CREATED' "
                + "WHERE s.shift_id=?", Long.class, first);
        assertTrue(Math.abs(auditGapSeconds) <= 2, "Shift change and success audit must use the same UTC timeline.");
        EmployeeAccessException conflict = assertThrows(EmployeeAccessException.class,
                () -> service.create(manager(), date, DESTINATION, WORKER_A,
                        LocalTime.of(12, 0), LocalTime.of(16, 0)));
        assertEquals(EmployeeAccessException.Code.SAME_DAY_SHIFT_CONFLICT, conflict.code());
        assertEquals(1, shiftCount());
        assertEquals(1, auditCount("SHIFT_CREATED"));
        assertNotEquals(first, service.create(manager(), date.plusDays(1), SOURCE, WORKER_A,
                EIGHT, TEN).id());
        assertEquals(2, shiftCount());
    }

    @Test
    @DisplayName("AC-UC13/AC-BR37 cancellation preserves schedule, and cancelled history permits replacement")
    void cancellationAndReplacement() {
        LocalDate date = futureDate();
        long id = service.create(manager(), date, SOURCE, WORKER_A, EIGHT, TEN).id();
        var cancelled = service.cancel(manager(), id, "Worker unavailable");
        assertEquals(ShiftStatus.CANCELLED, cancelled.status());
        assertEquals(date, cancelled.date());
        assertEquals(EIGHT, cancelled.start());
        assertEquals("Worker unavailable", cancelled.cancellationReason());
        service.create(manager(), date, SOURCE, WORKER_A, LocalTime.of(10, 0), LocalTime.of(12, 0));
        assertEquals(2, shiftCount());
        assertEquals(1, auditCount("SHIFT_CANCELLED"));
        ApiException repeated = assertThrows(ApiException.class,
                () -> service.cancel(manager(), id, "Again"));
        assertEquals("SHIFT_NOT_ACTIVE", repeated.code());
        assertEquals(1, auditCount("SHIFT_CANCELLED"));
    }

    @Test
    @DisplayName("AC-BR36 adjustment excludes self; collision and missing destination scope preserve original")
    void adjustmentConflictAndStoreScope() {
        LocalDate date = futureDate();
        long id = service.create(manager(), date, SOURCE, WORKER_A, EIGHT, TEN).id();
        service.create(manager(), date, SOURCE, WORKER_B, EIGHT, TEN);
        service.adjust(manager(), id, date, SOURCE, WORKER_A, EIGHT, LocalTime.of(11, 0));
        assertThrows(EmployeeAccessException.class, () -> service.adjust(manager(), id,
                date, DESTINATION, WORKER_B, EIGHT, TEN));
        assertEquals(WORKER_A, service.find(manager(), id).employeeId());
        assertEquals(LocalTime.of(11, 0), service.find(manager(), id).end());
        assertThrows(ApiException.class, () -> service.adjust(sourceOnlyManager(), id,
                date.plusDays(1), DESTINATION, WORKER_A, EIGHT, TEN));
        assertEquals(1, auditCount("SHIFT_ADJUSTED"));
    }

    @Test
    @DisplayName("AC-UC13 self-read is own employee only; manager list needs explicit store scope")
    void ownAndStoreScopedReads() {
        LocalDate date = futureDate();
        service.create(manager(), date, SOURCE, WORKER_A, EIGHT, TEN);
        assertEquals(1, service.myShifts(workerA()).size());
        assertEquals(0, service.myShifts(workerB()).size());
        assertEquals(1, service.storeShifts(manager(), SOURCE).size());
        assertThrows(ApiException.class, () -> service.storeShifts(sourceOnlyManager(), DESTINATION));
        assertThrows(ApiException.class, () -> service.myShifts(manager()));
    }

    @Test
    @DisplayName("AC-BR35 audit failure rolls back shift insert")
    void auditFailureRollsBack() {
        ActorContext missingAccount = new ActorContext(48999L, "missing", IdentityType.EMPLOYEE,
                MANAGER, manager().activeGrants());
        assertThrows(RuntimeException.class, () -> service.create(missingAccount,
                futureDate(), SOURCE, WORKER_A, EIGHT, TEN));
        assertEquals(0, shiftCount());
    }

    @Test
    @DisplayName("AC-BR36 changing employee and date checks the target day and keeps source on conflict")
    void employeeAndDateChangeChecksTarget() {
        LocalDate date = futureDate();
        long id = service.create(manager(), date, SOURCE, WORKER_A, EIGHT, TEN).id();
        service.create(manager(), date.plusDays(1), DESTINATION, WORKER_B, EIGHT, TEN);
        assertThrows(EmployeeAccessException.class, () -> service.adjust(manager(), id,
                date.plusDays(1), DESTINATION, WORKER_B, EIGHT, TEN));
        assertEquals(WORKER_A, service.find(manager(), id).employeeId());
        assertEquals(date, service.find(manager(), id).date());
        var moved = service.adjust(manager(), id, date.plusDays(2), DESTINATION,
                WORKER_B, EIGHT, LocalTime.of(12, 0));
        assertEquals(WORKER_B, moved.employeeId());
        assertEquals(date.plusDays(2), moved.date());
        assertEquals(DESTINATION, moved.storeId());
        assertEquals(1, auditCount("SHIFT_ADJUSTED"));
    }

    @Test
    @DisplayName("AC-BR36/AC-BR37 audit failure rolls back adjustment and cancellation")
    void auditFailureRollsBackUpdates() {
        LocalDate date = futureDate();
        long id = service.create(manager(), date, SOURCE, WORKER_A, EIGHT, TEN).id();
        ActorContext missingAccount = new ActorContext(48999L, "missing", IdentityType.EMPLOYEE,
                MANAGER, manager().activeGrants());
        assertThrows(RuntimeException.class, () -> service.adjust(missingAccount, id,
                date.plusDays(1), SOURCE, WORKER_B, EIGHT, TEN));
        assertEquals(WORKER_A, service.find(manager(), id).employeeId());
        assertEquals(date, service.find(manager(), id).date());
        assertThrows(RuntimeException.class, () -> service.cancel(missingAccount, id, "Unavailable"));
        assertEquals(ShiftStatus.ACTIVE, service.find(manager(), id).status());
        assertEquals(0, auditCount("SHIFT_ADJUSTED"));
        assertEquals(0, auditCount("SHIFT_CANCELLED"));
    }

    @Test
    @DisplayName("AC-BR37 today and past shifts cannot be cancelled")
    void todayAndPastCancellationDenied() {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Oslo"));
        long todayShift = service.create(manager(), today, SOURCE, WORKER_A, EIGHT, TEN).id();
        long pastShift = service.create(manager(), today.minusDays(1), SOURCE, WORKER_A,
                EIGHT, TEN).id();
        assertEquals("SHIFT_NOT_FUTURE", assertThrows(ApiException.class,
                () -> service.cancel(manager(), todayShift, "Today")).code());
        assertEquals("SHIFT_NOT_FUTURE", assertThrows(ApiException.class,
                () -> service.cancel(manager(), pastShift, "Past")).code());
        assertEquals(2, shiftCount());
        assertEquals(0, auditCount("SHIFT_CANCELLED"));
    }

    @Test
    @DisplayName("AC-BR35 two independent MySQL connections racing first same-day shifts leave one active")
    void firstShiftRaceUsesEmployeeParentLock() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<Long> connectionIds = new ConcurrentLinkedQueue<>();
        LocalDate date = futureDate();
        Callable<Boolean> atSource = contender(SOURCE, date, ready, start, connectionIds);
        Callable<Boolean> atDestination = contender(DESTINATION, date, ready, start, connectionIds);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(atSource);
            var second = pool.submit(atDestination);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            int successful = (first.get(15, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(15, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(2, Set.copyOf(connectionIds).size());
            assertEquals(1, successful);
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM employee_shift "
                    + "WHERE employee_id=? AND shift_date=? AND shift_status='ACTIVE'",
                    Integer.class, WORKER_A, java.sql.Date.valueOf(date)));
            assertEquals(1, auditCount("SHIFT_CREATED"));
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    private Callable<Boolean> contender(long storeId, LocalDate date, CountDownLatch ready,
            CountDownLatch start, ConcurrentLinkedQueue<Long> connectionIds) {
        return () -> {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            try {
                return transaction.execute(status -> {
                    connectionIds.add(jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class));
                    ready.countDown();
                    try {
                        if (!start.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Race start barrier timed out.");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Race interrupted.", exception);
                    }
                    service.create(manager(), date, storeId, WORKER_A, EIGHT, TEN);
                    return true;
                });
            } catch (EmployeeAccessException exception) {
                if (exception.code() != EmployeeAccessException.Code.SAME_DAY_SHIFT_CONFLICT) {
                    throw exception;
                }
                return false;
            }
        };
    }

    private static LocalDate futureDate() {
        return LocalDate.now(ZoneId.of("Europe/Oslo")).plusDays(3);
    }

    private static ActorContext manager() {
        return new ActorContext(48004L, "t08-manager", IdentityType.EMPLOYEE, MANAGER,
                List.of(new ActiveRoleGrant(RoleCode.MANAGER, SOURCE),
                        new ActiveRoleGrant(RoleCode.MANAGER, DESTINATION)));
    }

    private static ActorContext sourceOnlyManager() {
        return new ActorContext(48004L, "t08-manager", IdentityType.EMPLOYEE, MANAGER,
                List.of(new ActiveRoleGrant(RoleCode.MANAGER, SOURCE)));
    }

    private static ActorContext workerA() {
        return new ActorContext(48014L, "t08-a", IdentityType.EMPLOYEE, WORKER_A,
                List.of(new ActiveRoleGrant(RoleCode.EMPLOYEE, SOURCE)));
    }

    private static ActorContext workerB() {
        return new ActorContext(48016L, "t08-b", IdentityType.EMPLOYEE, WORKER_B,
                List.of(new ActiveRoleGrant(RoleCode.EMPLOYEE, SOURCE)));
    }

    private int shiftCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM employee_shift WHERE employee_id IN (48003,48005,48007)", Integer.class);
    }

    private int auditCount(String action) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE actor_account_id=48004 AND action_code=?",
                Integer.class, action);
    }
}
