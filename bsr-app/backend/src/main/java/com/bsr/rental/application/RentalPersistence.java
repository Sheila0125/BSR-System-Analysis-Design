package com.bsr.rental.application;

import com.bsr.rental.domain.RentalContract;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RentalPersistence {
    StoredContract insert(RentalContract contract, LocalDateTime now);
    Optional<StoredContract> find(long id);
    Optional<StoredContract> lock(long id);
    List<StoredContract> forCustomer(long customerId);
    List<StoredContract> forStore(long storeId);
    void saveState(StoredContract contract);
    void markIssued(long contractId, List<Long> itemIds, LocalDateTime now);
    void markReturned(long itemId, long storeId, LocalDateTime now);
    StoredItem appendItem(long contractId,long equipmentId,BigDecimal rate);
    void createAdjustmentCharge(long contractId,long changeId,BigDecimal delta,LocalDateTime now);
    void reviseInitialCharge(long contractId,BigDecimal newAmount,LocalDateTime now);

    record StoredContract(long id, RentalContract contract, List<StoredItem> items, List<Charge> charges) {
        public StoredContract {
            items = List.copyOf(items);
            charges = List.copyOf(charges);
        }
    }
    record StoredItem(long id, long equipmentId, BigDecimal dailyRateSnapshot, String status,
            LocalDateTime checkedOutAt, LocalDateTime returnedAt, Long returnStoreId,boolean active) { }
    record Charge(long id, long contractId, Long changeId, String type, BigDecimal amount,
            String status, LocalDateTime createdAt) { }
}
