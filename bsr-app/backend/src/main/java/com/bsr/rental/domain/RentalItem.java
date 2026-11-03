package com.bsr.rental.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class RentalItem {

    private final long equipmentId;
    private final BigDecimal dailyRateSnapshot;
    private RentalItemStatus status;

    private RentalItem(long equipmentId, BigDecimal dailyRateSnapshot, RentalItemStatus status) {
        this.equipmentId = positiveId(equipmentId, "equipmentId");
        this.dailyRateSnapshot = money(dailyRateSnapshot, "dailyRateSnapshot");
        this.status = required(status, "status");
    }

    public static RentalItem pendingIssue(long equipmentId, BigDecimal dailyRateSnapshot) {
        return new RentalItem(equipmentId, dailyRateSnapshot, RentalItemStatus.PENDING_ISSUE);
    }

    public static RentalItem restore(long equipmentId, BigDecimal rate, RentalItemStatus status) {
        return new RentalItem(equipmentId, rate, status);
    }

    public void issue() {
        if (status != RentalItemStatus.PENDING_ISSUE) throw new IllegalStateException("Only a pending item may be issued");
        status = RentalItemStatus.RENTED;
    }
    public void receivePhysicalReturn() {
        if (status != RentalItemStatus.RENTED) throw new IllegalStateException("Only a rented item may be returned");
        status = RentalItemStatus.RETURNED;
    }

    private static long positiveId(long value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    static BigDecimal money(BigDecimal value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        BigDecimal normalized;
        try {
            normalized = value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(field + " must have at most two decimal places", exception);
        }
        if (normalized.signum() < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        if (normalized.precision() > 12) {
            throw new IllegalArgumentException(field + " exceeds DECIMAL(12,2)");
        }
        return normalized;
    }

    private static <T> T required(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    public long equipmentId() {
        return equipmentId;
    }

    public BigDecimal dailyRateSnapshot() {
        return dailyRateSnapshot;
    }

    public RentalItemStatus status() {
        return status;
    }
}
