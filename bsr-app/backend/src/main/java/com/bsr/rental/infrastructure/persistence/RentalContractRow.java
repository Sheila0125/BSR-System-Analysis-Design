package com.bsr.rental.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity(name = "RentalContractRow")
@Table(name = "rental_contract")
public class RentalContractRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "contract_id", nullable = false)
    Long contractId;

    @Column(name = "contract_number", nullable = false, length = 30)
    String contractNumber;

    @Column(name = "source_request_id", nullable = true)
    Long sourceRequestId;

    @Column(name = "customer_id", nullable = false)
    Long customerId;

    @Column(name = "created_by_employee_id", nullable = false)
    Long createdByEmployeeId;

    @Column(name = "service_store_id", nullable = false)
    Long serviceStoreId;

    @Column(name = "contract_date", nullable = false)
    LocalDate contractDate;

    @Column(name = "rental_start_date", nullable = false)
    LocalDate rentalStartDate;

    @Column(name = "rental_days", nullable = false)
    Integer rentalDays;

    @Column(name = "amount_due", nullable = false, precision = 12, scale = 2)
    BigDecimal amountDue;

    @Column(name = "contract_status", nullable = false, length = 20)
    String contractStatus;

    protected RentalContractRow() {
    }
}
