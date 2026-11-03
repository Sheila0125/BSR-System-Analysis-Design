package com.bsr.customerrequest.domain;

import java.math.BigDecimal;

public record CustomerProfile(
        long customerId,
        String customerNumber,
        String fullName,
        String email,
        String phone,
        String address,
        Short birthYear,
        BigDecimal heightCm,
        BigDecimal weightKg,
        BigDecimal bootSize,
        boolean heightEstimated,
        boolean weightEstimated,
        boolean bootSizeEstimated) {

    public CustomerProfile {
        if (customerId <= 0) {
            throw new IllegalArgumentException("customerId must be positive");
        }
        customerNumber = requiredText(customerNumber, "customerNumber");
        fullName = requiredText(fullName, "fullName");
        email = requiredText(email, "email");
        phone = requiredText(phone, "phone");
        requirePositive(heightCm, "heightCm");
        requirePositive(weightKg, "weightKg");
        requirePositive(bootSize, "bootSize");
        requireMeasuredValue(heightCm, heightEstimated, "heightEstimated");
        requireMeasuredValue(weightKg, weightEstimated, "weightEstimated");
        requireMeasuredValue(bootSize, bootSizeEstimated, "bootSizeEstimated");
    }

    public CustomerProfile withMeasurements(
            BigDecimal newHeightCm,
            boolean newHeightEstimated,
            BigDecimal newWeightKg,
            boolean newWeightEstimated,
            BigDecimal newBootSize,
            boolean newBootSizeEstimated) {
        return new CustomerProfile(
                customerId,
                customerNumber,
                fullName,
                email,
                phone,
                address,
                birthYear,
                newHeightCm,
                newWeightKg,
                newBootSize,
                newHeightEstimated,
                newWeightEstimated,
                newBootSizeEstimated);
    }

    public CustomerProfile withContactDetails(String newFullName, String newEmail, String newPhone,
            String newAddress, Short newBirthYear) {
        return new CustomerProfile(customerId, customerNumber, newFullName, newEmail, newPhone,
                newAddress, newBirthYear, heightCm, weightKg, bootSize,
                heightEstimated, weightEstimated, bootSizeEstimated);
    }

    private static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static void requirePositive(BigDecimal value, String field) {
        if (value != null && value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private static void requireMeasuredValue(BigDecimal value, boolean estimated, String field) {
        if (estimated && value == null) {
            throw new IllegalArgumentException(field + " requires a measurement value");
        }
    }
}
