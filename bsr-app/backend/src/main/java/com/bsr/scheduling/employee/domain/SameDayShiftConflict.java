package com.bsr.scheduling.employee.domain;

public record SameDayShiftConflict(long shiftId, boolean active) {

    public SameDayShiftConflict {
        if (shiftId <= 0) {
            throw new IllegalArgumentException("shiftId must be positive.");
        }
    }
}
