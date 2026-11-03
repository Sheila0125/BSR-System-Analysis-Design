package com.bsr.rental.infrastructure.persistence;

import com.bsr.rental.application.RentalPersistence;
import com.bsr.rental.domain.RentalContract;
import com.bsr.rental.domain.RentalContractStatus;
import com.bsr.rental.domain.RentalItem;
import com.bsr.rental.domain.RentalItemStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

@Repository
public class JpaRentalPersistence implements RentalPersistence {
    private final RentalContractRepository contracts;
    private final RentalItemRepository items;
    private final RentalChargeRepository charges;
    private final EntityManager entityManager;
    private final com.bsr.rental.application.ChangePersistence changes;
    private final PaymentTransactionRepository payments;

    public JpaRentalPersistence(RentalContractRepository contracts, RentalItemRepository items,
            RentalChargeRepository charges, EntityManager entityManager,
            com.bsr.rental.application.ChangePersistence changes,PaymentTransactionRepository payments) {
        this.contracts = contracts;
        this.items = items;
        this.charges = charges;
        this.entityManager = entityManager;
        this.changes=changes; this.payments=payments;
    }

    @Override
    public StoredContract insert(RentalContract contract, LocalDateTime now) {
        RentalContractRow row = new RentalContractRow();
        row.contractNumber = contract.contractNumber();
        row.sourceRequestId = contract.sourceRequestId();
        row.customerId = contract.customerId();
        row.createdByEmployeeId = contract.createdByEmployeeId();
        row.serviceStoreId = contract.serviceStoreId();
        row.contractDate = contract.contractDate();
        row.rentalStartDate = contract.rentalStartDate();
        row.rentalDays = contract.rentalDays();
        row.amountDue = contract.amountDue();
        row.contractStatus = contract.status().name();
        contracts.saveAndFlush(row);
        for (RentalItem item : contract.items()) {
            RentalItemRow detail = new RentalItemRow();
            detail.contractId = row.contractId;
            detail.equipmentId = item.equipmentId();
            detail.dailyRateSnapshot = item.dailyRateSnapshot();
            detail.itemStatus = item.status().name();
            items.save(detail);
        }
        items.flush(); // Frozen equipment API verifies these real associations with JDBC.
        if (contract.amountDue().signum() > 0) {
            RentalChargeRow charge = new RentalChargeRow();
            charge.contractId = row.contractId;
            charge.chargeType = "INITIAL";
            charge.amount = contract.amountDue();
            charge.chargeStatus = "PENDING";
            charge.createdAt = now;
            charges.saveAndFlush(charge);
        }
        return stored(row);
    }

    @Override public Optional<StoredContract> find(long id) { return contracts.findById(id).map(this::stored); }
    @Override public Optional<StoredContract> lock(long id) {
        return contracts.lock(id).map(row -> {
            entityManager.refresh(row, LockModeType.PESSIMISTIC_WRITE);
            items.findByContractIdOrderByRentalItemId(id).forEach(entityManager::refresh);
            charges.findByContractIdOrderByRentalChargeId(id).forEach(entityManager::refresh);
            return stored(row);
        });
    }
    @Override public List<StoredContract> forCustomer(long id) {
        return contracts.findByCustomerIdOrderByContractIdDesc(id).stream().map(this::stored).toList();
    }
    @Override public List<StoredContract> forStore(long id) {
        return contracts.findByServiceStoreIdOrderByContractIdDesc(id).stream().map(this::stored).toList();
    }
    @Override public void saveState(StoredContract stored) {
        RentalContractRow row = contracts.findById(stored.id()).orElseThrow();
        row.contractStatus = stored.contract().status().name();
        row.amountDue = stored.contract().amountDue();
        contracts.saveAndFlush(row);
    }
    @Override public void markIssued(long contractId, List<Long> ids, LocalDateTime now) {
        for (RentalItemRow row : items.findByContractIdOrderByRentalItemId(contractId)) {
            if (ids.contains(row.rentalItemId)) {
                row.itemStatus = "RENTED";
                row.checkedOutAt = now;
                items.save(row);
            }
        }
        items.flush();
    }
    @Override public void markReturned(long id, long storeId, LocalDateTime now) {
        var row=items.findById(id).orElseThrow();
        row.itemStatus="RETURNED"; row.returnedAt=now; row.returnStoreId=storeId;
        items.saveAndFlush(row);
    }

    @Override public StoredItem appendItem(long contractId,long equipmentId,java.math.BigDecimal rate) {
        var r=new RentalItemRow(); r.contractId=contractId; r.equipmentId=equipmentId;
        r.dailyRateSnapshot=rate; r.itemStatus="PENDING_ISSUE";
        items.saveAndFlush(r);
        return new StoredItem(r.rentalItemId,equipmentId,rate,r.itemStatus,null,null,null,true);
    }
    @Override public void createAdjustmentCharge(long contractId,long changeId,java.math.BigDecimal delta,LocalDateTime now) {
        if(delta.signum()==0) return;
        var r=new RentalChargeRow(); r.contractId=contractId; r.rentalChangeId=changeId;
        r.amount=delta.abs(); r.chargeType=delta.signum()>0?"SUPPLEMENT":"REFUND";
        r.chargeStatus=delta.signum()>0?"PENDING":"PENDING_REFUND"; r.createdAt=now;
        charges.saveAndFlush(r);
    }
    @Override public void reviseInitialCharge(long contractId,java.math.BigDecimal amount,LocalDateTime now) {
        var initial=charges.findByContractIdOrderByRentalChargeId(contractId).stream().filter(c -> c.chargeType.equals("INITIAL")).findFirst();
        if(initial.isPresent()) {
            var c=initial.get();
            if(!payments.findByRentalChargeIdOrderByPaymentTransactionId(c.rentalChargeId).isEmpty())
                throw new IllegalStateException("Recorded external history prevents draft charge revision");
            if(!contracts.findById(contractId).orElseThrow().contractStatus.equals("DRAFT"))
                throw new IllegalStateException("Only an unconfirmed draft fee may be revised");
            if(amount.signum()==0) {
                // User approval 2026-10-10: unconfirmed, no external facts only.
                charges.delete(c); charges.flush(); return;
            }
            c.amount=amount; charges.saveAndFlush(c);
        } else if(amount.signum()>0) {
            var c=new RentalChargeRow(); c.contractId=contractId; c.chargeType="INITIAL";
            c.amount=amount; c.chargeStatus="PENDING"; c.createdAt=now; charges.saveAndFlush(c);
        }
    }

    private StoredContract stored(RentalContractRow row) {
        var effects=changes.effectsForContract(row.contractId);
        List<StoredItem> details = items.findByContractIdOrderByRentalItemId(row.contractId).stream()
                .map(item -> new StoredItem(item.rentalItemId, item.equipmentId, item.dailyRateSnapshot,
                        item.itemStatus, item.checkedOutAt, item.returnedAt, item.returnStoreId,
                        effects.stream().noneMatch(e -> e.itemId()==item.rentalItemId &&
                                ((e.status().equals("REJECTED") && (e.effect().equals("ADD") || e.effect().equals("REPLACE_IN")))
                                || (e.status().equals("PROCESSING") && (e.effect().equals("ADD") || e.effect().equals("REPLACE_IN")))
                                || (e.status().equals("COMPLETED") && item.itemStatus.equals("PENDING_ISSUE")
                                    && (e.effect().equals("RETURN") || e.effect().equals("REPLACE_OUT"))))))).toList();
        RentalContract contract = RentalContract.restore(row.contractNumber, row.sourceRequestId,
                row.customerId, row.createdByEmployeeId, row.serviceStoreId, row.contractDate,
                row.rentalStartDate, row.rentalDays, details.stream().filter(StoredItem::active).map(item -> RentalItem.restore(
                        item.equipmentId(), item.dailyRateSnapshot(), RentalItemStatus.valueOf(item.status()))).toList(),
                RentalContractStatus.valueOf(row.contractStatus),row.amountDue);
        return new StoredContract(row.contractId, contract, details,
                charges.findByContractIdOrderByRentalChargeId(row.contractId).stream()
                        .map(charge -> new Charge(charge.rentalChargeId, charge.contractId,
                                charge.rentalChangeId, charge.chargeType, charge.amount, charge.chargeStatus,
                                charge.createdAt)).toList());
    }
}
