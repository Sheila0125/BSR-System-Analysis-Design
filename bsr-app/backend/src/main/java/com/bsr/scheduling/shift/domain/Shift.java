package com.bsr.scheduling.shift.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public final class Shift {
    private static final LocalTime FIRST_START = LocalTime.of(8, 0);
    private static final LocalTime LAST_END = LocalTime.of(22, 0);

    private final Long id;
    private final String number;
    private final LocalDate date;
    private final long storeId;
    private final long employeeId;
    private final LocalTime start;
    private final LocalTime end;
    private final ShiftStatus status;
    private final long changedByEmployeeId;
    private final LocalDateTime changedAt;
    private final String cancellationReason;

    private Shift(Long id, String number, LocalDate date, long storeId, long employeeId,
            LocalTime start, LocalTime end, ShiftStatus status, long changedByEmployeeId,
            LocalDateTime changedAt, String cancellationReason) {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("Shift ID must be positive.");
        }
        if (number == null || number.isBlank() || number.length() > 30) {
            throw new IllegalArgumentException("Shift number is required and must fit the schema.");
        }
        if (date == null || storeId <= 0 || employeeId <= 0) {
            throw new IllegalArgumentException("Shift requires a date, store and employee.");
        }
        if (start == null || end == null || start.isBefore(FIRST_START)
                || end.isAfter(LAST_END) || !start.isBefore(end)) {
            throw new IllegalArgumentException("Shift must be within 08:00-22:00 with start before end.");
        }
        if (status == null || changedByEmployeeId <= 0 || changedAt == null) {
            throw new IllegalArgumentException("Shift requires status and change facts.");
        }
        if (status == ShiftStatus.CANCELLED && (cancellationReason == null
                || cancellationReason.isBlank() || cancellationReason.length() > 255)) {
            throw new IllegalArgumentException("Cancelled shift requires a reason up to 255 characters.");
        }
        if (status == ShiftStatus.ACTIVE && cancellationReason != null) {
            throw new IllegalArgumentException("Active shift cannot have a cancellation reason.");
        }
        this.id = id;
        this.number = number;
        this.date = date;
        this.storeId = storeId;
        this.employeeId = employeeId;
        this.start = start;
        this.end = end;
        this.status = status;
        this.changedByEmployeeId = changedByEmployeeId;
        this.changedAt = changedAt;
        this.cancellationReason = cancellationReason;
    }

    public static Shift schedule(String number, LocalDate date, long storeId, long employeeId,
            LocalTime start, LocalTime end, long changedByEmployeeId, LocalDateTime changedAt) {
        return new Shift(null, number, date, storeId, employeeId, start, end,
                ShiftStatus.ACTIVE, changedByEmployeeId, changedAt, null);
    }

    public static Shift restore(long id, String number, LocalDate date, long storeId,
            long employeeId, LocalTime start, LocalTime end, ShiftStatus status,
            long changedByEmployeeId, LocalDateTime changedAt, String cancellationReason) {
        return new Shift(id, number, date, storeId, employeeId, start, end, status,
                changedByEmployeeId, changedAt, cancellationReason);
    }

    public Shift withId(long newId) {
        return new Shift(newId, number, date, storeId, employeeId, start, end, status,
                changedByEmployeeId, changedAt, cancellationReason);
    }

    public Shift adjust(LocalDate newDate, long newStoreId, long newEmployeeId,
            LocalTime newStart, LocalTime newEnd, long actorEmployeeId, LocalDateTime at) {
        requireActive();
        return new Shift(id, number, newDate, newStoreId, newEmployeeId, newStart, newEnd,
                ShiftStatus.ACTIVE, actorEmployeeId, at, null);
    }

    public Shift cancel(String reason, long actorEmployeeId, LocalDateTime at, LocalDate today) {
        requireActive();
        if (today == null || !date.isAfter(today)) {
            throw new IllegalStateException("Only future shifts can be cancelled.");
        }
        if (reason == null || reason.isBlank() || reason.length() > 255) {
            throw new IllegalArgumentException("Cancellation reason is required and at most 255 characters.");
        }
        return new Shift(id, number, date, storeId, employeeId, start, end,
                ShiftStatus.CANCELLED, actorEmployeeId, at, reason.trim());
    }

    private void requireActive() {
        if (status != ShiftStatus.ACTIVE) {
            throw new IllegalStateException("Only active shifts can be changed.");
        }
    }

    public Long id() { return id; }
    public String number() { return number; }
    public LocalDate date() { return date; }
    public long storeId() { return storeId; }
    public long employeeId() { return employeeId; }
    public LocalTime start() { return start; }
    public LocalTime end() { return end; }
    public ShiftStatus status() { return status; }
    public long changedByEmployeeId() { return changedByEmployeeId; }
    public LocalDateTime changedAt() { return changedAt; }
    public String cancellationReason() { return cancellationReason; }
}
