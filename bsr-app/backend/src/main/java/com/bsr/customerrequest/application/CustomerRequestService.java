package com.bsr.customerrequest.application;

import com.bsr.audit.api.AuditAppender;
import com.bsr.customerrequest.application.CustomerRequestPersistence.StoredRentalRequest;
import com.bsr.customerrequest.application.CustomerRequestPersistence.SupplementCycle;
import com.bsr.customerrequest.domain.CustomerProfile;
import com.bsr.customerrequest.domain.RentalRequest;
import com.bsr.customerrequest.domain.RentalRequestItem;
import com.bsr.customerrequest.domain.RentalRequestStatus;
import com.bsr.identity.api.ActionPermission;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.Authorization;
import com.bsr.platform.error.ApiException;
import com.bsr.storetransfer.store.api.StoreAccess;
import com.bsr.storetransfer.store.api.StoreAccessException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerRequestService {

    private static final List<RentalRequestStatus> EMPLOYEE_QUEUE_STATUSES = List.of(
            RentalRequestStatus.PENDING,
            RentalRequestStatus.NEEDS_INFO,
            RentalRequestStatus.ACCEPTED);

    private final Authorization authorization;
    private final CustomerRequestPersistence persistence;
    private final AuditAppender auditAppender;
    private final Clock clock;
    private final StoreAccess storeAccess;

    public CustomerRequestService(
            Authorization authorization,
            CustomerRequestPersistence persistence,
            AuditAppender auditAppender,
            Clock clock,
            StoreAccess storeAccess) {
        this.authorization = authorization;
        this.persistence = persistence;
        this.auditAppender = auditAppender;
        this.clock = clock;
        this.storeAccess = storeAccess;
    }

    @Transactional(readOnly = true)
    public CustomerProfile loadOwnProfile(ActorContext actor) {
        requireCustomerAction(actor, ActionPermission.CUSTOMER_PROFILE_SELF);
        return persistence.findCustomer(actor.identityId())
                .orElseThrow(() -> notFound("CUSTOMER_NOT_FOUND", "The customer profile does not exist."));
    }

    @Transactional(readOnly = true)
    public CustomerProfile findCustomerForEmployee(
            ActorContext actor,
            long storeId,
            String customerNumber,
            String email) {
        requirePositiveId(storeId, "storeId");
        authorization.require(actor, ActionPermission.EMPLOYEE_RENTAL_PROCESS, storeId);
        boolean byNumber = customerNumber != null && !customerNumber.isBlank();
        boolean byEmail = email != null && !email.isBlank();
        if (byNumber == byEmail) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    "Exactly one customer number or email is required.");
        }
        if (byNumber) {
            String exactNumber = customerNumber.trim();
            if (exactNumber.length() > 30) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                        "customerNumber must not exceed 30 characters.");
            }
            return persistence.findCustomerByNumber(exactNumber)
                    .orElseThrow(() -> notFound("CUSTOMER_NOT_FOUND", "The customer does not exist."));
        }
        String exactEmail = email.trim();
        if (exactEmail.length() > 255 || !exactEmail.contains("@")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    "email must be a valid customer email of at most 255 characters.");
        }
        return persistence.findCustomerByEmail(exactEmail)
                .orElseThrow(() -> notFound("CUSTOMER_NOT_FOUND", "The customer does not exist."));
    }

    public record ContactUpdate(String fullName, String email, String phone, String address, String birthYear) { }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CustomerProfile updateOwnContactDetails(ActorContext actor, ContactUpdate update) {
        requireCustomerAction(actor, ActionPermission.CUSTOMER_PROFILE_SELF);
        if (update == null || update.fullName() == null || update.fullName().isBlank()
                || update.fullName().trim().length() > 100 || update.email() == null
                || update.email().isBlank() || update.email().trim().length() > 255
                || !update.email().trim().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
                || update.phone() == null || update.phone().isBlank() || update.phone().trim().length() > 40
                || update.address() != null && update.address().trim().length() > 255) {
            throw new IllegalArgumentException("Invalid customer contact details.");
        }
        CustomerProfile current = persistence.findCustomerForUpdate(actor.identityId())
                .orElseThrow(() -> notFound("CUSTOMER_NOT_FOUND", "The customer profile does not exist."));
        Short year = current.birthYear();
        if (update.birthYear() != null) {
            String text = update.birthYear().trim();
            if (text.isEmpty()) {
                year = null;
            } else {
                if (!text.matches("[0-9]{1,4}")) throw new IllegalArgumentException("Invalid birth year.");
                int value = Integer.parseInt(text);
                if (value < 1 || value > LocalDate.now(clock).getYear()) {
                    throw new IllegalArgumentException("Birth year must not be in the future.");
                }
                year = (short) value;
            }
        }
        String address = update.address() == null ? current.address() : update.address().trim();
        if (address != null && address.isEmpty()) address = null;
        CustomerProfile updated = current.withContactDetails(update.fullName().trim(), update.email().trim(),
                update.phone().trim(), address, year);
        persistence.saveCustomer(updated);
        auditAppender.appendSuccess(actor, "CUSTOMER", updated.customerId(), "CUSTOMER_CONTACT_UPDATED",
                "Contact details updated; measurement values preserved.");
        return updated;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CustomerProfile updateOwnMeasurements(ActorContext actor, MeasurementUpdate update) {
        requireCustomerAction(actor, ActionPermission.CUSTOMER_PROFILE_SELF);
        CustomerProfile current = persistence.findCustomerForUpdate(actor.identityId())
                .orElseThrow(() -> notFound("CUSTOMER_NOT_FOUND", "The customer profile does not exist."));
        CustomerProfile updated = current.withMeasurements(
                update.heightCm(),
                update.heightEstimated(),
                update.weightKg(),
                update.weightEstimated(),
                update.bootSize(),
                update.bootSizeEstimated());
        persistence.saveCustomer(updated);
        auditAppender.appendSuccess(
                actor,
                "CUSTOMER",
                updated.customerId(),
                "CUSTOMER_PROFILE_UPDATED",
                "heightEstimated=%s;weightEstimated=%s;bootSizeEstimated=%s".formatted(
                        updated.heightEstimated(),
                        updated.weightEstimated(),
                        updated.bootSizeEstimated()));
        return updated;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CustomerProfile reviewCustomerMeasurements(
            ActorContext actor, long storeId, long customerId, String customerNumber,
            MeasurementUpdate expected, MeasurementUpdate update) {
        requirePositiveId(storeId, "storeId");
        requirePositiveId(customerId, "customerId");
        authorization.require(actor, ActionPermission.EMPLOYEE_RENTAL_PROCESS, storeId);
        CustomerProfile current = persistence.findCustomerForUpdate(customerId)
                .orElseThrow(() -> notFound("CUSTOMER_NOT_FOUND", "The customer profile does not exist."));
        if (!current.customerNumber().equals(customerNumber)) {
            throw notFound("CUSTOMER_NOT_FOUND", "The customer profile does not exist.");
        }
        if (!sameMeasurement(current.heightCm(), expected.heightCm())
                || !sameMeasurement(current.weightKg(), expected.weightKg())
                || !sameMeasurement(current.bootSize(), expected.bootSize())
                || current.heightEstimated() != expected.heightEstimated()
                || current.weightEstimated() != expected.weightEstimated()
                || current.bootSizeEstimated() != expected.bootSizeEstimated()) {
            throw conflict("CUSTOMER_PROFILE_CHANGED", "Measurements changed; reload the customer before review.");
        }
        CustomerProfile reviewed = current.withMeasurements(
                update.heightCm(), update.heightEstimated(),
                update.weightKg(), update.weightEstimated(),
                update.bootSize(), update.bootSizeEstimated());
        persistence.saveCustomer(reviewed);
        auditAppender.appendSuccess(
                actor,
                "CUSTOMER",
                reviewed.customerId(),
                "CUSTOMER_MEASUREMENTS_REVIEWED",
                "storeId=%d;heightEstimated=%s;weightEstimated=%s;bootSizeEstimated=%s".formatted(
                        storeId, reviewed.heightEstimated(),
                        reviewed.weightEstimated(), reviewed.bootSizeEstimated()));
        return reviewed;
    }

    private boolean sameMeasurement(BigDecimal actual, BigDecimal expected) {
        return actual == null ? expected == null : expected != null && actual.compareTo(expected) == 0;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StoredRentalRequest submit(ActorContext actor, Submission submission) {
        requireCustomerAction(actor, ActionPermission.CUSTOMER_RENTAL_REQUEST);
        lockActiveStore(submission.serviceStoreId());
        persistence.findCustomerForUpdate(actor.identityId())
                .orElseThrow(() -> notFound("CUSTOMER_NOT_FOUND", "The customer profile does not exist."));
        if (persistence.hasAwaitingRequest(actor.identityId()) && !submission.confirmDuplicate()) {
            throw conflict(
                    "DUPLICATE_REQUEST_CONFIRMATION_REQUIRED",
                    "An awaiting rental request already exists; explicit confirmation is required to continue.");
        }

        RentalRequest request = RentalRequest.submit(
                nextRequestNumber(),
                actor.identityId(),
                submission.serviceStoreId(),
                submission.rentalStartDate(),
                submission.rentalDays(),
                submission.skillLevel(),
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
                submission.items());
        long requestId = persistence.saveRequest(request);
        auditAppender.appendSuccess(
                actor,
                "RENTAL_REQUEST",
                requestId,
                "RENTAL_REQUEST_SUBMITTED",
                "storeId=%d;rentalDays=%d;itemCount=%d".formatted(
                        request.serviceStoreId(),
                        request.rentalDays(),
                        request.items().size()));
        return new StoredRentalRequest(requestId, request);
    }

    private void lockActiveStore(long storeId) {
        try {
            storeAccess.lockForUse(storeId);
        } catch (StoreAccessException exception) {
            HttpStatus status = exception.code() == StoreAccessException.Code.STORE_NOT_FOUND
                    ? HttpStatus.NOT_FOUND
                    : HttpStatus.CONFLICT;
            throw new ApiException(status, exception.code().name(), exception.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public StoredRentalRequest loadOwnedRequest(ActorContext actor, long requestId) {
        requireCustomerAction(actor, ActionPermission.CUSTOMER_RENTAL_READ);
        if (requestId <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "requestId must be positive.");
        }
        return persistence.findOwnedRequest(requestId, actor.identityId())
                .orElseThrow(() -> notFound("RENTAL_REQUEST_NOT_FOUND", "The rental request does not exist."));
    }

    @Transactional(readOnly = true)
    public List<StoredRentalRequest> loadOwnedRequests(ActorContext actor) {
        requireCustomerAction(actor, ActionPermission.CUSTOMER_RENTAL_READ);
        return persistence.findOwnedRequests(actor.identityId());
    }

    @Transactional(readOnly = true)
    public List<StoredRentalRequest> loadEmployeeQueue(ActorContext actor, long storeId) {
        requirePositiveId(storeId, "storeId");
        authorization.require(actor, ActionPermission.EMPLOYEE_RENTAL_PROCESS, storeId);
        return persistence.findStoreRequests(storeId, EMPLOYEE_QUEUE_STATUSES);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StoredRentalRequest requestMoreInformation(ActorContext actor, long requestId, String reason) {
        String normalizedReason = requiredSupplementText(reason, "reason");
        requirePositiveId(requestId, "requestId");
        StoredRentalRequest stored = persistence.findRequestForUpdate(requestId)
                .orElseThrow(() -> notFound("RENTAL_REQUEST_NOT_FOUND", "The rental request does not exist."));
        RentalRequest request = stored.request();
        authorization.require(actor, ActionPermission.EMPLOYEE_RENTAL_PROCESS, request.serviceStoreId());
        try {
            request.requestMoreInformation();
        } catch (IllegalStateException exception) {
            throw conflict("RENTAL_REQUEST_STATE_CONFLICT", exception.getMessage());
        }
        int nextCycle = stored.supplements().isEmpty()
                ? 1 : stored.supplements().getLast().cycleNumber() + 1;
        persistence.addSupplement(requestId, nextCycle, actor.identityId(), normalizedReason,
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        persistence.saveRequestState(requestId, request);
        auditAppender.appendSuccess(actor, "RENTAL_REQUEST", requestId,
                "RENTAL_REQUEST_INFO_REQUESTED",
                "storeId=%d;cycle=%d;resultStatus=NEEDS_INFO".formatted(
                        request.serviceStoreId(), nextCycle));
        return new StoredRentalRequest(requestId, request, persistence.listSupplements(requestId));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StoredRentalRequest resubmitSupplementedInformation(
            ActorContext actor, long requestId, String response) {
        requireCustomerAction(actor, ActionPermission.CUSTOMER_RENTAL_REQUEST);
        String normalizedResponse = requiredSupplementText(response, "response");
        requirePositiveId(requestId, "requestId");
        persistence.findCustomerForUpdate(actor.identityId())
                .orElseThrow(() -> notFound("CUSTOMER_NOT_FOUND", "The customer profile does not exist."));
        StoredRentalRequest stored = persistence.findRequestForUpdate(requestId)
                .orElseThrow(() -> notFound("RENTAL_REQUEST_NOT_FOUND", "The rental request does not exist."));
        RentalRequest request = stored.request();
        if (request.customerId() != actor.identityId()) {
            throw notFound("RENTAL_REQUEST_NOT_FOUND", "The rental request does not exist.");
        }
        List<SupplementCycle> cycles = stored.supplements();
        if (cycles.isEmpty() || cycles.getLast().customerResponse() != null) {
            throw conflict("RENTAL_REQUEST_STATE_CONFLICT", "No unanswered supplement cycle exists.");
        }
        try {
            request.resubmitSupplementedInformation();
        } catch (IllegalStateException exception) {
            throw conflict("RENTAL_REQUEST_STATE_CONFLICT", exception.getMessage());
        }
        persistence.answerSupplement(cycles.getLast().supplementId(), normalizedResponse,
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        persistence.saveRequestState(requestId, request);
        auditAppender.appendSuccess(actor, "RENTAL_REQUEST", requestId,
                "RENTAL_REQUEST_INFO_RESUBMITTED",
                "cycle=%d;resultStatus=PENDING".formatted(cycles.getLast().cycleNumber()));
        return new StoredRentalRequest(requestId, request, persistence.listSupplements(requestId));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StoredRentalRequest accept(ActorContext actor, long requestId) {
        return processEmployeeAction(actor, requestId, EmployeeRequestAction.ACCEPT);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StoredRentalRequest markUnavailable(ActorContext actor, long requestId) {
        return processEmployeeAction(actor, requestId, EmployeeRequestAction.MARK_UNAVAILABLE);
    }

    private StoredRentalRequest processEmployeeAction(
            ActorContext actor,
            long requestId,
            EmployeeRequestAction action) {
        requirePositiveId(requestId, "requestId");
        StoredRentalRequest stored = persistence.findRequestForUpdate(requestId)
                .orElseThrow(() -> notFound("RENTAL_REQUEST_NOT_FOUND", "The rental request does not exist."));
        RentalRequest request = stored.request();
        authorization.require(actor, ActionPermission.EMPLOYEE_RENTAL_PROCESS, request.serviceStoreId());
        try {
            switch (action) {
                case ACCEPT -> request.accept(actor.identityId());
                case MARK_UNAVAILABLE -> request.markUnavailable();
            }
        } catch (IllegalStateException exception) {
            throw conflict("RENTAL_REQUEST_STATE_CONFLICT", exception.getMessage());
        }
        persistence.saveRequestState(requestId, request);
        auditAppender.appendSuccess(
                actor,
                "RENTAL_REQUEST",
                requestId,
                action.auditAction(),
                "storeId=%d;resultStatus=%s".formatted(request.serviceStoreId(), request.status().name()));
        return new StoredRentalRequest(requestId, request, stored.supplements());
    }

    private void requireCustomerAction(ActorContext actor, ActionPermission action) {
        authorization.require(actor, action, null);
        authorization.requireCustomerOwnership(actor, actor.identityId());
    }

    private String nextRequestNumber() {
        return "REQ-" + UUID.randomUUID().toString().replace("-", "").substring(0, 26).toUpperCase();
    }

    private ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private void requirePositiveId(long value, String field) {
        if (value <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", field + " must be positive.");
        }
    }

    private String requiredSupplementText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", field + " is required.");
        }
        String normalized = value.trim();
        if (normalized.length() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    field + " must not exceed 500 characters.");
        }
        return normalized;
    }

    private enum EmployeeRequestAction {
        ACCEPT("RENTAL_REQUEST_ACCEPTED"),
        MARK_UNAVAILABLE("RENTAL_REQUEST_MARKED_UNAVAILABLE");

        private final String auditAction;

        EmployeeRequestAction(String auditAction) {
            this.auditAction = auditAction;
        }

        String auditAction() {
            return auditAction;
        }
    }

    public record MeasurementUpdate(
            BigDecimal heightCm,
            boolean heightEstimated,
            BigDecimal weightKg,
            boolean weightEstimated,
            BigDecimal bootSize,
            boolean bootSizeEstimated) {
    }

    public record Submission(
            long serviceStoreId,
            LocalDate rentalStartDate,
            int rentalDays,
            String skillLevel,
            List<RentalRequestItem> items,
            boolean confirmDuplicate) {

        public Submission {
            items = items == null ? null : List.copyOf(items);
        }
    }
}
