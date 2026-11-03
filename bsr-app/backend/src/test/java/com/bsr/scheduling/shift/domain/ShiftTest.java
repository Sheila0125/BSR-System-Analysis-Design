package com.bsr.scheduling.shift.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ShiftTest {
    private static final LocalDate DATE = LocalDate.of(2026, 10, 20);
    private static final LocalDateTime CHANGED_AT = LocalDateTime.of(2026, 10, 13, 8, 0);

    @Test
    @DisplayName("AC-BR33 and AC-BR34 accept one employee at one store from 08:00 to 22:00")
    void acceptsExactBusinessHours() {
        Shift shift = Shift.schedule("SH-20261020-A", DATE, 7L, 11L,
                LocalTime.of(8, 0), LocalTime.of(22, 0), 31L, CHANGED_AT);

        assertEquals(DATE, shift.date());
        assertEquals(7L, shift.storeId());
        assertEquals(11L, shift.employeeId());
        assertEquals(LocalTime.of(8, 0), shift.start());
        assertEquals(LocalTime.of(22, 0), shift.end());
        assertEquals(ShiftStatus.ACTIVE, shift.status());
        assertNull(shift.cancellationReason());
    }

    @Test
    @DisplayName("AC-BR34 reject 07:59, 22:01, equal times and crossing midnight")
    void rejectsOutsideHoursAndInvalidOrder() {
        assertThrows(IllegalArgumentException.class, () -> schedule(LocalTime.of(7, 59), LocalTime.NOON));
        assertThrows(IllegalArgumentException.class, () -> schedule(LocalTime.NOON, LocalTime.of(22, 1)));
        assertThrows(IllegalArgumentException.class, () -> schedule(LocalTime.NOON, LocalTime.NOON));
        assertThrows(IllegalArgumentException.class, () -> schedule(LocalTime.of(21, 0), LocalTime.of(8, 0)));
    }

    @Test
    @DisplayName("AC-BR33 missing date, store, employee or time is rejected")
    void rejectsMissingRequiredFacts() {
        assertThrows(IllegalArgumentException.class, () -> Shift.schedule("SH-A", null, 7L, 11L,
                LocalTime.of(8, 0), LocalTime.of(9, 0), 31L, CHANGED_AT));
        assertThrows(IllegalArgumentException.class, () -> Shift.schedule("SH-A", DATE, 0L, 11L,
                LocalTime.of(8, 0), LocalTime.of(9, 0), 31L, CHANGED_AT));
        assertThrows(IllegalArgumentException.class, () -> Shift.schedule("SH-A", DATE, 7L, 0L,
                LocalTime.of(8, 0), LocalTime.of(9, 0), 31L, CHANGED_AT));
        assertThrows(IllegalArgumentException.class, () -> Shift.schedule("SH-A", DATE, 7L, 11L,
                null, LocalTime.of(9, 0), 31L, CHANGED_AT));
    }

    @Test
    @DisplayName("AC-BR37 future cancellation retains original plan and records actor, time and reason")
    void futureCancellationRetainsPlan() {
        Shift original = schedule(LocalTime.of(9, 0), LocalTime.of(17, 0));
        LocalDateTime cancellationTime = LocalDateTime.of(2026, 10, 14, 7, 30);

        Shift cancelled = original.cancel("Employee unavailable", 32L, cancellationTime,
                LocalDate.of(2026, 10, 19));

        assertEquals(ShiftStatus.CANCELLED, cancelled.status());
        assertEquals(original.date(), cancelled.date());
        assertEquals(original.storeId(), cancelled.storeId());
        assertEquals(original.employeeId(), cancelled.employeeId());
        assertEquals(original.start(), cancelled.start());
        assertEquals(original.end(), cancelled.end());
        assertEquals("Employee unavailable", cancelled.cancellationReason());
        assertEquals(32L, cancelled.changedByEmployeeId());
        assertEquals(cancellationTime, cancelled.changedAt());
        assertEquals(ShiftStatus.ACTIVE, original.status());
    }

    @Test
    @DisplayName("AC-BR37 current or past shifts, empty reason and repeat cancellation are rejected")
    void rejectsInvalidCancellation() {
        Shift original = schedule(LocalTime.of(9, 0), LocalTime.of(17, 0));
        assertThrows(IllegalStateException.class, () -> original.cancel("Reason", 32L, CHANGED_AT, DATE));
        assertThrows(IllegalStateException.class, () -> original.cancel("Reason", 32L, CHANGED_AT, DATE.plusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> original.cancel(" ", 32L, CHANGED_AT, DATE.minusDays(1)));
        Shift cancelled = original.cancel("Reason", 32L, CHANGED_AT, DATE.minusDays(1));
        assertThrows(IllegalStateException.class,
                () -> cancelled.cancel("Another", 32L, CHANGED_AT, DATE.minusDays(1)));
    }

    @Test
    @DisplayName("AC-BR36 adjustment checks the new hours without modifying the original")
    void adjustmentValidatesAndKeepsOriginal() {
        Shift original = schedule(LocalTime.of(9, 0), LocalTime.of(17, 0));
        Shift adjusted = original.adjust(DATE.plusDays(1), 8L, 12L,
                LocalTime.of(8, 0), LocalTime.of(16, 0), 31L, CHANGED_AT.plusDays(1));
        assertEquals(DATE.plusDays(1), adjusted.date());
        assertEquals(8L, adjusted.storeId());
        assertEquals(12L, adjusted.employeeId());
        assertEquals(DATE, original.date());
        assertThrows(IllegalArgumentException.class, () -> original.adjust(DATE, 7L, 11L,
                LocalTime.of(7, 59), LocalTime.of(16, 0), 31L, CHANGED_AT));
    }

    private static Shift schedule(LocalTime start, LocalTime end) {
        return Shift.schedule("SH-20261020-A", DATE, 7L, 11L, start, end, 31L, CHANGED_AT);
    }
}
