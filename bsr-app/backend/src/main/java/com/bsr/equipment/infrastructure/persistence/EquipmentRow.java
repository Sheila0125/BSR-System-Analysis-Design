package com.bsr.equipment.infrastructure.persistence;

import com.bsr.equipment.domain.Equipment;
import com.bsr.equipment.domain.EquipmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity(name = "EquipmentRow")
@Table(name = "equipment")
public class EquipmentRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "equipment_id", nullable = false)
    Long equipmentId;

    @Column(name = "inventory_number", nullable = false, length = 40)
    String inventoryNumber;

    @Column(name = "equipment_name", nullable = false, length = 120)
    String equipmentName;

    @Column(name = "equipment_category", nullable = false, length = 40)
    String equipmentCategory;

    @Column(name = "skill_level", nullable = false, length = 20)
    String skillLevel;

    @Column(name = "home_store_id", nullable = false)
    Long homeStoreId;

    @Column(name = "current_store_id", nullable = false)
    Long currentStoreId;

    @Column(name = "equipment_status", nullable = false, length = 20)
    String equipmentStatus;

    @Column(name = "purchase_date", nullable = true)
    LocalDate purchaseDate;

    @Column(name = "purchase_price", nullable = true, precision = 12, scale = 2)
    BigDecimal purchasePrice;

    @Column(name = "current_daily_rate", nullable = false, precision = 12, scale = 2)
    BigDecimal currentDailyRate;

    protected EquipmentRow() {
    }

    Equipment toDomain() {
        return Equipment.restore(
                equipmentId,
                inventoryNumber,
                equipmentName,
                equipmentCategory,
                skillLevel,
                homeStoreId,
                currentStoreId,
                EquipmentStatus.valueOf(equipmentStatus),
                purchaseDate,
                purchasePrice,
                currentDailyRate);
    }

    void apply(Equipment equipment) {
        if (!equipmentId.equals(equipment.equipmentId())) {
            throw new IllegalArgumentException("equipment row identity does not match domain identity");
        }
        homeStoreId = equipment.homeStoreId();
        currentStoreId = equipment.currentStoreId();
        equipmentStatus = equipment.status().name();
    }
}
