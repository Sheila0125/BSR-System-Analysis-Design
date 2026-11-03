package com.bsr.rental.infrastructure.persistence;

import com.bsr.rental.application.ChangePersistence;
import com.bsr.rental.domain.RentalChangeRequest;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaChangePersistence implements ChangePersistence {
    private final RentalChangeRepository changes;
    private final RentalChangeItemRepository effects;
    private final jakarta.persistence.EntityManager entityManager;
    public JpaChangePersistence(RentalChangeRepository changes,RentalChangeItemRepository effects,jakarta.persistence.EntityManager entityManager) {
        this.changes=changes; this.effects=effects; this.entityManager=entityManager;
    }
    @Override public StoredChange insertRequest(RentalChangeRequest request) {
        var row=new RentalChangeRow(); row.contractId=request.contractId(); row.changeNumber=request.number();
        row.changeType=request.type().name(); row.changeStatus="SUBMITTED";
        row.requestedByCustomerId=request.customerId(); row.requestedStoreId=request.requestedStoreId();
        row.requestedAt=request.requestedAt(); row.requestNote=request.note();
        return snapshot(changes.saveAndFlush(row));
    }
    @Override public List<StoredChange> forContract(long id) { return changes.findByContractIdOrderByRentalChangeId(id).stream().map(r -> { entityManager.refresh(r); return snapshot(r); }).toList(); }
    @Override public List<StoredChange> forRequestedStore(long id) {
        return java.util.stream.Stream.concat(
                changes.findByRequestedStoreIdAndChangeStatusOrderByRentalChangeId(id,"SUBMITTED").stream(),
                changes.findByRequestedStoreIdAndChangeStatusOrderByRentalChangeId(id,"PROCESSING").stream()).map(this::snapshot).toList();
    }
    @Override public Optional<StoredChange> find(long id) { return changes.findById(id).map(r -> { entityManager.refresh(r); return snapshot(r); }); }
    @Override public StoredChange begin(long id,long employeeId,LocalDate date,LocalDateTime now) {
        var r=changes.findById(id).orElseThrow();
        r.changeStatus="PROCESSING"; r.handledByEmployeeId=employeeId; r.effectiveDate=date; r.changedAt=now;
        return snapshot(changes.saveAndFlush(r));
    }
    @Override public StoredChange quote(long id,BigDecimal delta) {
        var r=changes.findById(id).orElseThrow(); r.amountDelta=delta;
        return snapshot(changes.saveAndFlush(r));
    }
    @Override public StoredChange finish(long id,String status) {
        var r=changes.findById(id).orElseThrow(); r.changeStatus=status;
        return snapshot(changes.saveAndFlush(r));
    }
    @Override public StoredChange insertDraftRevision(long contractId,long employeeId,long storeId,LocalDate date,BigDecimal delta,LocalDateTime now) {
        var r=new RentalChangeRow(); r.contractId=contractId;
        r.changeNumber="CG-"+UUID.randomUUID().toString().replace("-","").substring(0,24);
        r.changeType="REPLACE"; r.changeStatus="COMPLETED"; r.requestedStoreId=storeId;
        r.handledByEmployeeId=employeeId; r.effectiveDate=date; r.amountDelta=delta; r.changedAt=now;
        r.changeReason="Draft revision; requires fresh contract confirmation";
        return snapshot(changes.saveAndFlush(r));
    }
    @Override public void link(long changeId,long itemId,String effect) {
        var r=new RentalChangeItemRow(); r.rentalChangeId=changeId; r.rentalItemId=itemId; r.effectCode=effect;
        effects.saveAndFlush(r);
    }
    @Override public StoredChange insertHandling(long contractId,String type,long employeeId,long storeId,LocalDate date,LocalDateTime now) {
        var r=new RentalChangeRow(); r.contractId=contractId; r.changeType=type; r.changeStatus="PROCESSING";
        r.changeNumber="CG-"+UUID.randomUUID().toString().replace("-","").substring(0,24);
        r.handledByEmployeeId=employeeId; r.requestedStoreId=storeId; r.effectiveDate=date; r.changedAt=now;
        return snapshot(changes.saveAndFlush(r));
    }
    @Override public List<Effect> effectsForContract(long contractId) {
        return changes.findByContractIdOrderByRentalChangeId(contractId).stream().peek(entityManager::refresh).flatMap(c ->
                effects.findByRentalChangeIdOrderByRentalChangeItemId(c.rentalChangeId).stream()
                        .map(e -> new Effect(c.rentalChangeId,e.rentalItemId,e.effectCode,c.changeStatus,c.requestedStoreId))).toList();
    }
    private StoredChange snapshot(RentalChangeRow r) { return new StoredChange(r.rentalChangeId,r.contractId,r.changeNumber,r.changeType,
            r.changeStatus,r.requestedByCustomerId,r.requestedStoreId,r.requestedAt,r.requestNote,r.handledByEmployeeId,
            r.effectiveDate,r.amountDelta,r.changedAt,effects.findByRentalChangeIdOrderByRentalChangeItemId(r.rentalChangeId).stream()
                    .filter(e -> e.effectCode.equals("REQUESTED_RETURN")).map(e -> e.rentalItemId).toList()); }
}
