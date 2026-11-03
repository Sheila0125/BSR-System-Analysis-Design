package com.bsr.equipment.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class Equipment {

    private final long equipmentId;
    private final String inventoryNumber;
    private final String equipmentName;
    private final String equipmentCategory;
    private final String skillLevel;
    private final LocalDate purchaseDate;
    private final BigDecimal purchasePrice;
    private final BigDecimal currentDailyRate;

    private long homeStoreId;
    private long currentStoreId;
    private EquipmentStatus status;

    private Equipment(
            long equipmentId,
            String inventoryNumber,
            String equipmentName,
            String equipmentCategory,
            String skillLevel,
            long homeStoreId,
            long currentStoreId,
            EquipmentStatus status,
            LocalDate purchaseDate,
            BigDecimal purchasePrice,
            BigDecimal currentDailyRate) {
        this.equipmentId = positiveId(equipmentId, "equipmentId");
        this.inventoryNumber = requiredText(inventoryNumber, 40, "inventoryNumber");
        this.equipmentName = requiredText(equipmentName, 120, "equipmentName");
        this.equipmentCategory = requiredText(equipmentCategory, 40, "equipmentCategory");
        this.skillLevel = requiredText(skillLevel, 20, "skillLevel");
        this.homeStoreId = positiveId(homeStoreId, "homeStoreId");
        this.currentStoreId = positiveId(currentStoreId, "currentStoreId");
        this.status = required(status, "status");
        this.purchaseDate = purchaseDate;
        this.purchasePrice = nonNegative(purchasePrice, "purchasePrice");
        this.currentDailyRate = requiredNonNegative(currentDailyRate, "currentDailyRate");
    }

    public static Equipment restore(
            long equipmentId,
            String inventoryNumber,
            String equipmentName,
            String equipmentCategory,
            String skillLevel,
            long homeStoreId,
            long currentStoreId,
            EquipmentStatus status,
            LocalDate purchaseDate,
            BigDecimal purchasePrice,
            BigDecimal currentDailyRate) {
        return new Equipment(
                equipmentId,
                inventoryNumber,
                equipmentName,
                equipmentCategory,
                skillLevel,
                homeStoreId,
                currentStoreId,
                status,
                purchaseDate,
                purchasePrice,
                currentDailyRate);
    }

    public void reserveForIssue(long expectedStoreId) {
        requireCurrentStore(expectedStoreId);
        transition(EquipmentStatus.AVAILABLE, EquipmentStatus.PENDING_ISSUE);
    }

    public void releaseBeforeIssue(long expectedStoreId) {
        requireCurrentStore(expectedStoreId);
        transition(EquipmentStatus.PENDING_ISSUE, EquipmentStatus.AVAILABLE);
    }

    public void issue(long expectedStoreId) {
        requireCurrentStore(expectedStoreId);
        transition(EquipmentStatus.PENDING_ISSUE, EquipmentStatus.RENTED);
    }

    public void receiveReturn(long returnStoreId) {
        long validatedReturnStoreId = positiveId(returnStoreId, "returnStoreId");
        transition(EquipmentStatus.RENTED, EquipmentStatus.RETURN_INSPECTION);
        currentStoreId = validatedReturnStoreId;
    }

    public void applyReturnInspection(EquipmentInspection inspection) {
        EquipmentInspection validated = requireMatchingInspection(inspection, InspectionType.RETURN);
        if (status != EquipmentStatus.RETURN_INSPECTION) {
            throw illegalTransition(statusForReturnResult(validated.result()));
        }
        status = statusForReturnResult(validated.result());
    }

    public void startRepair() {
        transition(EquipmentStatus.WAITING_REPAIR, EquipmentStatus.IN_REPAIR);
    }

    public void submitRepair() {
        transition(EquipmentStatus.IN_REPAIR, EquipmentStatus.PENDING_ACCEPTANCE);
    }

    public void applyRepairAcceptance(EquipmentInspection inspection) {
        EquipmentInspection validated = requireMatchingInspection(inspection, InspectionType.REPAIR_ACCEPTANCE);
        EquipmentStatus target = validated.result() == InspectionResult.NORMAL
                ? EquipmentStatus.AVAILABLE
                : EquipmentStatus.WAITING_REPAIR;
        transition(EquipmentStatus.PENDING_ACCEPTANCE, target);
    }

    public void dispatchTransfer(long sourceStoreId, long destinationStoreId) {
        requireDistinctStores(sourceStoreId, destinationStoreId);
        requireCurrentStore(sourceStoreId);
        transition(EquipmentStatus.AVAILABLE, EquipmentStatus.IN_TRANSFER);
    }

    public void receiveTransfer(long sourceStoreId, long destinationStoreId, boolean permanent) {
        requireDistinctStores(sourceStoreId, destinationStoreId);
        requireCurrentStore(sourceStoreId);
        transition(EquipmentStatus.IN_TRANSFER, EquipmentStatus.AVAILABLE);
        currentStoreId = destinationStoreId;
        if (permanent) {
            homeStoreId = destinationStoreId;
        }
    }

    private EquipmentInspection requireMatchingInspection(
            EquipmentInspection inspection,
            InspectionType expectedType) {
        EquipmentInspection validated = required(inspection, "inspection");
        if (validated.equipmentId() != equipmentId) {
            throw new IllegalArgumentException("inspection equipment does not match");
        }
        if (validated.type() != expectedType) {
            throw new IllegalArgumentException("inspection type does not match action");
        }
        return validated;
    }

    private EquipmentStatus statusForReturnResult(InspectionResult result) {
        return switch (result) {
            case NORMAL -> EquipmentStatus.AVAILABLE;
            case FAULT -> EquipmentStatus.WAITING_REPAIR;
            case PENDING_REVIEW -> EquipmentStatus.PENDING_REVIEW;
        };
    }

    private void transition(EquipmentStatus expected, EquipmentStatus target) {
        if (status != expected) {
            throw illegalTransition(target);
        }
        status = target;
    }

    private IllegalStateException illegalTransition(EquipmentStatus target) {
        return new IllegalStateException("cannot transition equipment from " + status + " to " + target);
    }

    private void requireCurrentStore(long expectedStoreId) {
        long validatedStoreId = positiveId(expectedStoreId, "expectedStoreId");
        if (currentStoreId != validatedStoreId) {
            throw new IllegalStateException("equipment is not at expected store");
        }
    }

    private static void requireDistinctStores(long sourceStoreId, long destinationStoreId) {
        positiveId(sourceStoreId, "sourceStoreId");
        positiveId(destinationStoreId, "destinationStoreId");
        if (sourceStoreId == destinationStoreId) {
            throw new IllegalArgumentException("transfer stores must differ");
        }
    }

    private static long positiveId(long value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static String requiredText(String value, int maximumLength, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maximumLength + " characters");
        }
        return normalized;
    }

    private static BigDecimal requiredNonNegative(BigDecimal value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return nonNegative(value, field);
    }

    private static BigDecimal nonNegative(BigDecimal value, String field) {
        if (value != null && value.signum() < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return value;
    }

    private static <T> T required(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    public long equipmentId() {
        return equipmentId;
    }

    public String inventoryNumber() {
        return inventoryNumber;
    }

    public String equipmentName() {
        return equipmentName;
    }

    public String equipmentCategory() {
        return equipmentCategory;
    }

    public String skillLevel() {
        return skillLevel;
    }

    public long homeStoreId() {
        return homeStoreId;
    }

    public long currentStoreId() {
        return currentStoreId;
    }

    public EquipmentStatus status() {
        return status;
    }

    public LocalDate purchaseDate() {
        return purchaseDate;
    }

    public BigDecimal purchasePrice() {
        return purchasePrice;
    }

    public BigDecimal currentDailyRate() {
        return currentDailyRate;
    }
}
