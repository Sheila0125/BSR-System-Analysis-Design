package com.bsr.repair.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepairContractorRepository extends JpaRepository<RepairContractorRow, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select row from RepairContractorRow row where row.contractorId = :contractorId")
    Optional<RepairContractorRow> findByIdForUpdate(@Param("contractorId") Long contractorId);
}
