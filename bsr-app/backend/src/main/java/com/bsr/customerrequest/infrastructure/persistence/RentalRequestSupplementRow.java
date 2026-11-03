package com.bsr.customerrequest.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity(name = "RentalRequestSupplementRow")
@Table(name = "rental_request_supplement")
public class RentalRequestSupplementRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "supplement_id", nullable = false)
    Long supplementId;

    @Column(name = "request_id", nullable = false)
    Long requestId;

    @Column(name = "cycle_number", nullable = false)
    Integer cycleNumber;

    @Column(name = "requested_by_employee_id", nullable = false)
    Long requestedByEmployeeId;

    @Column(name = "reason", nullable = false, length = 500)
    String reason;

    @Column(name = "requested_at", nullable = false)
    LocalDateTime requestedAt;

    @Column(name = "customer_response", nullable = true, length = 500)
    String customerResponse;

    @Column(name = "responded_at", nullable = true)
    LocalDateTime respondedAt;

    protected RentalRequestSupplementRow() {
    }

    RentalRequestSupplementRow(long requestId, int cycleNumber, long employeeId,
            String reason, LocalDateTime requestedAt) {
        this.requestId = requestId;
        this.cycleNumber = cycleNumber;
        this.requestedByEmployeeId = employeeId;
        this.reason = reason;
        this.requestedAt = requestedAt;
    }

    void answer(String response, LocalDateTime at) {
        if (customerResponse != null || respondedAt != null) {
            throw new IllegalStateException("supplement cycle already answered");
        }
        customerResponse = response;
        respondedAt = at;
    }
}
