package com.bsr.rental.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RentalItemRepository extends JpaRepository<RentalItemRow, Long> {
    List<RentalItemRow> findByContractIdOrderByRentalItemId(long contractId);
}
