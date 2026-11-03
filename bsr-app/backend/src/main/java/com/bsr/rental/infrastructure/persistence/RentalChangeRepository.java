package com.bsr.rental.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RentalChangeRepository extends JpaRepository<RentalChangeRow, Long> {
    List<RentalChangeRow> findByContractIdOrderByRentalChangeId(long contractId);
    List<RentalChangeRow> findByRequestedStoreIdAndChangeStatusOrderByRentalChangeId(long storeId,String status);
}
