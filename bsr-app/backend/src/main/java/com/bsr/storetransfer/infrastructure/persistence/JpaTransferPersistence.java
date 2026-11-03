package com.bsr.storetransfer.infrastructure.persistence;

import com.bsr.storetransfer.transfer.application.TransferPersistence;
import com.bsr.storetransfer.transfer.application.TransferViews;
import com.bsr.storetransfer.transfer.domain.TransferAggregate;
import com.bsr.storetransfer.transfer.domain.TransferItem;
import com.bsr.storetransfer.transfer.domain.TransferStatus;
import com.bsr.storetransfer.transfer.domain.TransferType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class JpaTransferPersistence implements TransferPersistence {
    private final EquipmentTransferRepository transfers;
    private final EquipmentTransferItemRepository items;
    private final JdbcTemplate jdbc;

    public JpaTransferPersistence(EquipmentTransferRepository transfers,
            EquipmentTransferItemRepository items, JdbcTemplate jdbc) {
        this.transfers = transfers;
        this.items = items;
        this.jdbc = jdbc;
    }

    @Override
    public long create(TransferAggregate aggregate) {
        EquipmentTransferRow row = new EquipmentTransferRow();
        copyHeader(aggregate, row);
        transfers.saveAndFlush(row);
        for (TransferItem item : aggregate.items()) {
            EquipmentTransferItemRow itemRow = new EquipmentTransferItemRow();
            itemRow.transferId = row.transferId;
            copyItem(item, itemRow);
            items.save(itemRow);
        }
        items.flush();
        return row.transferId;
    }

    @Override
    public Optional<TransferViews.StorePair> stores(long transferId) {
        return jdbc.query("SELECT source_store_id,destination_store_id FROM equipment_transfer WHERE transfer_id=?",
                (rs, row) -> new TransferViews.StorePair(
                        rs.getLong("source_store_id"), rs.getLong("destination_store_id")), transferId)
                .stream().findFirst();
    }

    @Override
    public Optional<TransferViews.TransferRecord> lock(long transferId) {
        return transfers.findByIdForUpdate(transferId).map(this::record);
    }

    @Override
    public Optional<TransferViews.TransferRecord> find(long transferId) {
        return transfers.findById(transferId).map(this::record);
    }

    @Override
    public List<TransferViews.TransferRecord> listByStore(long storeId) {
        return transfers.findByStore(storeId).stream().map(this::record).toList();
    }

    @Override
    public List<TransferViews.BlockingItem> activeBlockers(long storeId) {
        return transfers.findActiveByStore(storeId).stream()
                .flatMap(root -> items.findByTransferIdOrderByEquipmentIdAsc(root.transferId).stream()
                        .map(item -> new TransferViews.BlockingItem(root.transferId,
                                item.transferItemId, item.equipmentId,
                                TransferStatus.valueOf(root.transferStatus),
                                TransferItem.DispatchResult.valueOf(item.dispatchResult),
                                TransferItem.ReceiptResult.valueOf(item.receiptResult))))
                .toList();
    }

    @Override
    public void save(TransferViews.TransferRecord record) {
        EquipmentTransferRow root = transfers.findById(record.id())
                .orElseThrow(() -> new IllegalStateException("transfer disappeared"));
        copyHeader(record.aggregate(), root);
        transfers.saveAndFlush(root);
        Map<Long, EquipmentTransferItemRow> rows = new HashMap<>();
        for (EquipmentTransferItemRow row : items.findByTransferIdOrderByEquipmentIdAsc(record.id())) {
            rows.put(row.equipmentId, row);
        }
        if (!rows.keySet().equals(record.itemIds().keySet())) {
            throw new IllegalStateException("transfer item set changed");
        }
        for (TransferItem item : record.aggregate().items()) {
            EquipmentTransferItemRow row = rows.get(item.equipmentId());
            if (row == null || !row.transferItemId.equals(record.itemIds().get(item.equipmentId()))) {
                throw new IllegalStateException("transfer item identity changed");
            }
            copyItem(item, row);
        }
        items.saveAllAndFlush(rows.values());
    }

    private TransferViews.TransferRecord record(EquipmentTransferRow root) {
        List<EquipmentTransferItemRow> rows = items.findByTransferIdOrderByEquipmentIdAsc(root.transferId);
        List<TransferItem> domainItems = rows.stream().map(row -> TransferItem.restore(
                row.equipmentId,
                TransferItem.DispatchResult.valueOf(row.dispatchResult),
                row.dispatchedByEmployeeId, row.dispatchedAt,
                TransferItem.ReceiptResult.valueOf(row.receiptResult),
                row.receivedByEmployeeId, row.receivedAt,
                row.exceptionNote)).toList();
        TransferAggregate aggregate = TransferAggregate.restore(root.transferNumber,
                TransferType.valueOf(root.transferType), root.sourceStoreId,
                root.destinationStoreId, root.initiatedByEmployeeId, root.initiatedAt,
                domainItems, root.approvedByEmployeeId, root.approvedAt, root.completedAt);
        if (aggregate.status() != TransferStatus.valueOf(root.transferStatus)) {
            throw new IllegalStateException("transfer root status conflicts with item facts");
        }
        Map<Long, Long> itemIds = new HashMap<>();
        for (EquipmentTransferItemRow row : rows) {
            itemIds.put(row.equipmentId, row.transferItemId);
        }
        return new TransferViews.TransferRecord(root.transferId, aggregate, itemIds);
    }

    private static void copyHeader(TransferAggregate domain, EquipmentTransferRow row) {
        row.transferNumber = domain.number();
        row.transferType = domain.type().name();
        row.sourceStoreId = domain.sourceStoreId();
        row.destinationStoreId = domain.destinationStoreId();
        row.initiatedByEmployeeId = domain.initiatedBy();
        row.initiatedAt = domain.initiatedAt();
        row.approvedByEmployeeId = domain.approvedBy();
        row.approvedAt = domain.approvedAt();
        row.completedAt = domain.completedAt();
        row.transferStatus = domain.status().name();
    }

    private static void copyItem(TransferItem domain, EquipmentTransferItemRow row) {
        row.equipmentId = domain.equipmentId();
        row.dispatchResult = domain.dispatchResult().name();
        row.dispatchedByEmployeeId = domain.dispatchedBy();
        row.dispatchedAt = domain.dispatchedAt();
        row.receiptResult = domain.receiptResult().name();
        row.receivedByEmployeeId = domain.receivedBy();
        row.receivedAt = domain.receivedAt();
        row.exceptionNote = domain.exceptionNote();
    }
}
