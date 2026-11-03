package com.bsr.equipment.api;

import com.bsr.equipment.domain.EquipmentStatus;
import java.math.BigDecimal;

public record EquipmentSnapshot(
        long equipmentId,
        String inventoryNumber,
        String equipmentName,
        String equipmentCategory,
        String skillLevel,
        long homeStoreId,
        long currentStoreId,
        EquipmentStatus status,
        BigDecimal currentDailyRate) {
}
