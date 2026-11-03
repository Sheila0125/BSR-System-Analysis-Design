package com.bsr.storetransfer.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreRepository extends JpaRepository<StoreRow, Long> {

    boolean existsByStoreNumber(String storeNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select row from StoreRow row where row.storeId = :storeId")
    Optional<StoreRow> findByIdForUpdate(@Param("storeId") Long storeId);
}
