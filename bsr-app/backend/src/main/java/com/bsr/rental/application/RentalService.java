package com.bsr.rental.application;

import com.bsr.audit.api.AuditAppender;
import com.bsr.customerrequest.api.CustomerRequestOperations;
import com.bsr.equipment.api.EquipmentOperations;
import com.bsr.equipment.api.EquipmentSnapshot;
import com.bsr.equipment.api.InspectionOperations;
import com.bsr.equipment.api.InspectionOutcome;
import com.bsr.identity.api.ActionPermission;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.Authorization;
import com.bsr.identity.api.IdentityType;
import com.bsr.platform.error.ApiException;
import com.bsr.rental.application.RentalPersistence.StoredContract;
import com.bsr.rental.domain.RentalContract;
import com.bsr.rental.domain.RentalItem;
import com.bsr.scheduling.employee.api.EmployeeAccess;
import com.bsr.storetransfer.store.api.StoreAccess;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RentalService {
    private final RentalPersistence persistence;
    private final CustomerRequestOperations requests;
    private final EquipmentOperations equipment;
    private final Authorization authorization;
    private final StoreAccess stores;
    private final EmployeeAccess employees;
    private final AuditAppender audit;
    private final Clock clock;
    private final InspectionOperations inspections;
    private final ChangePersistence changes;

    public RentalService(RentalPersistence persistence, CustomerRequestOperations requests,
            EquipmentOperations equipment, Authorization authorization, StoreAccess stores,
            EmployeeAccess employees, AuditAppender audit, Clock clock, InspectionOperations inspections,ChangePersistence changes) {
        this.persistence = persistence;
        this.requests = requests;
        this.equipment = equipment;
        this.authorization = authorization;
        this.stores = stores;
        this.employees = employees;
        this.audit = audit;
        this.clock = clock;
        this.inspections = inspections;
        this.changes=changes;
    }

    public record Selection(long equipmentId, BigDecimal expectedDailyRate) { }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public StoredContract revise(ActorContext actor,long id,List<Long> replacedItemIds,List<Selection> additions) {
        var stored=employeeLocked(actor,id);
        if(stored.contract().status()!=com.bsr.rental.domain.RentalContractStatus.DRAFT)
            throw new IllegalStateException("Only an unconfirmed draft may be revised");
        if(replacedItemIds==null || replacedItemIds.isEmpty() || replacedItemIds.stream().anyMatch(i -> i==null || i<=0)
                || replacedItemIds.stream().distinct().count()!=replacedItemIds.size()) throw validation("Select distinct draft items");
        var removed=stored.items().stream().filter(i -> replacedItemIds.contains(i.id())).toList();
        if(removed.size()!=replacedItemIds.size() || removed.stream().anyMatch(i -> !i.active() || !i.status().equals("PENDING_ISSUE")))
            throw conflict("Only current unissued draft items may be replaced");
        var chosen=observedSelection(actor,stored.contract().serviceStoreId(),additions,stored);
        var retained=stored.items().stream().filter(RentalPersistence.StoredItem::active).filter(i -> !replacedItemIds.contains(i.id()));
        var amount=retained.map(RentalPersistence.StoredItem::dailyRateSnapshot).reduce(BigDecimal.ZERO,BigDecimal::add)
                .add(chosen.stream().map(EquipmentSnapshot::currentDailyRate).reduce(BigDecimal.ZERO,BigDecimal::add))
                .multiply(BigDecimal.valueOf(stored.contract().rentalDays())).setScale(2,java.math.RoundingMode.HALF_UP);
        var change=changes.insertDraftRevision(id,actor.identityId(),stored.contract().serviceStoreId(),
                LocalDate.now(clock.withZone(ZoneId.of("Europe/Oslo"))),amount.subtract(stored.contract().amountDue()),now());
        removed.forEach(i -> changes.link(change.id(),i.id(),"REPLACE_OUT"));
        chosen.forEach(s -> changes.link(change.id(),persistence.appendItem(id,s.equipmentId(),s.currentDailyRate()).id(),"REPLACE_IN"));
        var lockedEquipment=equipment.replaceBeforeIssue(actor,id,stored.contract().serviceStoreId(),
                removed.stream().map(RentalPersistence.StoredItem::equipmentId).toList(),chosen.stream().map(EquipmentSnapshot::equipmentId).toList());
        var oldFit=new HashMap<String,Integer>(); var newFit=new HashMap<String,Integer>();
        for(var s:lockedEquipment) {
            if(removed.stream().anyMatch(i -> i.equipmentId()==s.equipmentId())) oldFit.merge(s.equipmentCategory()+":"+s.skillLevel(),1,Integer::sum);
            else {
                newFit.merge(s.equipmentCategory()+":"+s.skillLevel(),1,Integer::sum);
                var observed=chosen.stream().filter(o -> o.equipmentId()==s.equipmentId()).findFirst().orElseThrow();
                if(s.currentDailyRate().compareTo(observed.currentDailyRate())!=0) throw conflict("Daily rate changed; reconfirm draft selection");
            }
        }
        if(!oldFit.equals(newFit)) throw conflict("Draft replacements must preserve accepted category and skill quantities");
        stored.contract().reviseDraftAmount(amount);
        persistence.reviseInitialCharge(id,amount,now());
        finish(actor,stored,"RENTAL_DRAFT_REVISED");
        return found(id);
    }

    List<EquipmentSnapshot> observedSelection(ActorContext actor,long storeId,List<Selection> selected,StoredContract contract) {
        if(selected==null || selected.isEmpty() || selected.stream().anyMatch(s -> s==null || s.equipmentId()<=0 || s.expectedDailyRate()==null)
                || selected.stream().map(Selection::equipmentId).distinct().count()!=selected.size()) throw validation("Select distinct available equipment with observed rates");
        var available=equipment.selectAvailable(actor,storeId,null,null);
        return selected.stream().map(s -> {
            if(contract.items().stream().anyMatch(i -> i.equipmentId()==s.equipmentId())) throw conflict("Equipment already occurs in contract history");
            var found=available.stream().filter(e -> e.equipmentId()==s.equipmentId()).findFirst().orElseThrow(() -> conflict("Equipment is not available"));
            if(found.currentDailyRate().compareTo(s.expectedDailyRate())!=0) throw conflict("Observed rate changed; select again");
            return found;
        }).toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StoredContract create(ActorContext actor, long storeId, long requestId, List<Selection> selections) {
        employeeParents(actor, storeId, ActionPermission.EMPLOYEE_RENTAL_PROCESS);
        var request = requests.lockAcceptedForConversion(actor, requestId);
        if (request.serviceStoreId() != storeId) throw conflict("Request belongs to another store.");
        if (selections == null || selections.isEmpty()
                || selections.stream().anyMatch(s -> s == null || s.equipmentId() <= 0 || s.expectedDailyRate() == null)
                || selections.stream().map(Selection::equipmentId).distinct().count() != selections.size()) {
            throw validation("Select distinct concrete equipment with observed daily rates.");
        }
        Map<Long, EquipmentSnapshot> available = new HashMap<>();
        equipment.selectAvailable(actor, storeId, null, null).forEach(s -> available.put(s.equipmentId(), s));
        List<EquipmentSnapshot> chosen = selections.stream().map(selection -> {
            var snapshot = available.get(selection.equipmentId());
            if (snapshot == null || snapshot.currentDailyRate().compareTo(selection.expectedDailyRate()) != 0)
                throw conflict("Equipment availability or daily rate changed; select and confirm again.");
            if (!snapshot.skillLevel().equals(request.skillLevel())) throw conflict("Equipment skill does not match request.");
            return snapshot;
        }).toList();
        Map<String, Integer> required = new HashMap<>();
        request.items().forEach(item -> required.merge(item.equipmentCategory(), item.quantity(), Integer::sum));
        Map<String, Integer> actual = new HashMap<>();
        chosen.forEach(item -> actual.merge(item.equipmentCategory(), 1, Integer::sum));
        if (!required.equals(actual)) throw conflict("Concrete equipment must match accepted category quantities.");
        var contract = RentalContract.createDraft("RC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24),
                requestId, request.customerId(), actor.identityId(), storeId,
                LocalDate.now(clock.withZone(ZoneId.of("Europe/Oslo"))), request.rentalStartDate(), request.rentalDays(),
                chosen.stream().map(s -> RentalItem.pendingIssue(s.equipmentId(), s.currentDailyRate())).toList());
        StoredContract stored = persistence.insert(contract, now());
        var locked = equipment.reserveForRental(actor, stored.id(), storeId,
                chosen.stream().map(EquipmentSnapshot::equipmentId).toList());
        for (var authoritative : locked) {
            var observed = available.get(authoritative.equipmentId());
            if (observed == null || authoritative.currentStoreId() != storeId
                    || !authoritative.equipmentCategory().equals(observed.equipmentCategory())
                    || !authoritative.skillLevel().equals(observed.skillLevel())
                    || authoritative.currentDailyRate().compareTo(observed.currentDailyRate()) != 0)
                throw conflict("Equipment changed while creating contract; reconfirm selection.");
        }
        requests.markConverted(actor, requestId, stored.id());
        audit.appendSuccess(actor, "RENTAL_CONTRACT", stored.id(), "RENTAL_CONTRACT_CREATED", "storeId=" + storeId);
        return stored;
    }

    @Transactional(readOnly = true)
    public StoredContract read(ActorContext actor, long id) {
        var contract = found(id);
        readScope(actor, contract);
        return contract;
    }
    @Transactional(readOnly = true)
    public List<StoredContract> listCustomer(ActorContext actor) {
        authorization.require(actor, ActionPermission.CUSTOMER_RENTAL_READ, null);
        authorization.requireCustomerOwnership(actor, actor.identityId());
        return persistence.forCustomer(actor.identityId());
    }
    @Transactional(readOnly = true)
    public List<StoredContract> listEmployee(ActorContext actor, long storeId) {
        authorization.require(actor, ActionPermission.EMPLOYEE_RENTAL_PROCESS, storeId);
        return persistence.forStore(storeId);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StoredContract submit(ActorContext actor, long id) {
        var stored = employeeLocked(actor, id);
        stored.contract().submitForCustomerConfirmation();
        return finish(actor, stored, "RENTAL_CONTRACT_SUBMITTED");
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StoredContract decide(ActorContext actor, long id, boolean confirm) {
        authorization.require(actor, ActionPermission.CUSTOMER_RENTAL_READ, null);
        var observed = found(id);
        authorization.requireCustomerOwnership(actor, observed.contract().customerId());
        var stored = locked(id);
        authorization.requireCustomerOwnership(actor, stored.contract().customerId());
        if (confirm) stored.contract().confirmByCustomer(); else stored.contract().rejectByCustomer();
        return finish(actor, stored, confirm ? "RENTAL_CONTRACT_CONFIRMED" : "RENTAL_CONTRACT_REJECTED");
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StoredContract cancel(ActorContext actor, long id) {
        var stored = employeeLocked(actor, id);
        stored.contract().cancelBeforeIssue();
        equipment.releaseBeforeIssue(actor, id, stored.contract().serviceStoreId(),
                stored.items().stream().filter(RentalPersistence.StoredItem::active).map(RentalPersistence.StoredItem::equipmentId).toList());
        return finish(actor, stored, "RENTAL_CONTRACT_CANCELLED");
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StoredContract issue(ActorContext actor, long id, List<Long> itemIds) {
        var stored = employeeLocked(actor, id);
        if (itemIds == null || itemIds.isEmpty() || itemIds.stream().anyMatch(i -> i == null || i <= 0)
                || itemIds.stream().distinct().count() != itemIds.size()) throw validation("Select distinct pending items.");
        var chosen = stored.items().stream().filter(i -> itemIds.contains(i.id())).toList();
        if (chosen.size() != itemIds.size() || chosen.stream().anyMatch(i -> !i.active() || !i.status().equals("PENDING_ISSUE")))
            throw conflict("Only pending items of this contract may be issued.");
        if (stored.charges().stream().anyMatch(c -> !c.type().equals("REFUND") && !c.status().equals("SETTLED")))
            throw conflict("Payment must be successfully recorded before physical issue.");
        stored.contract().recordPhysicalIssue();
        equipment.issue(actor, id, stored.contract().serviceStoreId(), chosen.stream().map(RentalPersistence.StoredItem::equipmentId).toList());
        persistence.markIssued(id, itemIds, now());
        finish(actor, stored, "RENTAL_ITEMS_ISSUED");
        return found(id);
    }

    public record ReturnItem(long itemId, InspectionOutcome outcome, String faultDescription) { }
    public record ReturnReceipt(long contractId, String status, List<RentalPersistence.StoredItem> items) { }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ReturnReceipt issueAtStore(ActorContext actor,long id,long storeId,List<Long> itemIds) {
        employeeParents(actor,storeId,ActionPermission.EMPLOYEE_RENTAL_PROCESS);
        var stored=locked(id);
        if(itemIds==null || itemIds.isEmpty() || itemIds.stream().anyMatch(i -> i==null || i<=0)
                || itemIds.stream().distinct().count()!=itemIds.size()) throw validation("Select distinct pending items");
        var selected=stored.items().stream().filter(i -> itemIds.contains(i.id())).toList();
        var effects=changes.effectsForContract(id);
        if(selected.size()!=itemIds.size() || selected.stream().anyMatch(i -> !i.active() || !i.status().equals("PENDING_ISSUE")
                || effects.stream().noneMatch(e -> e.itemId()==i.id() && e.status().equals("COMPLETED") && e.storeId()!=null && e.storeId()==storeId
                    && (e.effect().equals("ADD") || e.effect().equals("REPLACE_IN")))))
            throw conflict("Only confirmed incoming items at this handling store may be issued");
        for(var item:selected) {
            var effect=effects.stream().filter(e -> e.itemId()==item.id() && e.status().equals("COMPLETED") && (e.effect().equals("ADD") || e.effect().equals("REPLACE_IN"))).findFirst().orElseThrow();
            if(stored.charges().stream().anyMatch(c -> c.changeId()!=null && c.changeId()==effect.changeId() && !c.type().equals("REFUND") && !c.status().equals("SETTLED")))
                throw conflict("Successful supplement result is required before issue");
        }
        stored.contract().recordPhysicalIssue();
        equipment.issue(actor,id,storeId,selected.stream().map(RentalPersistence.StoredItem::equipmentId).toList());
        persistence.markIssued(id,itemIds,now()); finish(actor,stored,"RENTAL_ITEMS_ISSUED");
        var reloaded=found(id); return new ReturnReceipt(id,reloaded.contract().status().name(),reloaded.items());
    }

    /** A receiving-store employee gets a minimal receipt, not the original store's financial/customer data. */
    @Transactional(readOnly = true)
    public ReturnReceipt returnLookup(ActorContext actor, long id, long returnStoreId) {
        authorization.require(actor, ActionPermission.EMPLOYEE_RETURN_AND_INSPECT, returnStoreId);
        var contract=found(id);
        return new ReturnReceipt(id,contract.contract().status().name(),contract.items());
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReturnReceipt returnItems(ActorContext actor, long id, long storeId, List<ReturnItem> returns) {
        employeeParents(actor,storeId,ActionPermission.EMPLOYEE_RETURN_AND_INSPECT);
        var contract=locked(id);
        if(returns==null || returns.isEmpty() || returns.stream().anyMatch(r -> r==null || r.itemId()<=0 || r.outcome()==null)
                || returns.stream().map(ReturnItem::itemId).distinct().count()!=returns.size())
            throw validation("Select distinct contract items and inspection results.");
        var selected=contract.items().stream().filter(i -> returns.stream().anyMatch(r -> r.itemId()==i.id()))
                .sorted(java.util.Comparator.comparingLong(RentalPersistence.StoredItem::equipmentId)).toList();
        if(selected.size()!=returns.size()) throw conflict("Returned item does not belong to this contract.");
        for(var item:selected) {
            // Real duplicate return: preserve original timestamp, store and inspection; no second fact.
            if(item.status().equals("RETURNED")) continue;
            if(!item.status().equals("RENTED")) throw conflict("Unissued items cannot be physically returned.");
            var command=returns.stream().filter(r -> r.itemId()==item.id()).findFirst().orElseThrow();
            equipment.receiveReturn(actor,item.id(),item.equipmentId(),storeId);
            inspections.inspectRentalReturn(actor,item.id(),item.equipmentId(),command.outcome(),command.faultDescription());
            persistence.markReturned(item.id(),storeId,now());
            contract.contract().receivePhysicalReturn(item.equipmentId(),contract.charges().stream().anyMatch(c -> !c.status().equals("SETTLED"))
                    || changes.forContract(id).stream().anyMatch(c -> c.status().equals("PROCESSING")));
            audit.appendSuccess(actor,"RENTAL_CONTRACT",id,"RENTAL_ITEM_RETURNED","itemId="+item.id()+";returnStoreId="+storeId);
        }
        persistence.saveState(contract);
        var reloaded=found(id);
        return new ReturnReceipt(id,reloaded.contract().status().name(),reloaded.items());
    }

    private StoredContract employeeLocked(ActorContext actor, long id) {
        var observed = found(id);
        employeeParents(actor, observed.contract().serviceStoreId(), ActionPermission.EMPLOYEE_RENTAL_PROCESS);
        return locked(id);
    }
    private void employeeParents(ActorContext actor, long storeId, ActionPermission permission) {
        authorization.require(actor, permission, storeId);
        stores.lockForUse(storeId);
        employees.lockForUse(actor.identityId());
    }
    private void readScope(ActorContext actor, StoredContract contract) {
        if (actor.identityType() == IdentityType.CUSTOMER) {
            authorization.require(actor, ActionPermission.CUSTOMER_RENTAL_READ, null);
            authorization.requireCustomerOwnership(actor, contract.contract().customerId());
        } else authorization.require(actor, ActionPermission.EMPLOYEE_RENTAL_PROCESS, contract.contract().serviceStoreId());
    }
    private StoredContract finish(ActorContext actor, StoredContract stored, String action) {
        persistence.saveState(stored);
        audit.appendSuccess(actor, "RENTAL_CONTRACT", stored.id(), action, "storeId=" + stored.contract().serviceStoreId());
        return stored;
    }
    private StoredContract found(long id) { return persistence.find(id).orElseThrow(() -> missing()); }
    private StoredContract locked(long id) { return persistence.lock(id).orElseThrow(() -> missing()); }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).withNano(0); }
    private static ApiException missing() { return new ApiException(HttpStatus.NOT_FOUND, "RENTAL_CONTRACT_NOT_FOUND", "Contract not found."); }
    private static ApiException validation(String message) { return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message); }
    private static ApiException conflict(String message) { return new ApiException(HttpStatus.CONFLICT, "RENTAL_STATE_CONFLICT", message); }
}
