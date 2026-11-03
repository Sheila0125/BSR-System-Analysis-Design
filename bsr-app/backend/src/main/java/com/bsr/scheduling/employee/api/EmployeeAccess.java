package com.bsr.scheduling.employee.api;

import com.bsr.scheduling.employee.domain.Employee;
import java.util.Optional;

public interface EmployeeAccess {

    Optional<EmployeeSnapshot> findById(long employeeId);

    Employee lockForUse(long employeeId);

    Employee lockForScheduling(long employeeId);

    Employee lockForDeactivation(long employeeId);
}
