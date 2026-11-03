package com.bsr.rental.domain;

public enum RentalContractStatus {
    DRAFT,
    PENDING_CONFIRMATION,
    PENDING_PAYMENT,
    PENDING_ISSUE,
    ACTIVE,
    PENDING_SETTLEMENT,
    ENDED,
    CANCELLED
}
