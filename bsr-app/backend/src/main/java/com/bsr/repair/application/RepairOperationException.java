package com.bsr.repair.application;

public final class RepairOperationException extends RuntimeException {

    public enum Code {
        UNFINISHED_TASK_EXISTS,
        STORE_CONFLICT,
        TASK_NOT_FOUND,
        TASK_STATE_CONFLICT,
        RATE_OVERLAP,
        CONTRACTOR_HAS_UNFINISHED_TASKS
    }

    private final Code code;

    public RepairOperationException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
