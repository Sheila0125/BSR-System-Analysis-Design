package com.bsr.rental.application;

import com.bsr.rental.domain.ExternalTransactionResult;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TransactionPersistence {
    Optional<RentalPersistence.Charge> charge(long id);
    Optional<RecordedTransaction> byExternalId(String externalId);
    RecordedTransaction insert(ExternalTransactionResult result, long employeeId, LocalDateTime now);
    RecordedTransaction insertSimulated(ExternalTransactionResult result, long accountId, LocalDateTime now);
    List<RecordedTransaction> forCharge(long chargeId);
    BigDecimal successfulTotal(long chargeId);
    void saveChargeStatus(long chargeId, String status);
    record RecordedTransaction(long id, ExternalTransactionResult result, Long employeeId, LocalDateTime recordedAt,
            String source, Long simulatedByAccountId) { }
}
