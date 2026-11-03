package com.bsr.rental.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity(name = "RentalChargeRow")
@Table(name = "rental_charge")
public class RentalChargeRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rental_charge_id", nullable = false)
    Long rentalChargeId;

    @Column(name = "contract_id", nullable = false)
    Long contractId;

    @Column(name = "rental_change_id", nullable = true)
    Long rentalChangeId;

    @Column(name = "charge_type", nullable = false, length = 20)
    String chargeType;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    BigDecimal amount;

    @Column(name = "charge_status", nullable = false, length = 20)
    String chargeStatus;

    @Column(name = "created_at", nullable = false)
    LocalDateTime createdAt;

    protected RentalChargeRow() {
    }
}
