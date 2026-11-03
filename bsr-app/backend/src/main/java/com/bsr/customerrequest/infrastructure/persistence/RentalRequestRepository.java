package com.bsr.customerrequest.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RentalRequestRepository extends JpaRepository<RentalRequestRow, Long> {

    boolean existsByCustomerIdAndRequestStatusIn(Long customerId, Collection<String> requestStatuses);

    Optional<RentalRequestRow> findByRequestIdAndCustomerId(Long requestId, Long customerId);

    List<RentalRequestRow> findByCustomerIdOrderBySubmittedAtDescRequestIdDesc(Long customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from RentalRequestRow request where request.requestId = :requestId")
    Optional<RentalRequestRow> findByIdForUpdate(@Param("requestId") Long requestId);

    List<RentalRequestRow> findByServiceStoreIdAndRequestStatusInOrderBySubmittedAtAscRequestIdAsc(
            Long serviceStoreId,
            Collection<String> requestStatuses);
}
