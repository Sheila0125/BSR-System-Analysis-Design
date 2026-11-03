package com.bsr.rental.api;

import java.util.List;

/** Read-only rental facts for T09 store deactivation. */
public interface RentalBlockingProjection {
    List<UnfinishedContract> unfinishedAtStore(long storeId);

    record UnfinishedContract(long contractId, long storeId, String status) { }
}
