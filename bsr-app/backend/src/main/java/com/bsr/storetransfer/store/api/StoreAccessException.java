package com.bsr.storetransfer.store.api;

public final class StoreAccessException extends RuntimeException {

    private final Code code;

    public StoreAccessException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public enum Code {
        STORE_NOT_FOUND,
        STORE_INACTIVE
    }
}
