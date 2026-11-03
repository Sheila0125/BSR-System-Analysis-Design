package com.bsr.repair.domain;

import java.util.Objects;

/** State changes for one repair task; callers persist each action with its audit record. */
public final class RepairTask {

    private RepairTaskStatus status;
    private Long currentContractorId;
    private RepairAcceptanceResult acceptanceResult;

    private RepairTask(RepairTaskStatus status, Long currentContractorId,
            RepairAcceptanceResult acceptanceResult) {
        this.status = Objects.requireNonNull(status, "status");
        if (currentContractorId != null) {
            positive(currentContractorId, "contractorId");
        }
        this.currentContractorId = currentContractorId;
        this.acceptanceResult = acceptanceResult;
    }

    public static RepairTask create(Long contractorId) {
        return new RepairTask(contractorId == null ? RepairTaskStatus.UNASSIGNED : RepairTaskStatus.PENDING,
                contractorId, null);
    }

    public static RepairTask restore(RepairTaskStatus status, Long contractorId,
            RepairAcceptanceResult acceptanceResult) {
        if ((status == RepairTaskStatus.UNASSIGNED) != (contractorId == null)) {
            throw new IllegalStateException("Stored repair task contractor does not match its state.");
        }
        if ((status == RepairTaskStatus.RETURNED && acceptanceResult != RepairAcceptanceResult.FAILED)
                || ((status == RepairTaskStatus.PENDING_CHARGE || status == RepairTaskStatus.COMPLETED)
                && acceptanceResult != RepairAcceptanceResult.PASSED)) {
            throw new IllegalStateException("Stored repair task acceptance does not match its state.");
        }
        return new RepairTask(status, contractorId, acceptanceResult);
    }

    public RepairTaskStatus status() {
        return status;
    }

    public Long currentContractorId() {
        return currentContractorId;
    }

    public RepairAcceptanceResult acceptanceResult() {
        return acceptanceResult;
    }

    public void assign(long contractorId) {
        positive(contractorId, "contractorId");
        requireState(RepairTaskStatus.UNASSIGNED, RepairTaskStatus.PENDING,
                RepairTaskStatus.IN_PROGRESS, RepairTaskStatus.RETURNED);
        boolean alreadyInProgress = status == RepairTaskStatus.IN_PROGRESS;
        currentContractorId = contractorId;
        acceptanceResult = null;
        status = alreadyInProgress ? RepairTaskStatus.IN_PROGRESS : RepairTaskStatus.PENDING;
    }

    public void start(long contractorId) {
        requireOwner(contractorId);
        requireState(RepairTaskStatus.PENDING, RepairTaskStatus.RETURNED);
        acceptanceResult = null;
        status = RepairTaskStatus.IN_PROGRESS;
    }

    public void submit(long contractorId, int laborCount) {
        requireOwner(contractorId);
        requireState(RepairTaskStatus.IN_PROGRESS);
        if (laborCount < 1) {
            throw new IllegalStateException("At least one complete labor entry is required.");
        }
        status = RepairTaskStatus.PENDING_ACCEPTANCE;
    }

    public void reject(String reason) {
        requireState(RepairTaskStatus.PENDING_ACCEPTANCE);
        if (reason == null || reason.isBlank() || reason.trim().length() > 500) {
            throw new IllegalArgumentException("A repair rejection reason of 1–500 characters is required.");
        }
        acceptanceResult = RepairAcceptanceResult.FAILED;
        status = RepairTaskStatus.RETURNED;
    }

    public void accept() {
        requireState(RepairTaskStatus.PENDING_ACCEPTANCE);
        acceptanceResult = RepairAcceptanceResult.PASSED;
        status = RepairTaskStatus.PENDING_CHARGE;
    }

    public void complete(boolean serviceChargeConfirmed) {
        requireState(RepairTaskStatus.PENDING_CHARGE);
        if (!serviceChargeConfirmed) {
            throw new IllegalStateException("A confirmed service charge is required.");
        }
        status = RepairTaskStatus.COMPLETED;
    }

    private void requireOwner(long contractorId) {
        positive(contractorId, "contractorId");
        if (currentContractorId == null || currentContractorId != contractorId) {
            throw new IllegalStateException("Only the currently assigned contractor may process this task.");
        }
    }

    private void requireState(RepairTaskStatus... allowed) {
        for (RepairTaskStatus candidate : allowed) {
            if (status == candidate) {
                return;
            }
        }
        throw new IllegalStateException("Repair task is not in a state that allows this action.");
    }

    private static void positive(long id, String field) {
        if (id <= 0) {
            throw new IllegalArgumentException(field + " must be positive.");
        }
    }
}
