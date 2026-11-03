package com.bsr.storetransfer.store.application;

import com.bsr.audit.api.AuditAppender;
import com.bsr.identity.api.ActionPermission;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.Authorization;
import com.bsr.identity.api.IdentityType;
import com.bsr.platform.error.ApiException;
import com.bsr.scheduling.employee.api.EmployeeAccess;
import com.bsr.storetransfer.infrastructure.persistence.StoreRepository;
import com.bsr.storetransfer.infrastructure.persistence.StoreRow;
import com.bsr.storetransfer.store.api.StoreAccess;
import com.bsr.storetransfer.store.api.StoreSnapshot;
import java.time.LocalTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreAdministrationService {
    private final StoreRepository repository;
    private final StoreAccess stores;
    private final EmployeeAccess employees;
    private final Authorization authorization;
    private final AuditAppender audit;

    public StoreAdministrationService(StoreRepository repository, StoreAccess stores,
            EmployeeAccess employees, Authorization authorization, AuditAppender audit) {
        this.repository = repository;
        this.stores = stores;
        this.employees = employees;
        this.authorization = authorization;
        this.audit = audit;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StoreSnapshot create(ActorContext actor, long authorityStoreId, String number,
            String name, String address, LocalTime opening, LocalTime closing) {
        requireManager(actor, authorityStoreId);
        String normalizedNumber = number == null ? null : number.strip();
        validateNumber(normalizedNumber);
        validateDetails(name, address, opening, closing);
        stores.lockForUse(authorityStoreId);
        employees.lockForUse(actor.identityId());
        if (repository.existsByStoreNumber(normalizedNumber)) {
            throw new ApiException(HttpStatus.CONFLICT, "STORE_NUMBER_EXISTS",
                    "Store number is already in use.");
        }
        StoreRow row = repository.saveAndFlush(StoreRow.active(normalizedNumber, name.strip(),
                normalizedAddress(address), opening, closing));
        audit.appendSuccess(actor, "STORE", row.snapshot().storeId(), "STORE_CREATED",
                "authorityStoreId=" + authorityStoreId);
        return row.snapshot();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StoreSnapshot update(ActorContext actor, long storeId, String name,
            String address, LocalTime opening, LocalTime closing) {
        requireManager(actor, storeId);
        validateDetails(name, address, opening, closing);
        StoreRow row = repository.findByIdForUpdate(storeId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "STORE_NOT_FOUND",
                        "Store does not exist."));
        if (!row.active()) {
            throw new ApiException(HttpStatus.CONFLICT, "STORE_INACTIVE", "Store is inactive.");
        }
        employees.lockForUse(actor.identityId());
        row.updateDetails(name.strip(), normalizedAddress(address), opening, closing);
        repository.saveAndFlush(row);
        audit.appendSuccess(actor, "STORE", storeId, "STORE_UPDATED", "storeId=" + storeId);
        return row.snapshot();
    }

    @Transactional(readOnly = true)
    public StoreSnapshot get(ActorContext actor, long storeId) {
        requireManager(actor, storeId);
        return repository.findById(storeId).map(StoreRow::snapshot)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "STORE_NOT_FOUND",
                        "Store does not exist."));
    }

    private void requireManager(ActorContext actor, long storeId) {
        if (actor == null || actor.identityType() != IdentityType.EMPLOYEE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "MANAGER_IDENTITY_REQUIRED",
                    "An employee manager account is required.");
        }
        if (storeId <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STORE_ID", "Store ID must be positive.");
        }
        authorization.require(actor, ActionPermission.MANAGER_STORE_AND_SCHEDULING, storeId);
    }

    private static void validateNumber(String number) {
        if (number == null || number.isBlank() || number.length() > 30) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STORE_NUMBER",
                    "Store number must have 1-30 characters.");
        }
    }

    private static void validateDetails(String name, String address, LocalTime opening, LocalTime closing) {
        if (name == null || name.isBlank() || name.length() > 120
                || address != null && address.length() > 255
                || opening == null || closing == null || !opening.isBefore(closing)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STORE_DETAILS",
                    "Store details and opening hours are invalid.");
        }
    }

    private static String normalizedAddress(String address) {
        return address == null || address.isBlank() ? null : address.strip();
    }
}
