package com.bsr.equipment.infrastructure.persistence;

import com.bsr.equipment.domain.EquipmentInspection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity(name = "EquipmentInspectionRow")
@Table(name = "equipment_inspection")
public class EquipmentInspectionRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inspection_id", nullable = false)
    Long inspectionId;

    @Column(name = "equipment_id", nullable = false)
    Long equipmentId;

    @Column(name = "inspected_by_employee_id", nullable = false)
    Long inspectedByEmployeeId;

    @Column(name = "rental_item_id", nullable = true)
    Long rentalItemId;

    @Column(name = "repair_task_id", nullable = true)
    Long repairTaskId;

    @Column(name = "inspection_type", nullable = false, length = 20)
    String inspectionType;

    @Column(name = "inspected_at", nullable = false)
    LocalDateTime inspectedAt;

    @Column(name = "inspection_result", nullable = false, length = 20)
    String inspectionResult;

    @Column(name = "fault_description", nullable = true, length = 500)
    String faultDescription;

    protected EquipmentInspectionRow() {
    }

    EquipmentInspectionRow(EquipmentInspection inspection) {
        this.equipmentId = inspection.equipmentId();
        this.inspectedByEmployeeId = inspection.inspectedByEmployeeId();
        this.rentalItemId = inspection.rentalItemId();
        this.repairTaskId = inspection.repairTaskId();
        this.inspectionType = inspection.type().name();
        this.inspectedAt = inspection.inspectedAt();
        this.inspectionResult = inspection.result().name();
        this.faultDescription = inspection.faultDescription();
    }

    long inspectionId() {
        if (inspectionId == null) {
            throw new IllegalStateException("inspection has not been persisted");
        }
        return inspectionId;
    }
}
