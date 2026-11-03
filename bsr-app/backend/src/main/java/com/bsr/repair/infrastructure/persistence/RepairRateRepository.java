package com.bsr.repair.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepairRateRepository extends JpaRepository<RepairRateRow, Long> {

    @Query("""
            select row from RepairRateRow row where row.contractorId = :contractorId
            and row.effectiveFrom <= :repairDate
            and (row.effectiveTo is null or row.effectiveTo >= :repairDate)
            order by row.effectiveFrom desc
            """)
    List<RepairRateRow> matching(@Param("contractorId") long contractorId,
            @Param("repairDate") LocalDate repairDate);

    List<RepairRateRow> findByContractorIdOrderByEffectiveFromAsc(long contractorId);
}
