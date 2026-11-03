package com.bsr.customerrequest.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class RentalRequest {

    private final String requestNumber;
    private final long customerId;
    private final long serviceStoreId;
    private final LocalDate rentalStartDate;
    private final int rentalDays;
    private final String skillLevel;
    private final LocalDateTime submittedAt;
    private final List<RentalRequestItem> items;

    private RentalRequestStatus status;
    private Long acceptedByEmployeeId;

    private RentalRequest(
            String requestNumber,
            long customerId,
            long serviceStoreId,
            LocalDate rentalStartDate,
            int rentalDays,
            String skillLevel,
            LocalDateTime submittedAt,
            List<RentalRequestItem> items) {
        this.requestNumber = requiredText(requestNumber, 30, "requestNumber");
        this.customerId = positiveId(customerId, "customerId");
        this.serviceStoreId = positiveId(serviceStoreId, "serviceStoreId");
        this.rentalStartDate = required(rentalStartDate, "rentalStartDate");
        if (rentalDays <= 0) {
            throw new IllegalArgumentException("rentalDays must be positive");
        }
        this.rentalDays = rentalDays;
        this.skillLevel = requiredText(skillLevel, 20, "skillLevel");
        this.submittedAt = required(submittedAt, "submittedAt");
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("at least one request item is required");
        }
        this.items = List.copyOf(items);
        if (this.items.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("request items must not contain null");
        }
        this.status = RentalRequestStatus.PENDING;
    }

    public static RentalRequest submit(
            String requestNumber,
            long customerId,
            long serviceStoreId,
            LocalDate rentalStartDate,
            int rentalDays,
            String skillLevel,
            LocalDateTime submittedAt,
            List<RentalRequestItem> items) {
        return new RentalRequest(
                requestNumber,
                customerId,
                serviceStoreId,
                rentalStartDate,
                rentalDays,
                skillLevel,
                submittedAt,
                items);
    }

    public static RentalRequest restore(
            String requestNumber,
            long customerId,
            long serviceStoreId,
            LocalDate rentalStartDate,
            int rentalDays,
            String skillLevel,
            LocalDateTime submittedAt,
            List<RentalRequestItem> items,
            RentalRequestStatus status,
            Long acceptedByEmployeeId) {
        RentalRequest request = new RentalRequest(
                requestNumber,
                customerId,
                serviceStoreId,
                rentalStartDate,
                rentalDays,
                skillLevel,
                submittedAt,
                items);
        request.status = required(status, "status");
        request.acceptedByEmployeeId = acceptedByEmployeeId == null
                ? null
                : positiveId(acceptedByEmployeeId, "acceptedByEmployeeId");
        return request;
    }

    public void requestMoreInformation() {
        transition(RentalRequestStatus.PENDING, RentalRequestStatus.NEEDS_INFO);
    }

    public void resubmitSupplementedInformation() {
        transition(RentalRequestStatus.NEEDS_INFO, RentalRequestStatus.PENDING);
    }

    public void accept(long employeeId) {
        long validatedEmployeeId = positiveId(employeeId, "employeeId");
        transition(RentalRequestStatus.PENDING, RentalRequestStatus.ACCEPTED);
        acceptedByEmployeeId = validatedEmployeeId;
    }

    public void markUnavailable() {
        if (status != RentalRequestStatus.PENDING && status != RentalRequestStatus.ACCEPTED) {
            throw illegalTransition(RentalRequestStatus.UNAVAILABLE);
        }
        status = RentalRequestStatus.UNAVAILABLE;
    }

    public void markConverted() {
        transition(RentalRequestStatus.ACCEPTED, RentalRequestStatus.CONVERTED);
    }

    private void transition(RentalRequestStatus expected, RentalRequestStatus target) {
        if (status != expected) {
            throw illegalTransition(target);
        }
        status = target;
    }

    private IllegalStateException illegalTransition(RentalRequestStatus target) {
        return new IllegalStateException("cannot transition rental request from " + status + " to " + target);
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

    private static <T> T required(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    public String requestNumber() {
        return requestNumber;
    }

    public long customerId() {
        return customerId;
    }

    public long serviceStoreId() {
        return serviceStoreId;
    }

    public LocalDate rentalStartDate() {
        return rentalStartDate;
    }

    public int rentalDays() {
        return rentalDays;
    }

    public String skillLevel() {
        return skillLevel;
    }

    public LocalDateTime submittedAt() {
        return submittedAt;
    }

    public List<RentalRequestItem> items() {
        return items;
    }

    public RentalRequestStatus status() {
        return status;
    }

    public Long acceptedByEmployeeId() {
        return acceptedByEmployeeId;
    }
}
