package com.bsr.customerrequest.domain;

public record RentalRequestItem(
        String equipmentCategory,
        int quantity,
        String fittingNote) {

    public RentalRequestItem {
        equipmentCategory = requiredText(equipmentCategory, 40, "equipmentCategory");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (fittingNote != null && fittingNote.length() > 255) {
            throw new IllegalArgumentException("fittingNote must not exceed 255 characters");
        }
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
}
