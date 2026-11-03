package com.bsr.equipment.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipmentInspectionRepository extends JpaRepository<EquipmentInspectionRow, Long> {
}
