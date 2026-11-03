package com.bsr.customerrequest.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RentalRequestItemRepository extends JpaRepository<RentalRequestItemRow, Long> {

    List<RentalRequestItemRow> findByRequestIdOrderByRequestItemId(Long requestId);
}
