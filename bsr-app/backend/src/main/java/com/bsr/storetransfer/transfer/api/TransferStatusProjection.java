package com.bsr.storetransfer.transfer.api;

import java.util.Map;

/** Existing transfer states involving one store; each transfer is counted once. */
public interface TransferStatusProjection {
    Map<String, Long> countsAtStore(long storeId);
}
