package com.bsr.scheduling.employee.domain;

import com.bsr.scheduling.employee.api.EmployeeAccessException;
import com.bsr.scheduling.employee.api.EmployeeSnapshot;
import java.util.Collection;
import java.util.Objects;

public final class Employee {

    private final EmployeeSnapshot snapshot;

    public Employee(EmployeeSnapshot snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
    }

    public EmployeeSnapshot snapshot() {
        return snapshot;
    }

    public void requireActive() {
        if (!snapshot.active()) {
            throw new EmployeeAccessException(
                    EmployeeAccessException.Code.EMPLOYEE_INACTIVE, "Employee is inactive.");
        }
    }

    public void validateNoSameDayShift(Collection<SameDayShiftConflict> conflicts) {
        Objects.requireNonNull(conflicts, "conflicts");
        if (conflicts.stream().anyMatch(conflict -> Objects.requireNonNull(conflict, "conflict").active())) {
            throw new EmployeeAccessException(
                    EmployeeAccessException.Code.SAME_DAY_SHIFT_CONFLICT,
                    "Employee already has an active shift on the requested date.");
        }
    }
}
