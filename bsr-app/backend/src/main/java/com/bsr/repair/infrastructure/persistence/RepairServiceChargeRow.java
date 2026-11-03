package com.bsr.repair.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.bsr.repair.application.RepairViews;
import com.bsr.repair.domain.RepairServiceCharge;

@Entity(name = "RepairServiceChargeRow")
@Table(name = "repair_service_charge")
public class RepairServiceChargeRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "repair_service_charge_id", nullable = false)
    Long repairServiceChargeId;

    @Column(name = "repair_task_id", nullable = false)
    Long repairTaskId;

    @Column(name = "total_hours", nullable = false, precision = 8, scale = 2)
    BigDecimal totalHours;

    @Column(name = "service_amount", nullable = false, precision = 12, scale = 2)
    BigDecimal serviceAmount;

    @Column(name = "charge_status", nullable = false, length = 20)
    String chargeStatus;

    @Column(name = "confirmed_by_employee_id", nullable = true)
    Long confirmedByEmployeeId;

    @Column(name = "confirmed_at", nullable = true)
    LocalDateTime confirmedAt;

    protected RepairServiceChargeRow() {
    }

    RepairServiceChargeRow(long taskId, RepairServiceCharge charge) {
        this.repairTaskId = taskId;
        this.totalHours = charge.totalHours();
        this.serviceAmount = charge.serviceAmount();
        this.chargeStatus = "PENDING_CONFIRMATION";
    }

    void confirm(long employeeId, LocalDateTime time) {
        if (!"PENDING_CONFIRMATION".equals(chargeStatus)) {
            throw new IllegalStateException("Repair service charge is not pending confirmation.");
        }
        chargeStatus = "CONFIRMED";
        confirmedByEmployeeId = employeeId;
        confirmedAt = time;
    }

    RepairViews.Charge view() {
        return new RepairViews.Charge(repairServiceChargeId, repairTaskId,
                totalHours, serviceAmount, chargeStatus, confirmedByEmployeeId, confirmedAt);
    }
}
