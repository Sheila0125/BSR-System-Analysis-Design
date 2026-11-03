package com.bsr.equipment.domain;

import java.time.LocalDateTime;

public record EquipmentInspection(
        long equipmentId,
        long inspectedByEmployeeId,
        Long rentalItemId,
        Long repairTaskId,
        InspectionType type,
        LocalDateTime inspectedAt,
        InspectionResult result,
        String faultDescription) {

    public EquipmentInspection {
        positiveId(equipmentId, "equipmentId");
        positiveId(inspectedByEmployeeId, "inspectedByEmployeeId");
        type = required(type, "type");
        inspectedAt = required(inspectedAt, "inspectedAt");
        result = required(result, "result");
        rentalItemId = optionalPositiveId(rentalItemId, "rentalItemId");
        repairTaskId = optionalPositiveId(repairTaskId, "repairTaskId");
        faultDescription = normalizeDescription(faultDescription);

        if (type == InspectionType.RETURN && (rentalItemId == null || repairTaskId != null)) {
            throw new IllegalArgumentException("return inspection requires only rentalItemId");
        }
        if (type == InspectionType.REPAIR_ACCEPTANCE && (repairTaskId == null || rentalItemId != null)) {
            throw new IllegalArgumentException("repair acceptance requires only repairTaskId");
        }
        if (type == InspectionType.REPAIR_ACCEPTANCE && result == InspectionResult.PENDING_REVIEW) {
            throw new IllegalArgumentException("repair acceptance result must be NORMAL or FAULT");
        }
    }

    private static long positiveId(long value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static Long optionalPositiveId(Long value, String field) {
        if (value != null) {
            positiveId(value, field);
        }
        return value;
    }

    private static String normalizeDescription(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 500) {
            throw new IllegalArgumentException("faultDescription must not exceed 500 characters");
        }
        return normalized;
    }

    private static <T> T required(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
