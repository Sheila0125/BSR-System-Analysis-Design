package com.bsr.rental.infrastructure.persistence;

import com.bsr.rental.application.RentalPersistence.Charge;
import com.bsr.rental.application.TransactionPersistence;
import com.bsr.rental.domain.ExternalTransactionResult;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaTransactionPersistence implements TransactionPersistence {
    private final PaymentTransactionRepository transactions;
    private final RentalChargeRepository charges;
    public JpaTransactionPersistence(PaymentTransactionRepository transactions, RentalChargeRepository charges) {
        this.transactions = transactions; this.charges = charges;
    }
    @Override public Optional<Charge> charge(long id) {
        return charges.findById(id).map(c -> new Charge(c.rentalChargeId,c.contractId,c.rentalChangeId,
                c.chargeType,c.amount,c.chargeStatus,c.createdAt));
    }
    @Override public Optional<RecordedTransaction> byExternalId(String id) {
        return transactions.findByExternalTransactionId(id).map(this::snapshot);
    }
    @Override public RecordedTransaction insert(ExternalTransactionResult result, long employeeId, LocalDateTime now) {
        return insert(result, employeeId, null, "EXTERNAL", now);
    }
    @Override public RecordedTransaction insertSimulated(ExternalTransactionResult result, long accountId, LocalDateTime now) {
        return insert(result, null, accountId, "SIMULATED", now);
    }
    private RecordedTransaction insert(ExternalTransactionResult result, Long employeeId, Long accountId,
            String source, LocalDateTime now) {
        var row = new PaymentTransactionRow();
        row.rentalChargeId=result.chargeId(); row.transactionType=result.type().name();
        row.externalTransactionId=result.externalId(); row.amount=result.amount();
        row.transactionStatus=result.status().name(); row.processedAt=result.processedAt();
        row.recordedByEmployeeId=employeeId; row.recordedAt=now;
        row.transactionSource=source; row.simulatedByAccountId=accountId;
        return snapshot(transactions.saveAndFlush(row));
    }
    @Override public List<RecordedTransaction> forCharge(long id) {
        return transactions.findByRentalChargeIdOrderByPaymentTransactionId(id).stream().map(this::snapshot).toList();
    }
    @Override public BigDecimal successfulTotal(long id) {
        return forCharge(id).stream().filter(t -> t.result().status()==ExternalTransactionResult.Status.SUCCEEDED)
                .map(t -> t.result().amount()).reduce(new BigDecimal("0.00"),BigDecimal::add);
    }
    @Override public void saveChargeStatus(long id, String status) {
        var row=charges.findById(id).orElseThrow(); row.chargeStatus=status; charges.saveAndFlush(row);
    }
    private RecordedTransaction snapshot(PaymentTransactionRow row) {
        return new RecordedTransaction(row.paymentTransactionId,new ExternalTransactionResult(row.rentalChargeId,
                ExternalTransactionResult.Type.valueOf(row.transactionType),row.externalTransactionId,row.amount,
                ExternalTransactionResult.Status.valueOf(row.transactionStatus),row.processedAt),row.recordedByEmployeeId,row.recordedAt,
                row.transactionSource,row.simulatedByAccountId);
    }
}
