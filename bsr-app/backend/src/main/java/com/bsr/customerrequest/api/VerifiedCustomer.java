package com.bsr.customerrequest.api;

import java.math.BigDecimal;

public record VerifiedCustomer(
        long customerId,
        String customerNumber,
        String fullName,
        String email,
        String phone,
        BigDecimal heightCm,
        BigDecimal weightKg,
        BigDecimal bootSize,
        boolean heightEstimated,
        boolean weightEstimated,
        boolean bootSizeEstimated) {

}
