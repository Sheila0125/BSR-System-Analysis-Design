package com.bsr.rental.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity(name = "RentalItemRow")
@Table(name = "rental_item")
public class RentalItemRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rental_item_id", nullable = false)
    Long rentalItemId;

    @Column(name = "contract_id", nullable = false)
    Long contractId;

    @Column(name = "equipment_id", nullable = false)
    Long equipmentId;

    @Column(name = "daily_rate_snapshot", nullable = false, precision = 12, scale = 2)
    BigDecimal dailyRateSnapshot;

    @Column(name = "checked_out_at", nullable = true)
    LocalDateTime checkedOutAt;

    @Column(name = "returned_at", nullable = true)
    LocalDateTime returnedAt;

    @Column(name = "return_store_id", nullable = true)
    Long returnStoreId;

    @Column(name = "item_status", nullable = false, length = 20)
    String itemStatus;

    protected RentalItemRow() {
    }
}
