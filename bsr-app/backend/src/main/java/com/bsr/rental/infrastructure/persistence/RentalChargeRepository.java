package com.bsr.rental.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RentalChargeRepository extends JpaRepository<RentalChargeRow, Long> {
    List<RentalChargeRow> findByContractIdOrderByRentalChargeId(long contractId);
}
