package com.bsr.rental.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity(name = "RentalChangeRow")
@Table(name = "rental_change")
public class RentalChangeRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rental_change_id", nullable = false)
    Long rentalChangeId;

    @Column(name = "change_number", nullable = false, length = 30)
    String changeNumber;

    @Column(name = "contract_id", nullable = false)
    Long contractId;

    @Column(name = "change_type", nullable = false, length = 20)
    String changeType;

    @Column(name = "change_reason", nullable = true, length = 255)
    String changeReason;

    @Column(name = "effective_date", nullable = true)
    LocalDate effectiveDate;

    @Column(name = "amount_delta", nullable = true, precision = 12, scale = 2)
    BigDecimal amountDelta;

    @Column(name = "handled_by_employee_id", nullable = true)
    Long handledByEmployeeId;

    @Column(name = "changed_at", nullable = true)
    LocalDateTime changedAt;

    @Column(name = "change_status", nullable = false, length = 20)
    String changeStatus;

    @Column(name = "requested_by_customer_id", nullable = true)
    Long requestedByCustomerId;

    @Column(name = "requested_at", nullable = true)
    LocalDateTime requestedAt;

    @Column(name = "requested_store_id", nullable = true)
    Long requestedStoreId;

    @Column(name = "request_note", nullable = true, length = 500)
    String requestNote;

    protected RentalChangeRow() {
    }
}
