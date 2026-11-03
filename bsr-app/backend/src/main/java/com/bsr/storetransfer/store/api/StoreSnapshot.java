package com.bsr.storetransfer.store.api;

import java.time.LocalTime;

public record StoreSnapshot(
        long storeId,
        String storeNumber,
        String storeName,
        String address,
        boolean active,
        LocalTime openingTime,
        LocalTime closingTime) {
}
