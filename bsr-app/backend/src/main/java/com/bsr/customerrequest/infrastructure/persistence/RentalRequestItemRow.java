package com.bsr.customerrequest.infrastructure.persistence;

import com.bsr.customerrequest.domain.RentalRequestItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity(name = "RentalRequestItemRow")
@Table(name = "rental_request_item")
public class RentalRequestItemRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_item_id", nullable = false)
    Long requestItemId;

    @Column(name = "request_id", nullable = false)
    Long requestId;

    @Column(name = "equipment_category", nullable = false, length = 40)
    String equipmentCategory;

    @Column(name = "quantity", nullable = false)
    Integer quantity;

    @Column(name = "fitting_note", nullable = true, length = 255)
    String fittingNote;

    protected RentalRequestItemRow() {
    }

    RentalRequestItemRow(long requestId, RentalRequestItem item) {
        this.requestId = requestId;
        equipmentCategory = item.equipmentCategory();
        quantity = item.quantity();
        fittingNote = item.fittingNote();
    }

    Long requestItemId() {
        return requestItemId;
    }

    Long requestId() {
        return requestId;
    }

    String equipmentCategory() {
        return equipmentCategory;
    }

    Integer quantity() {
        return quantity;
    }

    String fittingNote() {
        return fittingNote;
    }
}
