package com.bsr.customerrequest.application;

import com.bsr.audit.api.AuditAppender;
import com.bsr.customerrequest.api.CustomerRequestOperations;
import com.bsr.customerrequest.api.RentalRequestSnapshot;
import com.bsr.customerrequest.api.VerifiedCustomer;
import com.bsr.customerrequest.application.CustomerRequestPersistence.StoredRentalRequest;
import com.bsr.customerrequest.domain.RentalRequestStatus;
import com.bsr.customerrequest.domain.CustomerProfile;
import com.bsr.identity.api.ActionPermission;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.Authorization;
import com.bsr.platform.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerRequestOperationsAdapter implements CustomerRequestOperations {

    private final CustomerRequestService service;
    private final CustomerRequestPersistence persistence;
    private final Authorization authorization;
    private final AuditAppender auditAppender;

    public CustomerRequestOperationsAdapter(
            CustomerRequestService service,
            CustomerRequestPersistence persistence,
            Authorization authorization,
            AuditAppender auditAppender) {
        this.service = service;
        this.persistence = persistence;
        this.authorization = authorization;
        this.auditAppender = auditAppender;
    }

    @Override
    public RentalRequestSnapshot loadOwned(ActorContext actor, long requestId) {
        return snapshot(service.loadOwnedRequest(actor, requestId));
    }

    @Override
    public VerifiedCustomer verifyCustomer(
            ActorContext actor, long storeId, String customerNumber, String email) {
        return customerSnapshot(service.findCustomerForEmployee(actor, storeId, customerNumber, email));
    }

    @Override
    public RentalRequestSnapshot accept(ActorContext actor, long requestId) {
        return snapshot(service.accept(actor, requestId));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public RentalRequestSnapshot lockAcceptedForConversion(ActorContext actor, long requestId) {
        StoredRentalRequest stored = lockedAuthorizedRequest(actor, requestId);
        requireAccepted(stored);
        if (persistence.findConversionContract(requestId).isPresent()) {
            throw conflict("RENTAL_REQUEST_ALREADY_CONVERTED", "The request already has a source contract.");
        }
        return snapshot(stored);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public RentalRequestSnapshot markConverted(ActorContext actor, long requestId, long contractId) {
        if (contractId <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "contractId must be positive.");
        }
        StoredRentalRequest stored = lockedAuthorizedRequest(actor, requestId);
        requireAccepted(stored);
        var contract = persistence.findConversionContract(requestId)
                .orElseThrow(() -> conflict("RENTAL_REQUEST_CONTRACT_MISMATCH",
                        "The source contract does not exist for this request."));
        if (contract.contractId() != contractId
                || contract.customerId() != stored.request().customerId()
                || contract.serviceStoreId() != stored.request().serviceStoreId()) {
            throw conflict("RENTAL_REQUEST_CONTRACT_MISMATCH",
                    "The source contract does not match this request and its customer/store.");
        }
        stored.request().markConverted();
        persistence.saveRequestState(requestId, stored.request());
        auditAppender.appendSuccess(actor, "RENTAL_REQUEST", requestId, "RENTAL_REQUEST_CONVERTED",
                "storeId=%d;contractId=%d".formatted(stored.request().serviceStoreId(), contractId));
        return snapshot(stored);
    }

    private static RentalRequestSnapshot snapshot(StoredRentalRequest stored) {
        var request = stored.request();
        return new RentalRequestSnapshot(
                stored.requestId(), request.customerId(), request.serviceStoreId(),
                request.rentalStartDate(), request.rentalDays(), request.skillLevel(),
                request.status(), request.acceptedByEmployeeId(),
                request.items().stream().map(item -> new RentalRequestSnapshot.RequestedItem(
                        item.equipmentCategory(), item.quantity(), item.fittingNote())).toList());
    }

    private static VerifiedCustomer customerSnapshot(CustomerProfile profile) {
        return new VerifiedCustomer(
                profile.customerId(), profile.customerNumber(), profile.fullName(),
                profile.email(), profile.phone(), profile.heightCm(), profile.weightKg(),
                profile.bootSize(), profile.heightEstimated(), profile.weightEstimated(),
                profile.bootSizeEstimated());
    }

    private StoredRentalRequest lockedAuthorizedRequest(ActorContext actor, long requestId) {
        if (requestId <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "requestId must be positive.");
        }
        StoredRentalRequest stored = persistence.findRequestForUpdate(requestId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RENTAL_REQUEST_NOT_FOUND",
                        "The rental request does not exist."));
        authorization.require(actor, ActionPermission.EMPLOYEE_RENTAL_PROCESS,
                stored.request().serviceStoreId());
        return stored;
    }

    private static void requireAccepted(StoredRentalRequest stored) {
        if (stored.request().status() != RentalRequestStatus.ACCEPTED) {
            throw conflict("RENTAL_REQUEST_STATE_CONFLICT", "Only an accepted request can become a contract.");
        }
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
