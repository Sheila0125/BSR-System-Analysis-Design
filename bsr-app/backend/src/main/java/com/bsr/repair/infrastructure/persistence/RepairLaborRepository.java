package com.bsr.repair.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepairLaborRepository extends JpaRepository<RepairLaborRow, Long> {

    long countByRepairTaskIdAndContractorIdAndSubmittedAtIsNull(long taskId, long contractorId);

    List<RepairLaborRow> findByRepairTaskIdOrderByRepairLaborId(long taskId);

    List<RepairLaborRow> findByContractorIdOrderByRepairLaborIdDesc(long contractorId);

    @Query("""
            select row from RepairLaborRow row where row.contractorId = :contractorId
            and row.repairRateId is null and row.repairDate >= :fromDate
            and (:toDate is null or row.repairDate <= :toDate)
            order by row.repairLaborId
            """)
    List<RepairLaborRow> unratedInPeriod(@Param("contractorId") long contractorId,
            @Param("fromDate") java.time.LocalDate fromDate,
            @Param("toDate") java.time.LocalDate toDate);

    @Modifying
    @Query("""
            update RepairLaborRow row set row.submittedAt = :submittedAt
            where row.repairTaskId = :taskId and row.contractorId = :contractorId
            and row.submittedAt is null
            """)
    int markSubmitted(@Param("taskId") long taskId, @Param("contractorId") long contractorId,
            @Param("submittedAt") LocalDateTime submittedAt);
}
