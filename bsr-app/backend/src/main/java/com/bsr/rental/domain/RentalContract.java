package com.bsr.rental.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RentalContract {

    private final String contractNumber;
    private final long sourceRequestId;
    private final long customerId;
    private final long createdByEmployeeId;
    private final long serviceStoreId;
    private final LocalDate contractDate;
    private final LocalDate rentalStartDate;
    private final int rentalDays;
    private final List<RentalItem> items;
    private BigDecimal amountDue;

    private RentalContractStatus status;

    private RentalContract(
            String contractNumber,
            long sourceRequestId,
            long customerId,
            long createdByEmployeeId,
            long serviceStoreId,
            LocalDate contractDate,
            LocalDate rentalStartDate,
            int rentalDays,
            List<RentalItem> items, BigDecimal persistedAmount) {
        this.contractNumber = requiredText(contractNumber, 30, "contractNumber");
        this.sourceRequestId = positiveId(sourceRequestId, "sourceRequestId");
        this.customerId = positiveId(customerId, "customerId");
        this.createdByEmployeeId = positiveId(createdByEmployeeId, "createdByEmployeeId");
        this.serviceStoreId = positiveId(serviceStoreId, "serviceStoreId");
        this.contractDate = required(contractDate, "contractDate");
        this.rentalStartDate = required(rentalStartDate, "rentalStartDate");
        if (rentalDays <= 0) {
            throw new IllegalArgumentException("rentalDays must be positive");
        }
        this.rentalDays = rentalDays;
        this.items = immutableUniqueItems(items);
        this.amountDue = persistedAmount==null ? WholeDayChargeCalculator.initialAmount(this.items, rentalDays)
                : RentalItem.money(persistedAmount,"amountDue");
        this.status = RentalContractStatus.DRAFT;
    }

    public static RentalContract createDraft(
            String contractNumber,
            long sourceRequestId,
            long customerId,
            long createdByEmployeeId,
            long serviceStoreId,
            LocalDate contractDate,
            LocalDate rentalStartDate,
            int rentalDays,
            List<RentalItem> items) {
        return new RentalContract(
                contractNumber,
                sourceRequestId,
                customerId,
                createdByEmployeeId,
                serviceStoreId,
                contractDate,
                rentalStartDate,
                rentalDays,
                items,null);
    }

    public void submitForCustomerConfirmation() {
        transition(RentalContractStatus.DRAFT, RentalContractStatus.PENDING_CONFIRMATION);
    }

    public static RentalContract restore(String number, long requestId, long customerId,
            long employeeId, long storeId, LocalDate contractDate, LocalDate startDate,
            int days, List<RentalItem> items, RentalContractStatus status) {
        RentalContract contract = createDraft(number, requestId, customerId, employeeId,
                storeId, contractDate, startDate, days, items);
        contract.status = required(status, "status");
        return contract;
    }

    public static RentalContract restore(String number, long requestId, long customerId,
            long employeeId, long storeId, LocalDate contractDate, LocalDate startDate,
            int days, List<RentalItem> items, RentalContractStatus status, BigDecimal persistedAmount) {
        var contract=new RentalContract(number,requestId,customerId,employeeId,storeId,contractDate,startDate,days,items,required(persistedAmount,"amountDue"));
        contract.status=required(status,"status");
        return contract;
    }

    public void confirmAdjustment(BigDecimal delta) {
        if(status!=RentalContractStatus.ACTIVE && status!=RentalContractStatus.PENDING_SETTLEMENT)
            throw new IllegalStateException("Only a current rental may confirm an adjustment");
        if(delta==null) throw new IllegalArgumentException("Adjustment delta is required");
        amountDue=RentalItem.money(amountDue.add(delta),"amountDue");
    }

    public void keepPendingAdjustment() {
        if(status==RentalContractStatus.ENDED) status=RentalContractStatus.PENDING_SETTLEMENT;
    }

    public void resumeWithPendingEquipment() {
        if(status==RentalContractStatus.PENDING_SETTLEMENT) status=RentalContractStatus.ACTIVE;
    }

    public void reviseDraftAmount(BigDecimal amount) {
        if(status!=RentalContractStatus.DRAFT) throw new IllegalStateException("Only a draft may be revised");
        amountDue=RentalItem.money(amount,"amountDue");
    }

    public void recognizeSuccessfulInitialPayment() {
        transition(RentalContractStatus.PENDING_PAYMENT, RentalContractStatus.PENDING_ISSUE);
    }

    public void recordPhysicalIssue() {
        if (status != RentalContractStatus.PENDING_ISSUE && status != RentalContractStatus.ACTIVE) {
            throw new IllegalStateException("only paid contracts may issue equipment");
        }
        status = RentalContractStatus.ACTIVE;
    }

    public void receivePhysicalReturn(long equipmentId, boolean hasUnsettledCharges) {
        if (status != RentalContractStatus.ACTIVE) throw new IllegalStateException("Only active contracts accept returns");
        items.stream().filter(item -> item.equipmentId()==equipmentId).findFirst().orElseThrow(
                () -> new IllegalArgumentException("Equipment is not in this contract")).receivePhysicalReturn();
        resolveReturnedContract(hasUnsettledCharges);
    }
    public void resolveReturnedContract(boolean hasUnsettledCharges) {
        if (status == RentalContractStatus.CANCELLED) return;
        if (items.stream().allMatch(item -> item.status() == RentalItemStatus.RETURNED))
            status = hasUnsettledCharges ? RentalContractStatus.PENDING_SETTLEMENT : RentalContractStatus.ENDED;
    }

    public void cancelBeforeIssue() {
        if (status == RentalContractStatus.CANCELLED || status == RentalContractStatus.ENDED
                || items.stream().anyMatch(item -> item.status() != RentalItemStatus.PENDING_ISSUE)) {
            throw new IllegalStateException("only an unissued contract may be cancelled");
        }
        status = RentalContractStatus.CANCELLED;
    }

    public void rejectByCustomer() {
        transition(RentalContractStatus.PENDING_CONFIRMATION, RentalContractStatus.DRAFT);
    }

    public void confirmByCustomer() {
        transition(RentalContractStatus.PENDING_CONFIRMATION,
                amountDue.signum()==0 ? RentalContractStatus.PENDING_ISSUE : RentalContractStatus.PENDING_PAYMENT);
    }

    private void transition(RentalContractStatus expected, RentalContractStatus target) {
        if (status != expected) {
            throw new IllegalStateException("cannot transition rental contract from " + status + " to " + target);
        }
        status = target;
    }

    private static List<RentalItem> immutableUniqueItems(List<RentalItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("at least one rental item is required");
        }
        Set<Long> equipmentIds = new HashSet<>();
        for (RentalItem item : items) {
            if (item == null) {
                throw new IllegalArgumentException("rental items must not contain null");
            }
            if (!equipmentIds.add(item.equipmentId())) {
                throw new IllegalArgumentException("equipment IDs must be unique within a contract");
            }
        }
        return List.copyOf(items);
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

    public String contractNumber() {
        return contractNumber;
    }

    public long sourceRequestId() {
        return sourceRequestId;
    }

    public long customerId() {
        return customerId;
    }

    public long createdByEmployeeId() {
        return createdByEmployeeId;
    }

    public long serviceStoreId() {
        return serviceStoreId;
    }

    public LocalDate contractDate() {
        return contractDate;
    }

    public LocalDate rentalStartDate() {
        return rentalStartDate;
    }

    public LocalDate rentalEndExclusive() {
        return rentalStartDate.plusDays(rentalDays);
    }

    public int rentalDays() {
        return rentalDays;
    }

    public List<RentalItem> items() {
        return items;
    }

    public BigDecimal amountDue() {
        return amountDue;
    }

    public RentalContractStatus status() {
        return status;
    }
}
