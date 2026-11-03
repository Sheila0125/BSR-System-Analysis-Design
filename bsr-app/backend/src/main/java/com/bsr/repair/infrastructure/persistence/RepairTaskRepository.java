package com.bsr.repair.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepairTaskRepository extends JpaRepository<RepairTaskRow, Long> {

    @Query("select count(row) > 0 from RepairTaskRow row where row.equipmentId = :equipmentId and row.taskStatus <> 'COMPLETED'")
    boolean hasUnfinishedTask(@Param("equipmentId") long equipmentId);

    @Query("select count(row) > 0 from RepairTaskRow row where row.currentContractorId = :contractorId and row.taskStatus <> 'COMPLETED'")
    boolean hasUnfinishedAssignment(@Param("contractorId") long contractorId);

    List<RepairTaskRow> findByServiceStoreIdOrderByRepairTaskIdDesc(long storeId);

    List<RepairTaskRow> findByCurrentContractorIdOrderByRepairTaskIdDesc(long contractorId);

    Optional<RepairTaskRow> findByRepairTaskIdAndCurrentContractorId(long taskId, long contractorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select row from RepairTaskRow row where row.repairTaskId = :taskId")
    Optional<RepairTaskRow> findByIdForUpdate(@Param("taskId") long taskId);
}
