package com.bsr.repair.domain;

import java.math.BigDecimal;
import java.util.List;

public record RepairServiceCharge(BigDecimal totalHours, BigDecimal serviceAmount) {

    public static RepairServiceCharge fromLabors(List<RepairLabor> labors) {
        if (labors == null || labors.isEmpty()) {
            throw new IllegalStateException("At least one labor entry is required to form a charge.");
        }
        BigDecimal hours = new BigDecimal("0.00");
        BigDecimal amount = new BigDecimal("0.00");
        for (RepairLabor labor : labors) {
            if (labor.laborAmount() == null) {
                throw new IllegalStateException("Every labor entry requires a historical rate before charging.");
            }
            hours = hours.add(labor.durationHours());
            amount = amount.add(labor.laborAmount());
        }
        if (hours.precision() > 8 || amount.precision() > 12) {
            throw new IllegalArgumentException("Repair charge exceeds database precision.");
        }
        return new RepairServiceCharge(hours, amount);
    }
}
