package com.bsr.storetransfer.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity(name = "EquipmentTransferItemRow")
@Table(name = "equipment_transfer_item")
public class EquipmentTransferItemRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transfer_item_id", nullable = false)
    Long transferItemId;

    @Column(name = "transfer_id", nullable = false)
    Long transferId;

    @Column(name = "equipment_id", nullable = false)
    Long equipmentId;

    @Column(name = "dispatch_result", nullable = false, length = 20)
    String dispatchResult;

    @Column(name = "dispatched_by_employee_id", nullable = true)
    Long dispatchedByEmployeeId;

    @Column(name = "dispatched_at", nullable = true)
    LocalDateTime dispatchedAt;

    @Column(name = "receipt_result", nullable = false, length = 20)
    String receiptResult;

    @Column(name = "received_by_employee_id", nullable = true)
    Long receivedByEmployeeId;

    @Column(name = "received_at", nullable = true)
    LocalDateTime receivedAt;

    @Column(name = "exception_note", nullable = true, length = 500)
    String exceptionNote;

    protected EquipmentTransferItemRow() {
    }
}
