package com.bsr.scheduling.shift.application;

import com.bsr.audit.api.AuditAppender;
import com.bsr.identity.api.ActionPermission;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.Authorization;
import com.bsr.identity.api.IdentityType;
import com.bsr.platform.error.ApiException;
import com.bsr.scheduling.employee.api.EmployeeAccess;
import com.bsr.scheduling.employee.domain.Employee;
import com.bsr.scheduling.employee.domain.SameDayShiftConflict;
import com.bsr.scheduling.shift.domain.Shift;
import com.bsr.scheduling.shift.domain.ShiftStatus;
import com.bsr.storetransfer.store.api.StoreAccess;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.LongStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShiftService {
    private final ShiftPersistence persistence;
    private final StoreAccess stores;
    private final EmployeeAccess employees;
    private final Authorization authorization;
    private final AuditAppender audit;
    private final Clock clock;

    public ShiftService(ShiftPersistence persistence, StoreAccess stores, EmployeeAccess employees,
            Authorization authorization, AuditAppender audit, Clock clock) {
        this.persistence = persistence;
        this.stores = stores;
        this.employees = employees;
        this.authorization = authorization;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Shift create(ActorContext actor, LocalDate date, long storeId, long employeeId,
            LocalTime start, LocalTime end) {
        requireManager(actor, storeId);
        Shift proposed = Shift.schedule(number(), date, storeId, employeeId, start, end,
                actor.identityId(), nowUtc());
        lockStores(storeId);
        Map<Long, Employee> locked = lockEmployees(actor.identityId(), employeeId);
        requireNoConflict(locked.get(employeeId), employeeId, date, null);
        long id = persistence.create(proposed);
        audit.appendSuccess(actor, "EMPLOYEE_SHIFT", id, "SHIFT_CREATED",
                "storeId=" + storeId + ";employeeId=" + employeeId + ";date=" + date);
        return proposed.withId(id);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Shift adjust(ActorContext actor, long shiftId, LocalDate date, long storeId,
            long employeeId, LocalTime start, LocalTime end) {
        Shift observed = observed(shiftId);
        requireVisibleManager(actor, observed.storeId());
        requireManager(actor, storeId);
        lockStores(observed.storeId(), storeId);
        Map<Long, Employee> locked = lockEmployees(actor.identityId(), observed.employeeId(), employeeId);
        Shift current = lockedShift(shiftId, observed);
        requireActive(current);
        Shift adjusted = current.adjust(date, storeId, employeeId, start, end,
                actor.identityId(), nowUtc());
        requireNoConflict(locked.get(employeeId), employeeId, date, shiftId);
        persistence.save(adjusted);
        audit.appendSuccess(actor, "EMPLOYEE_SHIFT", shiftId, "SHIFT_ADJUSTED",
                "storeId=" + storeId + ";employeeId=" + employeeId + ";date=" + date);
        return adjusted;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Shift cancel(ActorContext actor, long shiftId, String reason) {
        Shift observed = observed(shiftId);
        requireVisibleManager(actor, observed.storeId());
        lockStores(observed.storeId());
        lockEmployees(actor.identityId(), observed.employeeId());
        Shift current = lockedShift(shiftId, observed);
        requireActive(current);
        LocalDate today = LocalDate.now(clock);
        if (!current.date().isAfter(today)) {
            throw conflict("SHIFT_NOT_FUTURE", "Only future shifts can be cancelled.");
        }
        Shift cancelled = current.cancel(reason, actor.identityId(), nowUtc(), today);
        persistence.save(cancelled);
        audit.appendSuccess(actor, "EMPLOYEE_SHIFT", shiftId, "SHIFT_CANCELLED",
                "storeId=" + current.storeId() + ";employeeId=" + current.employeeId());
        return cancelled;
    }

    @Transactional(readOnly = true)
    public Shift find(ActorContext actor, long shiftId) {
        Shift shift = observed(shiftId);
        requireVisibleManager(actor, shift.storeId());
        return shift;
    }

    @Transactional(readOnly = true)
    public List<Shift> storeShifts(ActorContext actor, long storeId) {
        requireManager(actor, storeId);
        return persistence.listByStore(storeId);
    }

    @Transactional(readOnly = true)
    public List<Shift> myShifts(ActorContext actor) {
        requireEmployeeIdentity(actor);
        authorization.require(actor, ActionPermission.EMPLOYEE_SHIFT_SELF_READ, null);
        return persistence.listByEmployee(actor.identityId());
    }

    private Shift observed(long shiftId) {
        if (shiftId <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SHIFT_ID", "Shift ID must be positive.");
        }
        return persistence.find(shiftId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "SHIFT_NOT_FOUND", "Shift does not exist."));
    }

    private Shift lockedShift(long shiftId, Shift observed) {
        Shift current = persistence.lock(shiftId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "SHIFT_NOT_FOUND", "Shift does not exist."));
        if (current.storeId() != observed.storeId()
                || current.employeeId() != observed.employeeId()
                || !current.date().equals(observed.date())
                || !current.start().equals(observed.start())
                || !current.end().equals(observed.end())
                || current.changedByEmployeeId() != observed.changedByEmployeeId()
                || !current.changedAt().equals(observed.changedAt())
                || current.status() != observed.status()
                || !Objects.equals(current.cancellationReason(), observed.cancellationReason())) {
            throw conflict("SHIFT_CHANGED", "Shift changed while acquiring parent locks.");
        }
        return current;
    }

    private void requireNoConflict(Employee employee, long employeeId, LocalDate date, Long excludingId) {
        employee.validateNoSameDayShift(persistence.activeOnDate(employeeId, date, excludingId).stream()
                .map(shift -> new SameDayShiftConflict(shift.id(), true)).toList());
    }

    private void requireActive(Shift shift) {
        if (shift.status() != ShiftStatus.ACTIVE) {
            throw conflict("SHIFT_NOT_ACTIVE", "Only an active shift can be changed.");
        }
    }

    private void requireManager(ActorContext actor, long storeId) {
        requireEmployeeIdentity(actor);
        authorization.require(actor, ActionPermission.MANAGER_STORE_AND_SCHEDULING, storeId);
    }

    private void requireVisibleManager(ActorContext actor, long storeId) {
        requireEmployeeIdentity(actor);
        try {
            authorization.require(actor, ActionPermission.MANAGER_STORE_AND_SCHEDULING, storeId);
        } catch (ApiException exception) {
            if (exception.status() != HttpStatus.FORBIDDEN) {
                throw exception;
            }
            throw new ApiException(HttpStatus.NOT_FOUND, "SHIFT_NOT_FOUND", "Shift does not exist.");
        }
    }

    private void requireEmployeeIdentity(ActorContext actor) {
        if (actor == null || actor.identityType() != IdentityType.EMPLOYEE || actor.identityId() <= 0) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EMPLOYEE_IDENTITY_REQUIRED",
                    "An employee identity is required.");
        }
    }

    private void lockStores(long... storeIds) {
        LongStream.of(storeIds).distinct().sorted().forEach(stores::lockForUse);
    }

    private Map<Long, Employee> lockEmployees(long actorEmployeeId, long... scheduledEmployeeIds) {
        Map<Long, Employee> locked = new HashMap<>();
        LongStream.concat(LongStream.of(actorEmployeeId), LongStream.of(scheduledEmployeeIds))
                .distinct().sorted().forEach(id -> {
                    boolean scheduled = LongStream.of(scheduledEmployeeIds).anyMatch(candidate -> candidate == id);
                    locked.put(id, scheduled ? employees.lockForScheduling(id) : employees.lockForUse(id));
                });
        return locked;
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
    }

    private static String number() {
        return "SH-" + UUID.randomUUID().toString().replace("-", "").substring(0, 27);
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
