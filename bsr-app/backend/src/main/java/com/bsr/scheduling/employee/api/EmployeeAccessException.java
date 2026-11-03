package com.bsr.scheduling.employee.api;

public final class EmployeeAccessException extends RuntimeException {

    private final Code code;

    public EmployeeAccessException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public enum Code {
        EMPLOYEE_NOT_FOUND,
        EMPLOYEE_INACTIVE,
        SAME_DAY_SHIFT_CONFLICT
    }
}
