package com.bsr.rental.application;

import com.bsr.audit.api.AuditAppender;
import com.bsr.identity.api.ActionPermission;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.Authorization;
import com.bsr.identity.api.IdentityType;
import com.bsr.platform.error.ApiException;
import com.bsr.rental.domain.RentalChangeRequest;
import com.bsr.storetransfer.store.api.StoreAccess;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChangeRequestService {
    private final ChangePersistence changes;
    private final RentalPersistence rentals;
    private final Authorization authorization;
    private final StoreAccess stores;
    private final AuditAppender audit;
    private final Clock clock;
    public ChangeRequestService(ChangePersistence changes,RentalPersistence rentals,Authorization authorization,
            StoreAccess stores,AuditAppender audit,Clock clock) {
        this.changes=changes; this.rentals=rentals; this.authorization=authorization; this.stores=stores; this.audit=audit; this.clock=clock;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ChangePersistence.StoredChange submit(ActorContext actor,long contractId,long storeId,RentalChangeRequest.Type type,String note) {
        return submit(actor,contractId,storeId,type,note,List.of());
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ChangePersistence.StoredChange submit(ActorContext actor,long contractId,long storeId,RentalChangeRequest.Type type,String note,List<Long> requestedReturnItemIds) {
        authorization.require(actor,ActionPermission.CUSTOMER_CHANGE_REQUEST,null);
        var observed=rentals.find(contractId).orElseThrow(ChangeRequestService::missing);
        authorization.requireCustomerOwnership(actor,observed.contract().customerId());
        stores.lockForUse(storeId);
        var contract=rentals.lock(contractId).orElseThrow(ChangeRequestService::missing);
        authorization.requireCustomerOwnership(actor,contract.contract().customerId());
        var requested = requestedReturnItemIds == null ? List.<Long>of() : requestedReturnItemIds;
        if (requested.stream().anyMatch(id -> id == null || id <= 0)
                || requested.stream().distinct().count() != requested.size()
                || type == RentalChangeRequest.Type.ADD && !requested.isEmpty()) {
            throw new IllegalArgumentException("Select distinct original contract items for a reduction or replacement request.");
        }
        var eligible = contract.items().stream().filter(i -> i.active() && (i.status().equals("RENTED") || i.status().equals("RETURNED")))
                .map(RentalPersistence.StoredItem::id).collect(java.util.stream.Collectors.toSet());
        if (!eligible.containsAll(requested)) {
            throw new IllegalArgumentException("Requested return items must belong to the current contract and remain eligible.");
        }
        var request=new RentalChangeRequest("CG-"+UUID.randomUUID().toString().replace("-","").substring(0,24),contractId,type,
                actor.identityId(),storeId,LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC).withNano(0),note);
        boolean endedRefund=eligibleEndedRefund(contract,type);
        request.requireRequestableContract(contract.contract(),endedRefund);
        if(endedRefund) {
            contract.contract().keepPendingAdjustment();
            rentals.saveState(contract);
        }
        var saved=changes.insertRequest(request);
        for (long itemId : requested) changes.link(saved.id(),itemId,"REQUESTED_RETURN");
        audit.appendSuccess(actor,"RENTAL_CONTRACT",contractId,"RENTAL_CHANGE_REQUESTED","changeId="+saved.id()+";requestedStoreId="+storeId);
        return changes.find(saved.id()).orElseThrow();
    }
    @Transactional(readOnly=true)
    public List<ChangePersistence.StoredChange> forContract(ActorContext actor,long id) {
        var contract=rentals.find(id).orElseThrow(ChangeRequestService::missing);
        if(actor.identityType()==IdentityType.CUSTOMER) {
            authorization.require(actor,ActionPermission.CUSTOMER_RENTAL_READ,null);
            authorization.requireCustomerOwnership(actor,contract.contract().customerId());
        } else authorization.require(actor,ActionPermission.EMPLOYEE_RENTAL_PROCESS,contract.contract().serviceStoreId());
        return changes.forContract(id);
    }
    @Transactional(readOnly=true)
    public List<ChangePersistence.StoredChange> queue(ActorContext actor,long storeId) {
        authorization.require(actor,ActionPermission.EMPLOYEE_RENTAL_PROCESS,storeId);
        return changes.forRequestedStore(storeId);
    }
    private boolean eligibleEndedRefund(RentalPersistence.StoredContract contract,RentalChangeRequest.Type type) {
        if(contract.contract().status()!=com.bsr.rental.domain.RentalContractStatus.ENDED
                || (type!=RentalChangeRequest.Type.REMOVE && type!=RentalChangeRequest.Type.PARTIAL_RETURN)) return false;
        LocalDate today=LocalDate.now(clock.withZone(ZoneId.of("Europe/Oslo")));
        if(today.isBefore(contract.contract().rentalStartDate()) || !today.isBefore(contract.contract().rentalEndExclusive())) return false;
        var completed=changes.effectsForContract(contract.id()).stream()
                .filter(e -> e.status().equals("COMPLETED") && (e.effect().equals("RETURN") || e.effect().equals("REPLACE_OUT")))
                .map(ChangePersistence.Effect::itemId).collect(java.util.stream.Collectors.toSet());
        return contract.items().stream().anyMatch(i -> i.active() && i.status().equals("RETURNED") && !completed.contains(i.id()));
    }
    private static ApiException missing() { return new ApiException(HttpStatus.NOT_FOUND,"RENTAL_CONTRACT_NOT_FOUND","Contract not found."); }
}
