package com.bsr.storetransfer.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipmentTransferItemRepository extends JpaRepository<EquipmentTransferItemRow, Long> {
    List<EquipmentTransferItemRow> findByTransferIdOrderByEquipmentIdAsc(Long transferId);
}
