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
import com.bsr.repair.domain.RepairLabor;
import com.bsr.repair.domain.RepairRatePeriod;

@Entity(name = "RepairLaborRow")
@Table(name = "repair_labor")
public class RepairLaborRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "repair_labor_id", nullable = false)
    Long repairLaborId;

    @Column(name = "repair_task_id", nullable = false)
    Long repairTaskId;

    @Column(name = "repair_date", nullable = false)
    LocalDate repairDate;

    @Column(name = "duration_hours", nullable = false, precision = 8, scale = 2)
    BigDecimal durationHours;

    @Column(name = "handling_result", nullable = false, length = 500)
    String handlingResult;

    @Column(name = "note", nullable = true, length = 500)
    String note;

    @Column(name = "repair_rate_id", nullable = true)
    Long repairRateId;

    @Column(name = "hourly_rate_snapshot", nullable = true, precision = 12, scale = 2)
    BigDecimal hourlyRateSnapshot;

    @Column(name = "labor_amount", nullable = true, precision = 12, scale = 2)
    BigDecimal laborAmount;

    @Column(name = "submitted_at", nullable = true)
    LocalDateTime submittedAt;

    @Column(name = "contractor_id", nullable = false)
    Long contractorId;

    protected RepairLaborRow() {
    }

    RepairLaborRow(long taskId, RepairLabor labor) {
        this.repairTaskId = taskId;
        this.contractorId = labor.contractorId();
        this.repairDate = labor.repairDate();
        this.durationHours = labor.durationHours();
        this.handlingResult = labor.handlingResult();
        this.note = labor.note();
        this.repairRateId = labor.repairRateId();
        this.hourlyRateSnapshot = labor.hourlyRateSnapshot();
        this.laborAmount = labor.laborAmount();
    }

    RepairViews.Labor view() {
        return new RepairViews.Labor(repairLaborId, repairTaskId, contractorId,
                repairDate, durationHours, handlingResult, note, repairRateId,
                hourlyRateSnapshot, laborAmount, submittedAt);
    }

    RepairLabor domain() {
        return RepairLabor.restore(contractorId, repairDate, durationHours,
                handlingResult, note, repairRateId, hourlyRateSnapshot, laborAmount);
    }

    long taskId() {
        return repairTaskId;
    }

    void applyRate(long rateId, RepairRatePeriod period) {
        var labor = domain();
        if (!labor.applyRate(rateId, period)) {
            throw new IllegalStateException("Unrated labor is outside the new rate period.");
        }
        repairRateId = labor.repairRateId();
        hourlyRateSnapshot = labor.hourlyRateSnapshot();
        laborAmount = labor.laborAmount();
    }
}
