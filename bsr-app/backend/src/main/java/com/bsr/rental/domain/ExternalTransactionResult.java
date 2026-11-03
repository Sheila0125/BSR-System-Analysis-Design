package com.bsr.rental.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Immutable external fact; employees register results, never initiate payment here. */
public record ExternalTransactionResult(long chargeId, Type type, String externalId,
        BigDecimal amount, Status status, LocalDateTime processedAt) {
    public enum Type { PAYMENT, SUPPLEMENT, REFUND }
    public enum Status { PENDING_CONFIRMATION, SUCCEEDED, FAILED }
    public ExternalTransactionResult {
        if (chargeId <= 0 || type == null || status == null || externalId == null
                || externalId.isBlank() || externalId.length() > 100 || !externalId.equals(externalId.trim()))
            throw new IllegalArgumentException("Valid charge, type, status and exact external identifier are required");
        amount = RentalItem.money(amount, "amount");
        if (amount.signum() <= 0) throw new IllegalArgumentException("Transaction amount must be positive");
        if (status == Status.SUCCEEDED && processedAt == null)
            throw new IllegalArgumentException("Successful results require the third-party processing time");
        if (processedAt != null && processedAt.getNano() != 0)
            throw new IllegalArgumentException("Third-party time must have whole-second precision");
    }
    public boolean sameBusinessContent(ExternalTransactionResult other) {
        return chargeId == other.chargeId && type == other.type && externalId.equals(other.externalId)
                && amount.compareTo(other.amount) == 0 && status == other.status
                && java.util.Objects.equals(processedAt, other.processedAt);
    }
    public void requireMatchesCharge(String chargeType, BigDecimal unsettledAmount) {
        Type expected = switch (chargeType) {
            case "INITIAL" -> Type.PAYMENT;
            case "SUPPLEMENT" -> Type.SUPPLEMENT;
            case "REFUND" -> Type.REFUND;
            default -> throw new IllegalArgumentException("Unknown charge type");
        };
        if (type != expected || amount.compareTo(unsettledAmount) > 0)
            throw new IllegalStateException("Transaction type/amount does not match outstanding business charge");
    }
}
