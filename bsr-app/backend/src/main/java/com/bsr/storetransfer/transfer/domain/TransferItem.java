package com.bsr.storetransfer.transfer.domain;

import java.time.LocalDateTime;
import java.util.Objects;

public final class TransferItem {
    public enum Phase { DISPATCH, RECEIPT }
    public enum DispatchResult { PENDING, DISPATCHED, EXCEPTION }
    public enum ReceiptResult { PENDING, RECEIVED, EXCEPTION }

    private final long equipmentId;
    private DispatchResult dispatchResult;
    private Long dispatchedBy;
    private LocalDateTime dispatchedAt;
    private ReceiptResult receiptResult;
    private Long receivedBy;
    private LocalDateTime receivedAt;
    private String exceptionNote;

    private TransferItem(long equipmentId, DispatchResult dispatchResult, Long dispatchedBy,
            LocalDateTime dispatchedAt, ReceiptResult receiptResult, Long receivedBy,
            LocalDateTime receivedAt, String exceptionNote) {
        if (equipmentId <= 0) {
            throw new IllegalArgumentException("equipmentId must be positive");
        }
        this.equipmentId = equipmentId;
        this.dispatchResult = Objects.requireNonNull(dispatchResult);
        this.dispatchedBy = dispatchedBy;
        this.dispatchedAt = dispatchedAt;
        this.receiptResult = Objects.requireNonNull(receiptResult);
        this.receivedBy = receivedBy;
        this.receivedAt = receivedAt;
        this.exceptionNote = exceptionNote;
        if ((dispatchedBy == null) != (dispatchedAt == null)
                || (receivedBy == null) != (receivedAt == null)
                || (dispatchResult == DispatchResult.PENDING) != (dispatchedBy == null)
                || (receiptResult == ReceiptResult.PENDING) != (receivedBy == null)
                || (receiptResult == ReceiptResult.RECEIVED && dispatchResult != DispatchResult.DISPATCHED)
                || (receiptResult != ReceiptResult.PENDING && dispatchResult != DispatchResult.DISPATCHED)
                || ((dispatchResult == DispatchResult.EXCEPTION || receiptResult == ReceiptResult.EXCEPTION)
                    && (exceptionNote == null || exceptionNote.isBlank()))) {
            throw new IllegalArgumentException("inconsistent item handoff facts");
        }
    }

    public static TransferItem pending(long equipmentId) {
        return new TransferItem(equipmentId, DispatchResult.PENDING, null, null,
                ReceiptResult.PENDING, null, null, null);
    }

    public static TransferItem restore(long equipmentId, DispatchResult dispatchResult,
            Long dispatchedBy, LocalDateTime dispatchedAt, ReceiptResult receiptResult,
            Long receivedBy, LocalDateTime receivedAt, String exceptionNote) {
        return new TransferItem(equipmentId, dispatchResult, dispatchedBy, dispatchedAt,
                receiptResult, receivedBy, receivedAt, exceptionNote);
    }

    public boolean dispatch(long actorId, LocalDateTime when) {
        requireActorTime(actorId, when);
        if (dispatchResult == DispatchResult.DISPATCHED) {
            return false;
        }
        if (receiptResult != ReceiptResult.PENDING) {
            throw new IllegalStateException("receipt phase already started");
        }
        dispatchResult = DispatchResult.DISPATCHED;
        dispatchedBy = actorId;
        dispatchedAt = when;
        return true;
    }

    public boolean receive(long actorId, LocalDateTime when) {
        requireActorTime(actorId, when);
        if (receiptResult == ReceiptResult.RECEIVED) {
            return false;
        }
        if (dispatchResult != DispatchResult.DISPATCHED) {
            throw new IllegalStateException("item was not dispatched");
        }
        receiptResult = ReceiptResult.RECEIVED;
        receivedBy = actorId;
        receivedAt = when;
        return true;
    }

    public void reportException(Phase phase, String note, long actorId, LocalDateTime when) {
        Objects.requireNonNull(phase);
        requireActorTime(actorId, when);
        if (note == null || note.isBlank() || note.length() > 500) {
            throw new IllegalArgumentException("exception note must have 1-500 characters");
        }
        if (phase == Phase.DISPATCH) {
            if (dispatchResult == DispatchResult.DISPATCHED) {
                throw new IllegalStateException("item was dispatched");
            }
            dispatchResult = DispatchResult.EXCEPTION;
            dispatchedBy = actorId;
            dispatchedAt = when;
        } else {
            if (dispatchResult != DispatchResult.DISPATCHED || receiptResult == ReceiptResult.RECEIVED) {
                throw new IllegalStateException("receipt exception is not allowed");
            }
            receiptResult = ReceiptResult.EXCEPTION;
            receivedBy = actorId;
            receivedAt = when;
        }
        exceptionNote = note.trim();
    }

    private static void requireActorTime(long actorId, LocalDateTime when) {
        if (actorId <= 0 || when == null) {
            throw new IllegalArgumentException("actor and time are required");
        }
    }

    public long equipmentId() { return equipmentId; }
    public DispatchResult dispatchResult() { return dispatchResult; }
    public Long dispatchedBy() { return dispatchedBy; }
    public LocalDateTime dispatchedAt() { return dispatchedAt; }
    public ReceiptResult receiptResult() { return receiptResult; }
    public Long receivedBy() { return receivedBy; }
    public LocalDateTime receivedAt() { return receivedAt; }
    public String exceptionNote() { return exceptionNote; }
}
