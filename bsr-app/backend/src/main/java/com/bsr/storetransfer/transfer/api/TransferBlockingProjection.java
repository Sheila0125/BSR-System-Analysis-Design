package com.bsr.storetransfer.transfer.api;

import java.util.List;

/** Read-only T07 facts for T09's cross-module stop-store guard. */
public interface TransferBlockingProjection {
    List<TransferBlocker> activeForStore(long storeId);
}
