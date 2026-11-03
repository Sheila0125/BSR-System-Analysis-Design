package com.bsr.equipment.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EquipmentCatalogPersistence {
    record Metadata(String name, String skillLevel, String description, LocalDate purchaseDate,
                    BigDecimal purchasePrice, BigDecimal retailPrice, BigDecimal currentDailyRate) { }
    record Entry(long id, String inventoryNumber, String category, long homeStoreId, long currentStoreId,
                 String status, long revision, Metadata metadata) { }

    List<Entry> find(long storeId, String status, String inventoryNumber, String category, String skillLevel);
    Optional<Entry> findById(long equipmentId);
    Optional<Entry> lock(long equipmentId);
    Entry insert(long storeId, String inventoryNumber, String category, Metadata metadata);
    Entry update(Entry existing, Metadata metadata);
}
