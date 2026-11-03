package com.bsr.customerrequest.infrastructure.persistence;

import com.bsr.customerrequest.domain.RentalRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity(name = "RentalRequestRow")
@Table(name = "rental_request")
public class RentalRequestRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id", nullable = false)
    Long requestId;

    @Column(name = "request_number", nullable = false, length = 30)
    String requestNumber;

    @Column(name = "customer_id", nullable = false)
    Long customerId;

    @Column(name = "service_store_id", nullable = false)
    Long serviceStoreId;

    @Column(name = "accepted_by_employee_id", nullable = true)
    Long acceptedByEmployeeId;

    @Column(name = "rental_start_date", nullable = false)
    LocalDate rentalStartDate;

    @Column(name = "rental_days", nullable = false)
    Integer rentalDays;

    @Column(name = "skill_level", nullable = false, length = 20)
    String skillLevel;

    @Column(name = "submitted_at", nullable = false)
    LocalDateTime submittedAt;

    @Column(name = "request_status", nullable = false, length = 20)
    String requestStatus;

    protected RentalRequestRow() {
    }

    RentalRequestRow(RentalRequest request) {
        requestNumber = request.requestNumber();
        customerId = request.customerId();
        serviceStoreId = request.serviceStoreId();
        acceptedByEmployeeId = request.acceptedByEmployeeId();
        rentalStartDate = request.rentalStartDate();
        rentalDays = request.rentalDays();
        skillLevel = request.skillLevel();
        submittedAt = request.submittedAt();
        requestStatus = request.status().name();
    }

    void updateState(RentalRequest request) {
        acceptedByEmployeeId = request.acceptedByEmployeeId();
        requestStatus = request.status().name();
    }

    Long requestId() {
        return requestId;
    }

    String requestNumber() {
        return requestNumber;
    }

    Long customerId() {
        return customerId;
    }

    Long serviceStoreId() {
        return serviceStoreId;
    }

    Long acceptedByEmployeeId() {
        return acceptedByEmployeeId;
    }

    LocalDate rentalStartDate() {
        return rentalStartDate;
    }

    Integer rentalDays() {
        return rentalDays;
    }

    String skillLevel() {
        return skillLevel;
    }

    LocalDateTime submittedAt() {
        return submittedAt;
    }

    String requestStatus() {
        return requestStatus;
    }
}
