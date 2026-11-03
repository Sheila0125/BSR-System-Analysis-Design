package com.bsr.scheduling.employee.api;

public record EmployeeSnapshot(
        long employeeId,
        String employeeNumber,
        String fullName,
        String email,
        String phone,
        boolean active) {
}
