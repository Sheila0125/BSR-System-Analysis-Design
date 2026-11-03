package com.bsr.customerrequest.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RentalRequestSupplementRepository extends JpaRepository<RentalRequestSupplementRow, Long> {

    List<RentalRequestSupplementRow> findByRequestIdOrderByCycleNumberAsc(Long requestId);

    Optional<RentalRequestSupplementRow> findFirstByRequestIdOrderByCycleNumberDesc(Long requestId);
}
