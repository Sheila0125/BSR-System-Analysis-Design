package com.bsr.scheduling.shift.application;

import com.bsr.scheduling.shift.domain.Shift;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ShiftPersistence {
    long create(Shift shift);

    Optional<Shift> find(long shiftId);

    Optional<Shift> lock(long shiftId);

    List<Shift> activeOnDate(long employeeId, LocalDate date, Long excludingShiftId);

    List<Shift> listByEmployee(long employeeId);

    List<Shift> listByStore(long storeId);

    void save(Shift shift);
}
