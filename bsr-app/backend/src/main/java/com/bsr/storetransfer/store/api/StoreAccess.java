package com.bsr.storetransfer.store.api;

import java.util.Optional;

public interface StoreAccess {

    Optional<StoreSnapshot> findById(long storeId);

    StoreSnapshot lockForUse(long storeId);

    StoreSnapshot lockForDeactivation(long storeId);
}
