package com.bsr.rental.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RentalContractRepository extends JpaRepository<RentalContractRow, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from RentalContractRow c where c.contractId = :id")
    Optional<RentalContractRow> lock(@Param("id") long id);
    List<RentalContractRow> findByCustomerIdOrderByContractIdDesc(long customerId);
    List<RentalContractRow> findByServiceStoreIdOrderByContractIdDesc(long storeId);
    @Query("select c from RentalContractRow c where c.serviceStoreId = :storeId and c.contractStatus not in ('ENDED', 'CANCELLED') order by c.contractId")
    List<RentalContractRow> unfinishedAtStore(@Param("storeId") long storeId);
}
