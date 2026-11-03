package com.bsr.repair.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.bsr.repair.application.RepairViews;
import com.bsr.repair.domain.RepairRatePeriod;

@Entity(name = "RepairRateRow")
@Table(name = "repair_rate")
public class RepairRateRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "repair_rate_id", nullable = false)
    Long repairRateId;

    @Column(name = "contractor_id", nullable = false)
    Long contractorId;

    @Column(name = "hourly_rate", nullable = false, precision = 12, scale = 2)
    BigDecimal hourlyRate;

    @Column(name = "effective_from", nullable = false)
    LocalDate effectiveFrom;

    @Column(name = "effective_to", nullable = true)
    LocalDate effectiveTo;

    @Column(name = "created_at", nullable = false)
    LocalDateTime createdAt;

    protected RepairRateRow() {
    }

    RepairRateRow(RepairRatePeriod period, LocalDateTime createdAt) {
        contractorId = period.contractorId();
        hourlyRate = period.hourlyRate();
        effectiveFrom = period.effectiveFrom();
        effectiveTo = period.effectiveTo();
        this.createdAt = createdAt;
    }

    RepairViews.Rate view() {
        return new RepairViews.Rate(repairRateId, contractorId,
                effectiveFrom, effectiveTo, hourlyRate);
    }
}
