package com.bsr.storetransfer.transfer.application;

import com.bsr.storetransfer.transfer.domain.TransferAggregate;
import com.bsr.storetransfer.transfer.domain.TransferItem;
import com.bsr.storetransfer.transfer.domain.TransferStatus;
import java.util.Map;

public final class TransferViews {
    private TransferViews() {
    }

    public record TransferRecord(long id, TransferAggregate aggregate, Map<Long, Long> itemIds) {
        public TransferRecord {
            itemIds = Map.copyOf(itemIds);
        }
    }

    public record StorePair(long sourceStoreId, long destinationStoreId) {
    }

    public record BlockingItem(long transferId, long itemId, long equipmentId,
            TransferStatus transferStatus, TransferItem.DispatchResult dispatchResult,
            TransferItem.ReceiptResult receiptResult) {
    }
}
