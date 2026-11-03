package com.bsr.storetransfer.transfer.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public final class TransferAggregate {
    private final String number;
    private final TransferType type;
    private final long sourceStoreId;
    private final long destinationStoreId;
    private final long initiatedBy;
    private final LocalDateTime initiatedAt;
    private final List<TransferItem> items;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime completedAt;

    private TransferAggregate(String number, TransferType type, long sourceStoreId,
            long destinationStoreId, long initiatedBy, LocalDateTime initiatedAt,
            List<TransferItem> items, Long approvedBy, LocalDateTime approvedAt,
            LocalDateTime completedAt) {
        if (number == null || number.isBlank() || number.length() > 30 || type == null
                || sourceStoreId <= 0 || destinationStoreId <= 0 || sourceStoreId == destinationStoreId
                || initiatedBy <= 0 || initiatedAt == null || items == null || items.isEmpty()) {
            throw new IllegalArgumentException("invalid transfer header");
        }
        if (items.stream().anyMatch(Objects::isNull)
                || items.stream().map(TransferItem::equipmentId).distinct().count() != items.size()
                || (approvedBy == null) != (approvedAt == null)
                || (approvedBy != null && approvedBy <= 0)) {
            throw new IllegalArgumentException("invalid transfer facts");
        }
        this.number = number;
        this.type = type;
        this.sourceStoreId = sourceStoreId;
        this.destinationStoreId = destinationStoreId;
        this.initiatedBy = initiatedBy;
        this.initiatedAt = initiatedAt;
        this.items = new ArrayList<>(items);
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
        this.completedAt = completedAt;
        if (completedAt != null && status() != TransferStatus.COMPLETED) {
            throw new IllegalArgumentException("completion time without full receipt");
        }
    }

    public static TransferAggregate initiate(String number, TransferType type, long sourceStoreId,
            long destinationStoreId, long initiatedBy, LocalDateTime initiatedAt,
            List<Long> equipmentIds) {
        if (equipmentIds == null || equipmentIds.isEmpty()
                || equipmentIds.stream().anyMatch(Objects::isNull)
                || new HashSet<>(equipmentIds).size() != equipmentIds.size()) {
            throw new IllegalArgumentException("equipment IDs must be nonempty and unique");
        }
        return new TransferAggregate(number, type, sourceStoreId, destinationStoreId,
                initiatedBy, initiatedAt, equipmentIds.stream().map(TransferItem::pending).toList(),
                null, null, null);
    }

    public static TransferAggregate restore(String number, TransferType type, long sourceStoreId,
            long destinationStoreId, long initiatedBy, LocalDateTime initiatedAt,
            List<TransferItem> items, Long approvedBy, LocalDateTime approvedAt,
            LocalDateTime completedAt) {
        return new TransferAggregate(number, type, sourceStoreId, destinationStoreId,
                initiatedBy, initiatedAt, items, approvedBy, approvedAt, completedAt);
    }

    public boolean approve(long actorId, LocalDateTime when) {
        requireActorTime(actorId, when);
        if (approvedBy != null) {
            return false;
        }
        approvedBy = actorId;
        approvedAt = when;
        return true;
    }

    public boolean dispatch(long equipmentId, long actorId, LocalDateTime when) {
        requireApproved();
        if (status() == TransferStatus.COMPLETED) {
            throw new IllegalStateException("transfer is completed");
        }
        return item(equipmentId).dispatch(actorId, when);
    }

    public boolean receive(long equipmentId, long actorId, LocalDateTime when) {
        requireApproved();
        TransferItem item = item(equipmentId);
        if (!item.receive(actorId, when)) {
            return false;
        }
        if (status() == TransferStatus.COMPLETED) {
            completedAt = when;
        }
        return true;
    }

    public void reportException(long equipmentId, TransferItem.Phase phase, String note,
            long actorId, LocalDateTime when) {
        requireApproved();
        item(equipmentId).reportException(phase, note, actorId, when);
    }

    public TransferStatus status() {
        if (approvedBy == null) {
            return TransferStatus.INITIATED;
        }
        long received = items.stream().filter(i -> i.receiptResult() == TransferItem.ReceiptResult.RECEIVED).count();
        if (received == items.size()) {
            return TransferStatus.COMPLETED;
        }
        if (received > 0) {
            return TransferStatus.PARTIALLY_RECEIVED;
        }
        boolean dispatched = items.stream().anyMatch(i -> i.dispatchResult() == TransferItem.DispatchResult.DISPATCHED);
        return dispatched ? TransferStatus.IN_TRANSIT : TransferStatus.APPROVED;
    }

    private TransferItem item(long equipmentId) {
        return items.stream().filter(i -> i.equipmentId() == equipmentId).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("equipment is not in transfer"));
    }

    private void requireApproved() {
        if (approvedBy == null) {
            throw new IllegalStateException("transfer has not been approved");
        }
    }

    private static void requireActorTime(long actorId, LocalDateTime when) {
        if (actorId <= 0 || when == null) {
            throw new IllegalArgumentException("actor and time are required");
        }
    }

    public String number() { return number; }
    public TransferType type() { return type; }
    public long sourceStoreId() { return sourceStoreId; }
    public long destinationStoreId() { return destinationStoreId; }
    public long initiatedBy() { return initiatedBy; }
    public LocalDateTime initiatedAt() { return initiatedAt; }
    public List<TransferItem> items() { return List.copyOf(items); }
    public Long approvedBy() { return approvedBy; }
    public LocalDateTime approvedAt() { return approvedAt; }
    public LocalDateTime completedAt() { return completedAt; }
}
