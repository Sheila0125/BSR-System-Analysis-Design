package com.bsr.storetransfer.transfer.application;

import com.bsr.storetransfer.transfer.domain.TransferAggregate;
import java.util.List;
import java.util.Optional;

public interface TransferPersistence {
    long create(TransferAggregate aggregate);
    Optional<TransferViews.StorePair> stores(long transferId);
    Optional<TransferViews.TransferRecord> lock(long transferId);
    Optional<TransferViews.TransferRecord> find(long transferId);
    List<TransferViews.TransferRecord> listByStore(long storeId);
    List<TransferViews.BlockingItem> activeBlockers(long storeId);
    void save(TransferViews.TransferRecord record);
}
