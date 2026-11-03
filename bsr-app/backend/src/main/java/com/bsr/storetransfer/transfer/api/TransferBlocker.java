package com.bsr.storetransfer.transfer.api;

public record TransferBlocker(long transferId, long itemId, long equipmentId,
        String transferStatus, String dispatchResult, String receiptResult) {
}
