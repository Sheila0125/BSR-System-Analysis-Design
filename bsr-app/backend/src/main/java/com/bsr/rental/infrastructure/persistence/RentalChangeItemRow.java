package com.bsr.rental.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity(name = "RentalChangeItemRow")
@Table(name = "rental_change_item")
public class RentalChangeItemRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rental_change_item_id", nullable = false)
    Long rentalChangeItemId;

    @Column(name = "rental_change_id", nullable = false)
    Long rentalChangeId;

    @Column(name = "rental_item_id", nullable = false)
    Long rentalItemId;

    @Column(name = "effect_code", nullable = false, length = 20)
    String effectCode;

    protected RentalChangeItemRow() {
    }
}
