package com.bsr.rental.application;

import com.bsr.audit.api.AuditAppender;
import com.bsr.equipment.api.EquipmentOperations;
import com.bsr.identity.api.*;
import com.bsr.platform.error.ApiException;
import com.bsr.rental.domain.*;
import com.bsr.scheduling.employee.api.EmployeeAccess;
import com.bsr.storetransfer.store.api.StoreAccess;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Each public action is a short committed stage, never a transaction around external waiting. */
@Service
public class AdjustmentService {
    private final ChangePersistence changes;
    private final RentalPersistence rentals;
    private final RentalService rentalService;
    private final EquipmentOperations equipment;
    private final Authorization authorization;
    private final StoreAccess stores;
    private final EmployeeAccess employees;
    private final AuditAppender audit;
    private final Clock clock;

    public AdjustmentService(ChangePersistence changes,RentalPersistence rentals,RentalService rentalService,
            EquipmentOperations equipment,Authorization authorization,StoreAccess stores,EmployeeAccess employees,AuditAppender audit,Clock clock) {
        this.changes=changes; this.rentals=rentals; this.rentalService=rentalService; this.equipment=equipment;
        this.authorization=authorization; this.stores=stores; this.employees=employees; this.audit=audit; this.clock=clock;
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ChangePersistence.StoredChange start(ActorContext actor,long contractId,long storeId,RentalChangeRequest.Type type) {
        if(type==null) throw new IllegalArgumentException("Adjustment type is required");
        employeeParents(actor,storeId);
        var contract=rentals.lock(contractId).orElseThrow(AdjustmentService::missing);
        requireActive(contract); requireNoOtherProcessing(contractId,-1); requireRentalDate(contract.contract());
        var c=changes.insertHandling(contractId,type.name(),actor.identityId(),storeId,today(),now());
        audit(actor,c,"RENTAL_ADJUSTMENT_STARTED"); return c;
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ChangePersistence.StoredChange receive(ActorContext actor,long changeId,List<RentalService.ReturnItem> returned) {
        var context=employeeLocked(actor,changeId);
        var c=begin(actor,context);
        if(c.type().equals("ADD")) throw conflict("An addition cannot remove existing items");
        if(c.amountDelta()!=null) throw conflict("A quoted adjustment is immutable; reject it before starting another");
        if(returned==null || returned.isEmpty()) throw new IllegalArgumentException("Select returned items and inspection outcomes");
        var allEffects=changes.effectsForContract(c.contractId());
        for(var r:returned) {
            if(r==null) throw new IllegalArgumentException("Return item is required");
            if(allEffects.stream().anyMatch(e -> e.itemId()==r.itemId() && e.changeId()!=c.id() && e.status().equals("COMPLETED") && outgoing(e.effect())))
                throw conflict("This item already has a completed formal reduction");
        }
        rentalService.returnItems(actor,c.contractId(),c.requestedStoreId(),returned);
        for(var r:returned) if(allEffects.stream().noneMatch(e -> e.changeId()==c.id() && e.itemId()==r.itemId() && outgoing(e.effect())))
            changes.link(c.id(),r.itemId(),c.type().equals("REPLACE")?"REPLACE_OUT":"RETURN");
        audit(actor,c,"RENTAL_ADJUSTMENT_RECEIVED"); return changes.find(c.id()).orElseThrow();
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ChangePersistence.StoredChange quote(ActorContext actor,long changeId,List<RentalService.Selection> additions) {
        var context=employeeLocked(actor,changeId);
        var c=begin(actor,context);
        if(c.amountDelta()!=null) throw conflict("The quoted content cannot be silently revised");
        var contract=rentals.find(c.contractId()).orElseThrow(AdjustmentService::missing);
        requireRentalDate(contract.contract());
        var outgoing=changes.effectsForContract(c.contractId()).stream().filter(e -> e.changeId()==c.id() && outgoing(e.effect())).toList();
        boolean adds=c.type().equals("ADD") || c.type().equals("REPLACE");
        if((!c.type().equals("ADD") && outgoing.isEmpty()) || (c.type().equals("ADD") && !outgoing.isEmpty()))
            throw conflict("Receive the formally removed items before quoting");
        if(!adds && additions!=null && !additions.isEmpty()) throw conflict("A reduction cannot add equipment");
        if(adds && !today().isBefore(contract.contract().rentalEndExclusive())) throw conflict("No rental day remains for added equipment");
        var chosen=adds?rentalService.observedSelection(actor,c.requestedStoreId(),additions,contract):List.<com.bsr.equipment.api.EquipmentSnapshot>of();
        BigDecimal delta=BigDecimal.ZERO;
        for(var e:outgoing) {
            var item=contract.items().stream().filter(i -> i.id()==e.itemId()).findFirst().orElseThrow();
            delta=delta.subtract(WholeDayChargeCalculator.returnRefund(item.dailyRateSnapshot(),contract.contract().rentalStartDate(),contract.contract().rentalDays(),today()));
        }
        for(var s:chosen) {
            var item=rentals.appendItem(c.contractId(),s.equipmentId(),s.currentDailyRate());
            changes.link(c.id(),item.id(),c.type().equals("REPLACE")?"REPLACE_IN":"ADD");
            delta=delta.add(WholeDayChargeCalculator.additionAmount(s.currentDailyRate(),contract.contract().rentalStartDate(),contract.contract().rentalDays(),today()));
        }
        if(!chosen.isEmpty()) {
            var locked=equipment.reserveForRental(actor,c.contractId(),c.requestedStoreId(),chosen.stream().map(com.bsr.equipment.api.EquipmentSnapshot::equipmentId).toList());
            for(var s:locked) {
                var observed=chosen.stream().filter(o -> o.equipmentId()==s.equipmentId()).findFirst().orElseThrow();
                if(s.currentDailyRate().compareTo(observed.currentDailyRate())!=0 || !s.equipmentCategory().equals(observed.equipmentCategory()) || !s.skillLevel().equals(observed.skillLevel()))
                    throw conflict("Equipment changed while quoting; select again");
            }
        }
        delta=delta.setScale(2,java.math.RoundingMode.HALF_UP);
        if(delta.precision()>12 || contract.contract().amountDue().add(delta).signum()<0) throw new IllegalArgumentException("Adjustment amount is outside the supported money range");
        changes.begin(c.id(),actor.identityId(),today(),now());
        var quoted=changes.quote(c.id(),delta);
        audit(actor,quoted,"RENTAL_ADJUSTMENT_QUOTED"); return quoted;
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ChangePersistence.StoredChange decide(ActorContext actor,long changeId,boolean confirm) {
        var observed=changes.find(changeId).orElseThrow(AdjustmentService::missing);
        var observedContract=rentals.find(observed.contractId()).orElseThrow(AdjustmentService::missing);
        authorization.require(actor,ActionPermission.CUSTOMER_RENTAL_READ,null);
        authorization.requireCustomerOwnership(actor,observedContract.contract().customerId());
        stores.lockForUse(observed.requestedStoreId());
        var contract=rentals.lock(observed.contractId()).orElseThrow(AdjustmentService::missing);
        authorization.requireCustomerOwnership(actor,contract.contract().customerId());
        var c=changes.find(changeId).orElseThrow(AdjustmentService::missing);
        if(!confirm) return reject(actor,c);
        if(!c.status().equals("PROCESSING") || c.amountDelta()==null) throw conflict("Only a complete quote can be confirmed");
        contract.contract().confirmAdjustment(c.amountDelta());
        changes.finish(changeId,"COMPLETED");
        rentals.createAdjustmentCharge(c.contractId(),changeId,c.amountDelta(),now());
        rentals.saveState(contract);
        var refreshed=rentals.find(c.contractId()).orElseThrow();
        if(refreshed.items().stream().anyMatch(i -> i.active() && i.status().equals("PENDING_ISSUE"))) refreshed.contract().resumeWithPendingEquipment();
        refreshed.contract().resolveReturnedContract(refreshed.charges().stream().anyMatch(ch -> !ch.status().equals("SETTLED")));
        rentals.saveState(refreshed);
        audit(actor,c,"RENTAL_ADJUSTMENT_CONFIRMED"); return changes.find(changeId).orElseThrow();
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ChangePersistence.StoredChange stop(ActorContext actor,long changeId) {
        return reject(actor,employeeLocked(actor,changeId).change());
    }

    @Transactional(readOnly=true)
    public Detail read(ActorContext actor,long id) {
        var c=changes.find(id).orElseThrow(AdjustmentService::missing);
        var contract=rentals.find(c.contractId()).orElseThrow(AdjustmentService::missing);
        if(actor.identityType()==IdentityType.CUSTOMER) {
            authorization.require(actor,ActionPermission.CUSTOMER_RENTAL_READ,null);
            authorization.requireCustomerOwnership(actor,contract.contract().customerId());
        } else authorization.require(actor,ActionPermission.EMPLOYEE_RENTAL_PROCESS,c.requestedStoreId());
        var effects=changes.effectsForContract(c.contractId()).stream().filter(e -> e.changeId()==c.id() && !e.effect().equals("REQUESTED_RETURN")).toList();
        return new Detail(c,contract.items(),effects,contract.charges().stream().filter(ch -> ch.changeId()!=null && ch.changeId()==id).toList());
    }
    public record Detail(ChangePersistence.StoredChange change,List<RentalPersistence.StoredItem> items,List<ChangePersistence.Effect> effects,List<RentalPersistence.Charge> charges) { }

    private ChangePersistence.StoredChange reject(ActorContext actor,ChangePersistence.StoredChange c) {
        if(!c.status().equals("SUBMITTED") && !c.status().equals("PROCESSING")) throw conflict("Only an unfinished adjustment may be rejected");
        var effects=changes.effectsForContract(c.contractId()).stream().filter(e -> e.changeId()==c.id() && incoming(e.effect())).toList();
        var contract=rentals.find(c.contractId()).orElseThrow();
        var result=changes.finish(c.id(),"REJECTED");
        if(!effects.isEmpty()) {
            var items=contract.items().stream().filter(i -> effects.stream().anyMatch(e -> e.itemId()==i.id())).toList();
            if(items.stream().anyMatch(i -> !i.status().equals("PENDING_ISSUE"))) throw conflict("Physical issues cannot be undone");
            var ids=items.stream().map(RentalPersistence.StoredItem::equipmentId).toList();
            if(actor.identityType()==IdentityType.CUSTOMER) equipment.releaseRejectedAddition(actor,c.contractId(),c.id(),c.requestedStoreId(),ids);
            else equipment.releaseBeforeIssue(actor,c.contractId(),c.requestedStoreId(),ids);
        }
        var refreshed=rentals.find(c.contractId()).orElseThrow();
        refreshed.contract().resolveReturnedContract(refreshed.charges().stream().anyMatch(ch -> !ch.status().equals("SETTLED")));
        rentals.saveState(refreshed); audit(actor,c,"RENTAL_ADJUSTMENT_REJECTED"); return result;
    }
    private Context employeeLocked(ActorContext actor,long id) {
        var observed=changes.find(id).orElseThrow(AdjustmentService::missing);
        employeeParents(actor,observed.requestedStoreId());
        var contract=rentals.lock(observed.contractId()).orElseThrow(AdjustmentService::missing);
        return new Context(changes.find(id).orElseThrow(AdjustmentService::missing),contract);
    }
    private ChangePersistence.StoredChange begin(ActorContext actor,Context context) {
        var c=context.change();
        if(c.status().equals("SUBMITTED")) {
            requireProcessable(c,context.contract()); requireNoOtherProcessing(c.contractId(),c.id()); requireRentalDate(context.contract().contract());
            c=changes.begin(c.id(),actor.identityId(),today(),now());
            audit(actor,c,"RENTAL_ADJUSTMENT_STARTED");
        }
        if(!c.status().equals("PROCESSING")) throw conflict("Adjustment is not being processed");
        return c;
    }
    private void requireNoOtherProcessing(long contractId,long excluded) {
        if(changes.forContract(contractId).stream().anyMatch(c -> c.id()!=excluded && c.status().equals("PROCESSING")))
            throw conflict("Finish the current adjustment first");
    }
    private void requireActive(RentalPersistence.StoredContract contract) {
        if(contract.contract().status()!=RentalContractStatus.ACTIVE) throw conflict("Only an active rental can begin adjustment");
    }
    private void requireProcessable(ChangePersistence.StoredChange change,RentalPersistence.StoredContract contract) {
        if(contract.contract().status()==RentalContractStatus.ACTIVE) return;
        boolean reopenedRefund=contract.contract().status()==RentalContractStatus.PENDING_SETTLEMENT
                && change.customerId()!=null
                && (change.type().equals("REMOVE") || change.type().equals("PARTIAL_RETURN"))
                && contract.charges().stream().allMatch(c -> c.status().equals("SETTLED"));
        if(!reopenedRefund) throw conflict("Only an active rental or a customer-requested ended refund can begin adjustment");
    }
    private void requireRentalDate(RentalContract contract) {
        if(today().isBefore(contract.rentalStartDate()) || today().isAfter(contract.rentalEndExclusive())) throw conflict("Adjustment is outside the agreed rental dates");
    }
    private void employeeParents(ActorContext actor,long storeId) {
        authorization.require(actor,ActionPermission.EMPLOYEE_RENTAL_PROCESS,storeId);
        stores.lockForUse(storeId); employees.lockForUse(actor.identityId());
    }
    private void audit(ActorContext actor,ChangePersistence.StoredChange c,String action) {
        audit.appendSuccess(actor,"RENTAL_CONTRACT",c.contractId(),action,"changeId="+c.id()+";storeId="+c.requestedStoreId());
    }
    private LocalDate today() { return LocalDate.now(clock.withZone(ZoneId.of("Europe/Oslo"))); }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC).withNano(0); }
    private static boolean outgoing(String effect) { return effect.equals("RETURN") || effect.equals("REPLACE_OUT"); }
    private static boolean incoming(String effect) { return effect.equals("ADD") || effect.equals("REPLACE_IN"); }
    private record Context(ChangePersistence.StoredChange change,RentalPersistence.StoredContract contract) { }
    private static ApiException missing() { return new ApiException(HttpStatus.NOT_FOUND,"RENTAL_CHANGE_NOT_FOUND","Adjustment not found"); }
    private static ApiException conflict(String message) { return new ApiException(HttpStatus.CONFLICT,"RENTAL_CHANGE_CONFLICT",message); }
}
