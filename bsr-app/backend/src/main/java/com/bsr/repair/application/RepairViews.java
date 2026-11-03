package com.bsr.repair.application;

import com.bsr.repair.domain.RepairAcceptanceResult;
import com.bsr.repair.domain.RepairTaskStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;

public final class RepairViews {

    private RepairViews() {
    }

    public record Task(
            long id,
            String number,
            long equipmentId,
            long serviceStoreId,
            Long currentContractorId,
            LocalDate requestDate,
            String faultDescription,
            RepairTaskStatus status,
            RepairAcceptanceResult acceptanceResult,
            LocalDateTime acceptedAt) {
    }

    public record Labor(long id, long taskId, long contractorId, LocalDate repairDate,
            BigDecimal durationHours, String handlingResult, String note,
            Long repairRateId, BigDecimal hourlyRateSnapshot, BigDecimal laborAmount,
            LocalDateTime submittedAt) {
    }

    public record Charge(long id, long taskId, BigDecimal totalHours,
            BigDecimal serviceAmount, String status, Long confirmedByEmployeeId,
            LocalDateTime confirmedAt) {
    }

    public record Contractor(long id, String number, String name, String contactName,
            String email, String phone, boolean active, String postalAddress) {
        public Contractor(long id, String number, String name, String contactName,
                String email, String phone, boolean active) {
            this(id, number, name, contactName, email, phone, active, null);
        }
    }

    public record Rate(long id, long contractorId, LocalDate effectiveFrom,
            LocalDate effectiveTo, BigDecimal hourlyRate) {
    }
}
