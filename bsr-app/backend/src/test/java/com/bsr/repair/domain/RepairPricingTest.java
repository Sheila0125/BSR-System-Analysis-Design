package com.bsr.repair.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RepairPricingTest {

    private static final LocalDate START = LocalDate.of(2026, 10, 1);

    @Test
    @DisplayName("AC-BR24 accepts positive quarter hours without rounding 0.30 into validity")
    void AC_BR24_quarterHourValidation() {
        assertEquals(new BigDecimal("0.25"), labor("0.25", START).durationHours());
        assertEquals(new BigDecimal("1.50"), labor("1.50", START).durationHours());
        for (String invalid : List.of("0", "-0.25", "0.10", "0.30")) {
            assertThrows(IllegalArgumentException.class, () -> labor(invalid, START));
        }
    }

    @Test
    @DisplayName("AC-BR25 closed rate endpoints include both dates and open end extends forward")
    void AC_BR25_rateBoundsAndOverlap() {
        RepairRatePeriod october = rate(41L, START, START.plusDays(9), "100.00");
        assertTrue(october.covers(START));
        assertTrue(october.covers(START.plusDays(9)));
        assertFalse(october.covers(START.minusDays(1)));
        assertTrue(october.overlaps(rate(41L, START.plusDays(9), START.plusDays(20), "110.00")));
        assertFalse(october.overlaps(rate(41L, START.plusDays(10), null, "110.00")));
        assertTrue(rate(41L, START.plusDays(10), null, "110.00")
                .covers(START.plusYears(2)));
        assertThrows(IllegalArgumentException.class,
                () -> rate(41L, START.plusDays(1), START, "100.00"));
    }

    @Test
    @DisplayName("AC-BR25 labor prices by its own contractor and repair date, retaining first snapshot")
    void AC_BR25_historicalRateSnapshotAndHalfUp() {
        RepairLabor labor = labor("0.25", START.plusDays(4));
        assertFalse(labor.applyRate(51L, rate(41L, START.plusDays(5), null, "100.00")));
        assertNull(labor.laborAmount());
        assertThrows(IllegalArgumentException.class,
                () -> labor.applyRate(51L, rate(42L, START, null, "100.00")));

        assertTrue(labor.applyRate(52L, rate(41L, START, START.plusDays(4), "99.99")));
        assertEquals(new BigDecimal("99.99"), labor.hourlyRateSnapshot());
        assertEquals(new BigDecimal("25.00"), labor.laborAmount());
        assertFalse(labor.applyRate(53L, rate(41L, START, null, "200.00")));
        assertEquals(new BigDecimal("25.00"), labor.laborAmount());
    }

    @Test
    @DisplayName("AC-BR26 missing rate blocks charge; completed charge sums saved line snapshots")
    void AC_BR26_chargeRequiresEveryLaborPriced() {
        RepairLabor first = labor("0.25", START);
        RepairLabor second = labor("1.50", START.plusDays(1));
        first.applyRate(51L, rate(41L, START, null, "99.99"));
        assertThrows(IllegalStateException.class,
                () -> RepairServiceCharge.fromLabors(List.of(first, second)));
        second.applyRate(51L, rate(41L, START, null, "99.99"));
        RepairServiceCharge charge = RepairServiceCharge.fromLabors(List.of(first, second));
        assertEquals(new BigDecimal("1.75"), charge.totalHours());
        assertEquals(new BigDecimal("174.99"), charge.serviceAmount());
    }

    private static RepairLabor labor(String duration, LocalDate date) {
        return RepairLabor.create(41L, date, new BigDecimal(duration), "Replaced the damaged component", null);
    }

    private static RepairRatePeriod rate(long contractorId, LocalDate from, LocalDate to, String hourlyRate) {
        return new RepairRatePeriod(contractorId, from, to, new BigDecimal(hourlyRate));
    }
}
