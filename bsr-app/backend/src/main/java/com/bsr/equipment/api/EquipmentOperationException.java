package com.bsr.equipment.api;

public final class EquipmentOperationException extends RuntimeException {

    private final Code code;

    public EquipmentOperationException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public enum Code {
        EQUIPMENT_NOT_FOUND,
        EQUIPMENT_STATE_CONFLICT,
        EQUIPMENT_STORE_CONFLICT,
        EQUIPMENT_REFERENCE_CONFLICT
    }
}
