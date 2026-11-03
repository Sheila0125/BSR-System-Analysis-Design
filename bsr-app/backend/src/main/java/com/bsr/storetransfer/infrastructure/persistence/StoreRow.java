package com.bsr.storetransfer.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import com.bsr.storetransfer.store.api.StoreSnapshot;

@Entity(name = "StoreRow")
@Table(name = "store")
public class StoreRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "store_id", nullable = false)
    Long storeId;

    @Column(name = "store_number", nullable = false, length = 30)
    String storeNumber;

    @Column(name = "store_name", nullable = false, length = 120)
    String storeName;

    @Column(name = "address", nullable = true, length = 255)
    String address;

    @Column(name = "business_status", nullable = false, length = 20)
    String businessStatus;

    @Column(name = "opening_time", nullable = false)
    LocalTime openingTime;

    @Column(name = "closing_time", nullable = false)
    LocalTime closingTime;

    protected StoreRow() {
    }

    public static StoreRow active(String number, String name, String address,
            LocalTime openingTime, LocalTime closingTime) {
        StoreRow row = new StoreRow();
        row.storeNumber = number;
        row.storeName = name;
        row.address = address;
        row.businessStatus = "ACTIVE";
        row.openingTime = openingTime;
        row.closingTime = closingTime;
        return row;
    }

    public void updateDetails(String name, String address, LocalTime opening, LocalTime closing) {
        this.storeName = name;
        this.address = address;
        this.openingTime = opening;
        this.closingTime = closing;
    }

    public void deactivate() {
        this.businessStatus = "INACTIVE";
    }

    public boolean active() {
        return "ACTIVE".equals(businessStatus);
    }

    public StoreSnapshot snapshot() {
        return new StoreSnapshot(storeId, storeNumber, storeName, address,
                active(), openingTime, closingTime);
    }
}
