package com.bsr.rental.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RentalChangeItemRepository extends JpaRepository<RentalChangeItemRow, Long> {
    java.util.List<RentalChangeItemRow> findByRentalChangeIdOrderByRentalChangeItemId(long changeId);
}
