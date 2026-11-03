package com.bsr.storetransfer.coordination.api;

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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Internal-only boundary. T07 deliberately exposes no stop-store HTTP route. */
@Service
public class GuardedStoreDeactivation {
    private final StoreAccess stores;
    private final StoreRepository repository;
    private final EmployeeAccess employees;
    private final Authorization authorization;
    private final StoreDeactivationGuard guard;
    private final AuditAppender audit;

    public GuardedStoreDeactivation(StoreAccess stores, StoreRepository repository,
            EmployeeAccess employees, Authorization authorization,
            StoreDeactivationGuard guard, AuditAppender audit) {
        this.stores = stores;
        this.repository = repository;
        this.employees = employees;
        this.authorization = authorization;
        this.guard = guard;
        this.audit = audit;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StoreSnapshot deactivate(ActorContext actor, long storeId) {
        if (actor == null || actor.identityType() != IdentityType.EMPLOYEE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "MANAGER_IDENTITY_REQUIRED",
                    "An employee manager account is required.");
        }
        if (storeId <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STORE_ID", "Store ID must be positive.");
        }
        authorization.require(actor, ActionPermission.MANAGER_STORE_AND_SCHEDULING, storeId);
        StoreSnapshot locked = stores.lockForDeactivation(storeId);
        if (!locked.active()) {
            throw new ApiException(HttpStatus.CONFLICT, "STORE_INACTIVE", "Store is already inactive.");
        }
        employees.lockForUse(actor.identityId());
        guard.validate(storeId);
        StoreRow row = repository.findById(storeId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "STORE_NOT_FOUND",
                        "Store does not exist."));
        row.deactivate();
        repository.saveAndFlush(row);
        audit.appendSuccess(actor, "STORE", storeId, "STORE_DEACTIVATED", "storeId=" + storeId);
        return row.snapshot();
    }
}
