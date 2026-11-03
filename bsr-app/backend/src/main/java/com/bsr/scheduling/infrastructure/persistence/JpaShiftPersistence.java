package com.bsr.scheduling.infrastructure.persistence;

import com.bsr.scheduling.shift.api.FutureShiftProjection;
import com.bsr.scheduling.shift.api.ShiftBlocker;
import com.bsr.scheduling.shift.application.ShiftPersistence;
import com.bsr.scheduling.shift.domain.Shift;
import com.bsr.scheduling.shift.domain.ShiftStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class JpaShiftPersistence implements ShiftPersistence, FutureShiftProjection {
    private final EmployeeShiftRepository shifts;

    public JpaShiftPersistence(EmployeeShiftRepository shifts) {
        this.shifts = shifts;
    }

    @Override
    public long create(Shift shift) {
        if (shift.id() != null || shift.status() != ShiftStatus.ACTIVE) {
            throw new IllegalArgumentException("Only a new active shift can be created.");
        }
        EmployeeShiftRow row = new EmployeeShiftRow();
        copy(shift, row);
        return shifts.saveAndFlush(row).shiftId;
    }

    @Override
    public Optional<Shift> find(long shiftId) {
        return shifts.findById(shiftId).map(JpaShiftPersistence::toDomain);
    }

    @Override
    public Optional<Shift> lock(long shiftId) {
        return shifts.findByIdForUpdate(shiftId).map(JpaShiftPersistence::toDomain);
    }

    @Override
    public List<Shift> activeOnDate(long employeeId, LocalDate date, Long excludingShiftId) {
        return shifts.findByEmployeeIdAndShiftDateAndShiftStatusOrderByShiftIdAsc(
                        employeeId, date, ShiftStatus.ACTIVE.name()).stream()
                .filter(row -> excludingShiftId == null || !excludingShiftId.equals(row.shiftId))
                .map(JpaShiftPersistence::toDomain)
                .toList();
    }

    @Override
    public List<Shift> listByEmployee(long employeeId) {
        return shifts.findByEmployeeIdOrderByShiftDateAscShiftIdAsc(employeeId).stream()
                .map(JpaShiftPersistence::toDomain).toList();
    }

    @Override
    public List<Shift> listByStore(long storeId) {
        return shifts.findByStoreIdOrderByShiftDateAscShiftIdAsc(storeId).stream()
                .map(JpaShiftPersistence::toDomain).toList();
    }

    @Override
    public void save(Shift shift) {
        if (shift.id() == null) {
            throw new IllegalArgumentException("A persisted shift ID is required for update.");
        }
        EmployeeShiftRow row = shifts.findById(shift.id())
                .orElseThrow(() -> new IllegalStateException("Shift disappeared during update."));
        copy(shift, row);
        shifts.saveAndFlush(row);
    }

    @Override
    public List<ShiftBlocker> futureActiveAtStore(long storeId, LocalDate businessToday) {
        return shifts.findByStoreIdAndShiftDateGreaterThanAndShiftStatusOrderByShiftDateAscShiftIdAsc(
                        storeId, businessToday, ShiftStatus.ACTIVE.name()).stream()
                .map(row -> new ShiftBlocker(row.shiftId, row.storeId, row.shiftDate))
                .toList();
    }

    private static Shift toDomain(EmployeeShiftRow row) {
        return Shift.restore(row.shiftId, row.shiftNumber, row.shiftDate, row.storeId,
                row.employeeId, row.startTime, row.endTime, ShiftStatus.valueOf(row.shiftStatus),
                row.changedByEmployeeId, row.changedAt, row.cancellationReason);
    }

    private static void copy(Shift shift, EmployeeShiftRow row) {
        row.shiftNumber = shift.number();
        row.shiftDate = shift.date();
        row.storeId = shift.storeId();
        row.employeeId = shift.employeeId();
        row.startTime = shift.start();
        row.endTime = shift.end();
        row.shiftStatus = shift.status().name();
        row.changedByEmployeeId = shift.changedByEmployeeId();
        row.changedAt = shift.changedAt();
        row.cancellationReason = shift.cancellationReason();
    }
}
