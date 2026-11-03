package com.bsr.customerrequest.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<CustomerRow, Long> {

    Optional<CustomerRow> findByCustomerNumber(String customerNumber);

    Optional<CustomerRow> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select customer from CustomerRow customer where customer.customerId = :customerId")
    Optional<CustomerRow> findByIdForUpdate(@Param("customerId") Long customerId);
}
