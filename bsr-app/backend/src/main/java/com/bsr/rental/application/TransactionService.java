package com.bsr.rental.application;

import com.bsr.audit.api.AuditAppender;
import com.bsr.identity.api.ActionPermission;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.Authorization;
import com.bsr.platform.error.ApiException;
import com.bsr.rental.application.TransactionPersistence.RecordedTransaction;
import com.bsr.rental.domain.ExternalTransactionResult;
import com.bsr.rental.domain.RentalContractStatus;
import com.bsr.scheduling.employee.api.EmployeeAccess;
import com.bsr.storetransfer.store.api.StoreAccess;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import com.bsr.identity.api.IdentityType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TransactionService {
    private final TransactionPersistence persistence;
    private final RentalPersistence rentals;
    private final Authorization authorization;
    private final StoreAccess stores;
    private final EmployeeAccess employees;
    private final AuditAppender audit;
    private final Clock clock;
    private final ChangePersistence changes;
    private final TransactionTemplate write;
    private final TransactionTemplate read;

    public TransactionService(TransactionPersistence persistence, RentalPersistence rentals, Authorization authorization,
            StoreAccess stores, EmployeeAccess employees, AuditAppender audit, Clock clock, PlatformTransactionManager manager,ChangePersistence changes) {
        this.persistence=persistence; this.rentals=rentals; this.authorization=authorization;
        this.stores=stores; this.employees=employees; this.audit=audit; this.clock=clock;
        this.changes=changes;
        write=new TransactionTemplate(manager);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read=new TransactionTemplate(manager); read.setReadOnly(true);
        read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /** The failed insert transaction is fully rolled back BEFORE any uniqueness recovery read. */
    public RecordedTransaction record(ActorContext actor, ExternalTransactionResult result) {
        if (result.externalId().startsWith("SIM-")) throw conflict("SIM- is reserved for clearly identified virtual payments.");
        try { return write.execute(ignored -> recordInTransaction(actor,result)); }
        catch (DataIntegrityViolationException exception) {
            if(!externalIdentifierViolation(exception)) throw exception;
            return read.execute(ignored -> {
                authorizedCharge(actor,result.chargeId());
                var existing=persistence.byExternalId(result.externalId());
                if (existing.isEmpty()) throw exception;
                return sameOrConflict(existing.get(),result);
            });
        }
    }

    /** Course demonstration only: no payment provider, card input, callback or real money. */
    public RecordedTransaction simulate(ActorContext actor,long chargeId,String key,String outcome) {
        authorization.require(actor,ActionPermission.CUSTOMER_PAYMENT_SIMULATE,null);
        if (key==null || !key.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw new IllegalArgumentException("A UUID request key is required for virtual payment retries.");
        var status=ExternalTransactionResult.Status.valueOf(outcome==null ? "" : outcome);
        if(status==ExternalTransactionResult.Status.PENDING_CONFIRMATION)
            throw new IllegalArgumentException("Select a successful or failed virtual payment outcome.");
        String externalId="SIM-"+UUID.fromString(key);
        try { return write.execute(ignored -> simulateInTransaction(actor,chargeId,externalId,status)); }
        catch(DataIntegrityViolationException exception) {
            if(!externalIdentifierViolation(exception)) throw exception;
            return read.execute(ignored -> {
                customerCharge(actor,chargeId);
                var existing=persistence.byExternalId(externalId);
                if(existing.isEmpty()) throw exception;
                return sameSimulationOrConflict(existing.get(),actor,chargeId,status);
            });
        }
    }

    private RecordedTransaction simulateInTransaction(ActorContext actor,long chargeId,String externalId,
            ExternalTransactionResult.Status outcome) {
        var observed=customerCharge(actor,chargeId);
        var observedContract=rentals.find(observed.contractId()).orElseThrow(TransactionService::missing);
        stores.lockForUse(paymentStore(observed,observedContract.contract().serviceStoreId()));
        var contract=rentals.lock(observed.contractId()).orElseThrow(TransactionService::missing);
        authorization.requireCustomerOwnership(actor,contract.contract().customerId());
        var charge=contract.charges().stream().filter(c -> c.id()==chargeId).findFirst().orElseThrow(TransactionService::missing);
        var existing=persistence.byExternalId(externalId);
        if(existing.isPresent()) return sameSimulationOrConflict(existing.get(),actor,chargeId,outcome);
        if(charge.type().equals("REFUND")) throw conflict("Refunds must be handled by the store; this action simulates customer payments only.");
        if(contract.contract().status()==RentalContractStatus.DRAFT
                || contract.contract().status()==RentalContractStatus.PENDING_CONFIRMATION
                || contract.contract().status()==RentalContractStatus.CANCELLED)
            throw conflict("Confirm the contract before making a virtual payment.");
        var remaining=charge.amount().subtract(persistence.successfulTotal(chargeId));
        if(remaining.signum()<=0) throw conflict("This charge is already paid. Refresh the invoice.");
        var now=LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC).withNano(0);
        var type=charge.type().equals("INITIAL") ? ExternalTransactionResult.Type.PAYMENT : ExternalTransactionResult.Type.SUPPLEMENT;
        var result=new ExternalTransactionResult(chargeId,type,externalId,remaining,outcome,now);
        result.requireMatchesCharge(charge.type(),remaining);
        var recorded=persistence.insertSimulated(result,actor.accountId(),now);
        return finish(actor,contract,charge,recorded,"SIMULATED_PAYMENT_RECORDED");
    }

    private RentalPersistence.Charge customerCharge(ActorContext actor,long chargeId) {
        if(actor.identityType()!=IdentityType.CUSTOMER)
            throw new ApiException(HttpStatus.FORBIDDEN,"ROLE_REQUIRED","Only the contract customer can simulate a payment.");
        authorization.require(actor,ActionPermission.CUSTOMER_PAYMENT_SIMULATE,null);
        var charge=persistence.charge(chargeId).orElseThrow(TransactionService::missing);
        var contract=rentals.find(charge.contractId()).orElseThrow(TransactionService::missing).contract();
        authorization.requireCustomerOwnership(actor,contract.customerId());
        return charge;
    }

    private RecordedTransaction sameSimulationOrConflict(RecordedTransaction existing,ActorContext actor,long chargeId,
            ExternalTransactionResult.Status outcome) {
        if(!existing.source().equals("SIMULATED") || !Long.valueOf(actor.accountId()).equals(existing.simulatedByAccountId())
                || existing.result().chargeId()!=chargeId || existing.result().status()!=outcome)
            throw conflict("The virtual payment request key was already used for a different operation.");
        return existing;
    }
    public List<RecordedTransaction> list(ActorContext actor,long chargeId) {
        return read.execute(ignored -> {
            var charge=persistence.charge(chargeId).orElseThrow(TransactionService::missing);
            var contract=rentals.find(charge.contractId()).orElseThrow(TransactionService::missing).contract();
            if (actor.identityType()==com.bsr.identity.api.IdentityType.CUSTOMER) {
                authorization.require(actor,ActionPermission.CUSTOMER_RENTAL_READ,null);
                authorization.requireCustomerOwnership(actor,contract.customerId());
            } else authorization.require(actor,ActionPermission.EMPLOYEE_PAYMENT_RECORD,paymentStore(charge,contract.serviceStoreId()));
            return persistence.forCharge(chargeId);
        });
    }
    private RecordedTransaction recordInTransaction(ActorContext actor,ExternalTransactionResult result) {
        var observed=authorizedCharge(actor,result.chargeId());
        var observedContract=rentals.find(observed.contractId()).orElseThrow(TransactionService::missing);
        long storeId=paymentStore(observed,observedContract.contract().serviceStoreId());
        stores.lockForUse(storeId); employees.lockForUse(actor.identityId());
        var contract=rentals.lock(observed.contractId()).orElseThrow(TransactionService::missing);
        var charge=contract.charges().stream().filter(c -> c.id()==result.chargeId()).findFirst().orElseThrow(TransactionService::missing);
        var existing=persistence.byExternalId(result.externalId());
        if(existing.isPresent()) return sameOrConflict(existing.get(),result);
        if(contract.contract().status()==RentalContractStatus.DRAFT
                || contract.contract().status()==RentalContractStatus.PENDING_CONFIRMATION
                || contract.contract().status()==RentalContractStatus.CANCELLED)
            throw conflict("External results cannot be newly registered for this contract stage.");
        var successful=persistence.successfulTotal(charge.id());
        result.requireMatchesCharge(charge.type(),charge.amount().subtract(successful));
        var recorded=persistence.insert(result,actor.identityId(),LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC).withNano(0));
        return finish(actor,contract,charge,recorded,"EXTERNAL_RESULT_RECORDED");
    }
    private RecordedTransaction finish(ActorContext actor,RentalPersistence.StoredContract contract,RentalPersistence.Charge charge,
            RecordedTransaction recorded,String action) {
        var result=recorded.result();
        var successful=persistence.successfulTotal(charge.id());
        String status=successful.compareTo(charge.amount())==0 ? "SETTLED"
                : charge.type().equals("REFUND") ? "PENDING_REFUND"
                : result.status()==ExternalTransactionResult.Status.PENDING_CONFIRMATION ? "PROCESSING" : "PENDING";
        persistence.saveChargeStatus(charge.id(),status);
        if(charge.type().equals("INITIAL") && status.equals("SETTLED")
                && contract.contract().status()==RentalContractStatus.PENDING_PAYMENT) {
            contract.contract().recognizeSuccessfulInitialPayment(); rentals.saveState(contract);
        }
        var refreshed=rentals.find(contract.id()).orElseThrow(TransactionService::missing);
        refreshed.contract().resolveReturnedContract(refreshed.charges().stream().anyMatch(c -> !c.status().equals("SETTLED"))
                || changes.forContract(refreshed.id()).stream().anyMatch(c -> c.status().equals("PROCESSING")));
        rentals.saveState(refreshed);
        audit.appendSuccess(actor,"RENTAL_CONTRACT",contract.id(),action,
                "chargeId="+charge.id()+";transactionId="+recorded.id()+";result="+result.status());
        return recorded;
    }
    private RentalPersistence.Charge authorizedCharge(ActorContext actor,long id) {
        var charge=persistence.charge(id).orElseThrow(TransactionService::missing);
        var contract=rentals.find(charge.contractId()).orElseThrow(TransactionService::missing).contract();
        authorization.require(actor,ActionPermission.EMPLOYEE_PAYMENT_RECORD,paymentStore(charge,contract.serviceStoreId()));
        return charge;
    }
    private long paymentStore(RentalPersistence.Charge charge,long originalStore) {
        if(charge.changeId()==null) return originalStore;
        var change=changes.find(charge.changeId()).orElseThrow(TransactionService::missing);
        if(change.contractId()!=charge.contractId() || !change.status().equals("COMPLETED") || change.requestedStoreId()==null)
            throw conflict("Charge does not reference a completed adjustment");
        return change.requestedStoreId();
    }
    private static boolean externalIdentifierViolation(Throwable error) {
        for(Throwable cause=error;cause!=null;cause=cause.getCause())
            if(cause instanceof java.sql.SQLException sql && sql.getErrorCode()==1062
                    && sql.getMessage()!=null && sql.getMessage().contains("uk_payment_external_id")) return true;
        return false;
    }
    private RecordedTransaction sameOrConflict(RecordedTransaction existing,ExternalTransactionResult result) {
        if(!existing.source().equals("EXTERNAL") || !existing.result().sameBusinessContent(result)) throw conflict("External identifier is already registered with different business content.");
        return existing;
    }
    private static ApiException conflict(String message) { return new ApiException(HttpStatus.CONFLICT,"EXTERNAL_TRANSACTION_CONFLICT",message); }
    private static ApiException missing() { return new ApiException(HttpStatus.NOT_FOUND,"RENTAL_CHARGE_NOT_FOUND","Rental charge not found."); }
}
