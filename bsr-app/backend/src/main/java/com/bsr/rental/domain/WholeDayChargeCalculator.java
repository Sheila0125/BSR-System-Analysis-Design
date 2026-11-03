package com.bsr.rental.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

public final class WholeDayChargeCalculator {

    private WholeDayChargeCalculator() {
    }

    /** Delivery today charges the complete local calendar day, including a short DST day. */
    public static BigDecimal additionAmount(BigDecimal rate, LocalDate start, int days, LocalDate deliveryDate) {
        return formalReductionRefund(rate,start,days,deliveryDate);
    }

    /** An already-started return day never earns a refund. */
    public static BigDecimal returnRefund(BigDecimal rate, LocalDate start, int days, LocalDate returnDate) {
        required(returnDate,"returnDate");
        requirePositiveDays(days);
        LocalDate end=required(start,"rentalStartDate").plusDays(days);
        if(returnDate.isBefore(start) || returnDate.isAfter(end))
            throw new IllegalArgumentException("Return date must be within the effective rental period");
        if(returnDate.equals(end)) return new BigDecimal("0.00");
        return formalReductionRefund(rate,start,days,returnDate.plusDays(1));
    }

    public static BigDecimal initialAmount(List<RentalItem> items, int rentalDays) {
        requirePositiveDays(rentalDays);
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("at least one rental item is required");
        }
        BigDecimal dailyTotal = BigDecimal.ZERO;
        for (RentalItem item : items) {
            if (item == null) {
                throw new IllegalArgumentException("rental items must not contain null");
            }
            dailyTotal = dailyTotal.add(item.dailyRateSnapshot());
        }
        return finalAmount(dailyTotal.multiply(BigDecimal.valueOf(rentalDays)), "initialAmount");
    }

    public static BigDecimal formalReductionRefund(
            BigDecimal dailyRateSnapshot,
            LocalDate rentalStartDate,
            int rentalDays,
            LocalDate effectiveDate) {
        BigDecimal rate = RentalItem.money(dailyRateSnapshot, "dailyRateSnapshot");
        LocalDate start = required(rentalStartDate, "rentalStartDate");
        LocalDate effective = required(effectiveDate, "effectiveDate");
        requirePositiveDays(rentalDays);

        LocalDate endExclusive = start.plusDays(rentalDays);
        if (effective.isBefore(start) || effective.isAfter(endExclusive)) {
            throw new IllegalArgumentException("effectiveDate must be within the agreed rental dates");
        }
        long remainingWholeDays = ChronoUnit.DAYS.between(effective, endExclusive);
        return finalAmount(rate.multiply(BigDecimal.valueOf(remainingWholeDays)), "refundAmount");
    }

    private static BigDecimal finalAmount(BigDecimal value, String field) {
        BigDecimal rounded = value.setScale(2, RoundingMode.HALF_UP);
        if (rounded.precision() > 12) {
            throw new IllegalArgumentException(field + " exceeds DECIMAL(12,2)");
        }
        return rounded;
    }

    private static void requirePositiveDays(int rentalDays) {
        if (rentalDays <= 0) {
            throw new IllegalArgumentException("rentalDays must be positive");
        }
    }

    private static <T> T required(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
