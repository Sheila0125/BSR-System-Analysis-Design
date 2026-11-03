package com.bsr.rental.application;

import com.bsr.rental.domain.RentalChangeRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChangePersistence {
    StoredChange insertRequest(RentalChangeRequest request);
    List<StoredChange> forContract(long contractId);
    List<StoredChange> forRequestedStore(long storeId);
    Optional<StoredChange> find(long id);
    StoredChange begin(long id,long employeeId,LocalDate date,LocalDateTime now);
    StoredChange quote(long id,BigDecimal delta);
    StoredChange finish(long id,String status);
    StoredChange insertDraftRevision(long contractId,long employeeId,long storeId,LocalDate date,BigDecimal delta,LocalDateTime now);
    StoredChange insertHandling(long contractId,String type,long employeeId,long storeId,LocalDate date,LocalDateTime now);
    void link(long changeId,long itemId,String effect);
    List<Effect> effectsForContract(long contractId);
    record Effect(long changeId,long itemId,String effect,String status,Long storeId) { }
    record StoredChange(long id,long contractId,String number,String type,String status,Long customerId,
            Long requestedStoreId,LocalDateTime requestedAt,String note,Long handledByEmployeeId,
            LocalDate effectiveDate,BigDecimal amountDelta,LocalDateTime changedAt,List<Long> requestedReturnItemIds) {
        public StoredChange { requestedReturnItemIds = requestedReturnItemIds == null ? List.of() : List.copyOf(requestedReturnItemIds); }
    }
}
