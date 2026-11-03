package com.bsr.repair.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record RepairRatePeriod(
        long contractorId,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        BigDecimal hourlyRate) {

    public RepairRatePeriod {
        if (contractorId <= 0) {
            throw new IllegalArgumentException("contractorId must be positive.");
        }
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        Objects.requireNonNull(hourlyRate, "hourlyRate");
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("effectiveTo must be on or after effectiveFrom.");
        }
        if (hourlyRate.signum() <= 0 || hourlyRate.scale() > 2 || hourlyRate.precision() > 12) {
            throw new IllegalArgumentException("hourlyRate must be positive DECIMAL(12,2).");
        }
        hourlyRate = hourlyRate.setScale(2);
    }

    public boolean covers(LocalDate date) {
        Objects.requireNonNull(date, "date");
        return !date.isBefore(effectiveFrom)
                && (effectiveTo == null || !date.isAfter(effectiveTo));
    }

    public boolean overlaps(RepairRatePeriod other) {
        Objects.requireNonNull(other, "other");
        return contractorId == other.contractorId
                && (effectiveTo == null || !effectiveTo.isBefore(other.effectiveFrom))
                && (other.effectiveTo == null || !other.effectiveTo.isBefore(effectiveFrom));
    }
}
