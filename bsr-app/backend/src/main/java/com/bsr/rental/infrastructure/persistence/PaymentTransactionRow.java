package com.bsr.rental.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity(name = "PaymentTransactionRow")
@Table(name = "payment_transaction")
public class PaymentTransactionRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_transaction_id", nullable = false)
    Long paymentTransactionId;

    @Column(name = "rental_charge_id", nullable = false)
    Long rentalChargeId;

    @Column(name = "transaction_type", nullable = false, length = 20)
    String transactionType;

    @Column(name = "external_transaction_id", nullable = false, length = 100)
    String externalTransactionId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    BigDecimal amount;

    @Column(name = "transaction_status", nullable = false, length = 20)
    String transactionStatus;

    @Column(name = "processed_at", nullable = true)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    LocalDateTime processedAt;

    @Column(name = "recorded_by_employee_id", nullable = true)
    Long recordedByEmployeeId;

    @Column(name = "transaction_source", nullable = false, length = 20)
    String transactionSource = "EXTERNAL";

    @Column(name = "simulated_by_account_id", nullable = true)
    Long simulatedByAccountId;

    @Column(name = "recorded_at", nullable = false)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    LocalDateTime recordedAt;

    protected PaymentTransactionRow() {
    }
}
