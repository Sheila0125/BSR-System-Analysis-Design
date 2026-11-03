package com.bsr.equipment.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EquipmentRepository extends JpaRepository<EquipmentRow, Long> {

    @Query("""
            select row from EquipmentRow row
            where row.currentStoreId = :storeId
              and row.equipmentStatus = 'AVAILABLE'
              and (:category is null or row.equipmentCategory = :category)
              and (:skillLevel is null or row.skillLevel = :skillLevel)
            order by row.equipmentId
            """)
    List<EquipmentRow> findAvailable(
            @Param("storeId") long storeId,
            @Param("category") String category,
            @Param("skillLevel") String skillLevel);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select row from EquipmentRow row where row.equipmentId in :equipmentIds order by row.equipmentId")
    List<EquipmentRow> findAllByIdInOrderForUpdate(@Param("equipmentIds") Collection<Long> equipmentIds);
}
