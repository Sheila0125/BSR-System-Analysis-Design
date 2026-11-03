package com.bsr.repair.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RepairServiceChargeRepository extends JpaRepository<RepairServiceChargeRow, Long> {

    Optional<RepairServiceChargeRow> findByRepairTaskId(long taskId);
}
