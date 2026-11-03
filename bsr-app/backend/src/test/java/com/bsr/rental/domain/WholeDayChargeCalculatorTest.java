package com.bsr.rental.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WholeDayChargeCalculatorTest {

    @Test
    @DisplayName("AC-BR16 approved mid-day addition includes today; old item refunds future days only")
    void approvedAdditionAndReturnBoundaries() {
        var start=LocalDate.of(2026,12,20);
        assertEquals(new BigDecimal("298.50"), WholeDayChargeCalculator.additionAmount(new BigDecimal("99.50"),start,3,start));
        assertEquals(new BigDecimal("199.00"), WholeDayChargeCalculator.returnRefund(new BigDecimal("99.50"),start,3,start));
        assertEquals(new BigDecimal("0.00"), WholeDayChargeCalculator.returnRefund(new BigDecimal("99.50"),start,3,start.plusDays(2)));
        assertEquals(new BigDecimal("99.50"), WholeDayChargeCalculator.additionAmount(new BigDecimal("99.50"),start,3,start.plusDays(2)));
    }

    @Test
    @DisplayName("AC-BR16 Oslo midnight and 23/25-hour DST days remain calendar days")
    void midnightAndDstCalendarDays() {
        var oslo=ZoneId.of("Europe/Oslo");
        var start=LocalDate.of(2026,3,28);
        var before=Instant.parse("2026-03-28T22:59:59Z").atZone(oslo).toLocalDate();
        var after=Instant.parse("2026-03-28T23:00:00Z").atZone(oslo).toLocalDate();
        assertEquals(new BigDecimal("199.00"),WholeDayChargeCalculator.returnRefund(new BigDecimal("99.50"),start,3,before));
        assertEquals(new BigDecimal("99.50"),WholeDayChargeCalculator.returnRefund(new BigDecimal("99.50"),start,3,after));
        assertEquals(new BigDecimal("199.00"),WholeDayChargeCalculator.additionAmount(new BigDecimal("99.50"),start,3,after));
        var autumn=LocalDate.of(2026,10,24);
        var longDay=Instant.parse("2026-10-25T01:30:00Z").atZone(oslo).toLocalDate();
        assertEquals(new BigDecimal("199.00"),WholeDayChargeCalculator.additionAmount(new BigDecimal("99.50"),autumn,3,longDay));
    }

    @Test
    @DisplayName("AC-UC05/AC-BR16 a formal reduction refunds only two not-yet-started whole days")
    void formalReductionUsesRemainingCalendarDays() {
        BigDecimal refund = WholeDayChargeCalculator.formalReductionRefund(
                new BigDecimal("99.50"),
                LocalDate.of(2026, 12, 20),
                3,
                LocalDate.of(2026, 12, 21));

        assertEquals(new BigDecimal("199.00"), refund);
    }

    @Test
    @DisplayName("AC-BR16 the end boundary has no remaining whole day and never uses hour duration")
    void endExclusiveBoundaryHasZeroRefund() {
        BigDecimal refund = WholeDayChargeCalculator.formalReductionRefund(
                new BigDecimal("99.50"),
                LocalDate.of(2026, 3, 28),
                2,
                LocalDate.of(2026, 3, 30));

        assertEquals(new BigDecimal("0.00"), refund);
    }

    @Test
    @DisplayName("AC-BR16 adjustment date outside the agreed calendar range is rejected")
    void adjustmentOutsideRentalDatesIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> WholeDayChargeCalculator.formalReductionRefund(
                new BigDecimal("99.50"),
                LocalDate.of(2026, 12, 20),
                3,
                LocalDate.of(2026, 12, 24)));
    }
}
