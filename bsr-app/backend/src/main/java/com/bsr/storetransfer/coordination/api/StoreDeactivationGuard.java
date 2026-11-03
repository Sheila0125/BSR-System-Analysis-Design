package com.bsr.storetransfer.coordination.api;

/** T09 supplies a cross-module implementation inside the stop-store transaction. */
public interface StoreDeactivationGuard {
    void validate(long storeId);
}
