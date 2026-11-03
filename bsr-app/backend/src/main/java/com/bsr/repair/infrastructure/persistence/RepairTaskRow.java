package com.bsr.repair.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.bsr.repair.domain.RepairTask;
import com.bsr.repair.domain.RepairAcceptanceResult;
import com.bsr.repair.domain.RepairTaskStatus;
import com.bsr.repair.application.RepairPersistence;

@Entity(name = "RepairTaskRow")
@Table(name = "repair_task")
public class RepairTaskRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "repair_task_id", nullable = false)
    Long repairTaskId;

    @Column(name = "repair_task_number", nullable = false, length = 30)
    String repairTaskNumber;

    @Column(name = "equipment_id", nullable = false)
    Long equipmentId;

    @Column(name = "requested_by_employee_id", nullable = false)
    Long requestedByEmployeeId;

    @Column(name = "service_store_id", nullable = false)
    Long serviceStoreId;

    @Column(name = "current_contractor_id", nullable = true)
    Long currentContractorId;

    @Column(name = "request_date", nullable = false)
    LocalDate requestDate;

    @Column(name = "fault_description", nullable = false, length = 500)
    String faultDescription;

    @Column(name = "handling_requirement", nullable = true, length = 500)
    String handlingRequirement;

    @Column(name = "task_status", nullable = false, length = 30)
    String taskStatus;

    @Column(name = "acceptance_result", nullable = true, length = 20)
    String acceptanceResult;

    @Column(name = "accepted_by_employee_id", nullable = true)
    Long acceptedByEmployeeId;

    @Column(name = "accepted_at", nullable = true)
    LocalDateTime acceptedAt;

    protected RepairTaskRow() {
    }

    RepairTaskRow(String number, long equipmentId, long employeeId, long storeId,
            LocalDate requestDate, String faultDescription, RepairTask task) {
        this.repairTaskNumber = number;
        this.equipmentId = equipmentId;
        this.requestedByEmployeeId = employeeId;
        this.serviceStoreId = storeId;
        this.currentContractorId = task.currentContractorId();
        this.requestDate = requestDate;
        this.faultDescription = faultDescription;
        this.taskStatus = task.status().name();
    }

    RepairPersistence.StoredTask snapshot() {
        var acceptance = acceptanceResult == null ? null : RepairAcceptanceResult.valueOf(acceptanceResult);
        RepairTask task = RepairTask.restore(RepairTaskStatus.valueOf(taskStatus), currentContractorId,
                acceptance);
        return new RepairPersistence.StoredTask(repairTaskId, repairTaskNumber, equipmentId,
                serviceStoreId, requestDate, faultDescription, task, acceptedAt);
    }

    void apply(RepairTask task) {
        taskStatus = task.status().name();
        currentContractorId = task.currentContractorId();
        acceptanceResult = task.acceptanceResult() == null ? null : task.acceptanceResult().name();
        if (task.acceptanceResult() == null) {
            acceptedByEmployeeId = null;
            acceptedAt = null;
        }
    }

    void recordAcceptance(long employeeId, LocalDateTime time) {
        acceptedByEmployeeId = employeeId;
        acceptedAt = time;
    }
}
