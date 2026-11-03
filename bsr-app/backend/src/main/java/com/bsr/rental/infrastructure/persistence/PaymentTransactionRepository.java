package com.bsr.rental.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransactionRow, Long> {
    Optional<PaymentTransactionRow> findByExternalTransactionId(String externalId);
    List<PaymentTransactionRow> findByRentalChargeIdOrderByPaymentTransactionId(long chargeId);
}
