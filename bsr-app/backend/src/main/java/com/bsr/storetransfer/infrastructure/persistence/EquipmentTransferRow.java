package com.bsr.storetransfer.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity(name = "EquipmentTransferRow")
@Table(name = "equipment_transfer")
public class EquipmentTransferRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transfer_id", nullable = false)
    Long transferId;

    @Column(name = "transfer_number", nullable = false, length = 30)
    String transferNumber;

    @Column(name = "transfer_type", nullable = false, length = 20)
    String transferType;

    @Column(name = "source_store_id", nullable = false)
    Long sourceStoreId;

    @Column(name = "destination_store_id", nullable = false)
    Long destinationStoreId;

    @Column(name = "initiated_by_employee_id", nullable = false)
    Long initiatedByEmployeeId;

    @Column(name = "approved_by_employee_id", nullable = true)
    Long approvedByEmployeeId;

    @Column(name = "initiated_at", nullable = false)
    LocalDateTime initiatedAt;

    @Column(name = "approved_at", nullable = true)
    LocalDateTime approvedAt;

    @Column(name = "completed_at", nullable = true)
    LocalDateTime completedAt;

    @Column(name = "transfer_status", nullable = false, length = 20)
    String transferStatus;

    protected EquipmentTransferRow() {
    }
}
