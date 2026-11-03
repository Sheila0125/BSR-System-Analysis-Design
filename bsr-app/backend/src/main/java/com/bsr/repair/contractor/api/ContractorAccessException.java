package com.bsr.repair.contractor.api;

public final class ContractorAccessException extends RuntimeException {

    private final Code code;

    public ContractorAccessException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public enum Code {
        CONTRACTOR_NOT_FOUND,
        CONTRACTOR_INACTIVE
    }
}
