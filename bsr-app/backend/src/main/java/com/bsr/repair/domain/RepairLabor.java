package com.bsr.repair.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

public final class RepairLabor {

    private static final BigDecimal QUARTER_HOUR = new BigDecimal("0.25");

    private final long contractorId;
    private final LocalDate repairDate;
    private final BigDecimal durationHours;
    private final String handlingResult;
    private final String note;
    private Long repairRateId;
    private BigDecimal hourlyRateSnapshot;
    private BigDecimal laborAmount;

    private RepairLabor(long contractorId, LocalDate repairDate, BigDecimal durationHours,
            String handlingResult, String note) {
        if (contractorId <= 0) {
            throw new IllegalArgumentException("contractorId must be positive.");
        }
        this.contractorId = contractorId;
        this.repairDate = Objects.requireNonNull(repairDate, "repairDate");
        Objects.requireNonNull(durationHours, "durationHours");
        if (durationHours.signum() <= 0 || durationHours.remainder(QUARTER_HOUR).signum() != 0
                || durationHours.precision() > 8) {
            throw new IllegalArgumentException("durationHours must be a positive quarter-hour multiple.");
        }
        this.durationHours = durationHours.setScale(2, RoundingMode.UNNECESSARY);
        if (handlingResult == null || handlingResult.isBlank() || handlingResult.trim().length() > 500) {
            throw new IllegalArgumentException("handlingResult must contain 1–500 characters.");
        }
        this.handlingResult = handlingResult.trim();
        if (note != null && note.trim().length() > 500) {
            throw new IllegalArgumentException("note must not exceed 500 characters.");
        }
        this.note = note == null || note.isBlank() ? null : note.trim();
    }

    public static RepairLabor create(long contractorId, LocalDate repairDate, BigDecimal durationHours,
            String handlingResult, String note) {
        return new RepairLabor(contractorId, repairDate, durationHours, handlingResult, note);
    }

    public static RepairLabor restore(long contractorId, LocalDate repairDate, BigDecimal durationHours,
            String handlingResult, String note, Long repairRateId,
            BigDecimal hourlyRateSnapshot, BigDecimal laborAmount) {
        RepairLabor labor = create(contractorId, repairDate, durationHours, handlingResult, note);
        if ((repairRateId == null) != (hourlyRateSnapshot == null)
                || (repairRateId == null) != (laborAmount == null)) {
            throw new IllegalStateException("Stored labor rate snapshot must be complete or absent.");
        }
        if (repairRateId != null) {
            if (repairRateId <= 0 || hourlyRateSnapshot.signum() <= 0
                    || laborAmount.compareTo(durationHours.multiply(hourlyRateSnapshot)
                            .setScale(2, RoundingMode.HALF_UP)) != 0) {
                throw new IllegalStateException("Stored labor rate snapshot is invalid.");
            }
            labor.repairRateId = repairRateId;
            labor.hourlyRateSnapshot = hourlyRateSnapshot;
            labor.laborAmount = laborAmount;
        }
        return labor;
    }

    public boolean applyRate(long rateId, RepairRatePeriod rate) {
        if (rateId <= 0) {
            throw new IllegalArgumentException("rateId must be positive.");
        }
        Objects.requireNonNull(rate, "rate");
        if (rate.contractorId() != contractorId) {
            throw new IllegalArgumentException("Rate belongs to a different contractor.");
        }
        if (repairRateId != null || !rate.covers(repairDate)) {
            return false;
        }
        BigDecimal calculated = durationHours.multiply(rate.hourlyRate()).setScale(2, RoundingMode.HALF_UP);
        if (calculated.precision() > 12) {
            throw new IllegalArgumentException("Calculated labor amount exceeds DECIMAL(12,2).");
        }
        repairRateId = rateId;
        hourlyRateSnapshot = rate.hourlyRate();
        laborAmount = calculated;
        return true;
    }

    public long contractorId() { return contractorId; }
    public LocalDate repairDate() { return repairDate; }
    public BigDecimal durationHours() { return durationHours; }
    public String handlingResult() { return handlingResult; }
    public String note() { return note; }
    public Long repairRateId() { return repairRateId; }
    public BigDecimal hourlyRateSnapshot() { return hourlyRateSnapshot; }
    public BigDecimal laborAmount() { return laborAmount; }
}
