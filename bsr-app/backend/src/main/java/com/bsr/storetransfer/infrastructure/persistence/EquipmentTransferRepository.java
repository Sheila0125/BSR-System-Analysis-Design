package com.bsr.storetransfer.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EquipmentTransferRepository extends JpaRepository<EquipmentTransferRow, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select row from EquipmentTransferRow row where row.transferId = :transferId")
    Optional<EquipmentTransferRow> findByIdForUpdate(@Param("transferId") Long transferId);

    @Query("select row from EquipmentTransferRow row where row.sourceStoreId = :storeId "
            + "or row.destinationStoreId = :storeId order by row.transferId")
    List<EquipmentTransferRow> findByStore(@Param("storeId") Long storeId);

    @Query("select row from EquipmentTransferRow row where "
            + "(row.sourceStoreId = :storeId or row.destinationStoreId = :storeId) "
            + "and row.transferStatus <> 'COMPLETED' order by row.transferId")
    List<EquipmentTransferRow> findActiveByStore(@Param("storeId") Long storeId);
}
