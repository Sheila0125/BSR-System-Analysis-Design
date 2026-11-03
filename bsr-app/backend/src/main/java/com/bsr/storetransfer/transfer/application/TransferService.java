package com.bsr.storetransfer.transfer.application;

import com.bsr.audit.api.AuditAppender;
import com.bsr.equipment.api.EquipmentOperations;
import com.bsr.equipment.api.EquipmentSnapshot;
import com.bsr.identity.api.ActionPermission;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.Authorization;
import com.bsr.identity.api.IdentityType;
import com.bsr.platform.error.ApiException;
import com.bsr.scheduling.employee.api.EmployeeAccess;
import com.bsr.storetransfer.store.api.StoreAccess;
import com.bsr.storetransfer.transfer.api.TransferBlocker;
import com.bsr.storetransfer.transfer.api.TransferBlockingProjection;
import com.bsr.storetransfer.transfer.domain.TransferAggregate;
import com.bsr.storetransfer.transfer.domain.TransferItem;
import com.bsr.storetransfer.transfer.domain.TransferType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService implements TransferBlockingProjection {
    private final TransferPersistence persistence;
    private final StoreAccess stores;
    private final EmployeeAccess employees;
    private final EquipmentOperations equipment;
    private final Authorization authorization;
    private final AuditAppender audit;
    private final Clock clock;

    public TransferService(TransferPersistence persistence, StoreAccess stores,
            EmployeeAccess employees, EquipmentOperations equipment,
            Authorization authorization, AuditAppender audit, Clock clock) {
        this.persistence = persistence;
        this.stores = stores;
        this.employees = employees;
        this.equipment = equipment;
        this.authorization = authorization;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransferViews.TransferRecord initiate(ActorContext actor, long sourceStoreId,
            long destinationStoreId, TransferType type, List<Long> equipmentIds) {
        requireManagerAtBothStores(actor, sourceStoreId, destinationStoreId);
        String number = "TR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 26);
        TransferAggregate proposed = TransferAggregate.initiate(number, type,
                sourceStoreId, destinationStoreId, actor.identityId(), now(), equipmentIds);
        lockStoresForUse(sourceStoreId, destinationStoreId);
        employees.lockForUse(actor.identityId());

        HashSet<Long> availableIds = new HashSet<>();
        for (EquipmentSnapshot candidate : equipment.selectAvailable(actor, sourceStoreId, null, null)) {
            availableIds.add(candidate.equipmentId());
        }
        List<Long> invalidIds = equipmentIds.stream().filter(id -> !availableIds.contains(id)).toList();
        if (!invalidIds.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "TRANSFER_EQUIPMENT_UNAVAILABLE",
                    "Some equipment is no longer available at the source store.",
                    Map.of("invalidEquipmentIds", invalidIds.stream().map(String::valueOf)
                            .reduce((left, right) -> left + "," + right).orElse("")));
        }

        long id = persistence.create(proposed);
        audit.appendSuccess(actor, "EQUIPMENT_TRANSFER", id, "TRANSFER_INITIATED",
                "sourceStoreId=" + sourceStoreId + ";destinationStoreId=" + destinationStoreId);
        return persistence.find(id).orElseThrow();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransferViews.TransferRecord approve(ActorContext actor, long transferId) {
        if (transferId <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TRANSFER_ID", "Transfer ID must be positive.");
        }
        TransferViews.StorePair observed = persistence.stores(transferId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TRANSFER_NOT_FOUND",
                        "Transfer does not exist."));
        long source = observed.sourceStoreId();
        long destination = observed.destinationStoreId();
        requireManagerAtBothStores(actor, source, destination);
        lockStoresForUse(source, destination);
        employees.lockForUse(actor.identityId());
        TransferViews.TransferRecord locked = persistence.lock(transferId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TRANSFER_NOT_FOUND",
                        "Transfer does not exist."));
        if (locked.aggregate().sourceStoreId() != source
                || locked.aggregate().destinationStoreId() != destination) {
            throw new ApiException(HttpStatus.CONFLICT, "TRANSFER_CHANGED", "Transfer stores changed.");
        }
        if (locked.aggregate().approve(actor.identityId(), now())) {
            persistence.save(locked);
            audit.appendSuccess(actor, "EQUIPMENT_TRANSFER", transferId, "TRANSFER_APPROVED",
                    "sourceStoreId=" + source + ";destinationStoreId=" + destination);
        }
        return locked;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransferViews.TransferRecord dispatch(ActorContext actor, long transferId, long itemId) {
        TransferViews.StorePair observed = observedStores(transferId);
        long source = observed.sourceStoreId();
        long destination = observed.destinationStoreId();
        requireEmployeeAtStore(actor, source);
        lockStoresForUse(source, destination);
        employees.lockForUse(actor.identityId());
        TransferViews.TransferRecord locked = lock(transferId, source, destination);
        long equipmentId = equipmentId(locked, itemId);
        if (transition(() -> locked.aggregate().dispatch(equipmentId, actor.identityId(), now()))) {
            equipment.dispatchTransfer(actor, transferId, source, destination, List.of(equipmentId));
            persistence.save(locked);
            audit.appendSuccess(actor, "EQUIPMENT_TRANSFER", transferId, "TRANSFER_ITEM_DISPATCHED",
                    "itemId=" + itemId + ";equipmentId=" + equipmentId);
        }
        return locked;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransferViews.TransferRecord receive(ActorContext actor, long transferId, long itemId) {
        TransferViews.StorePair observed = observedStores(transferId);
        long source = observed.sourceStoreId();
        long destination = observed.destinationStoreId();
        requireEmployeeAtStore(actor, destination);
        lockStoresForUse(source, destination);
        employees.lockForUse(actor.identityId());
        TransferViews.TransferRecord locked = lock(transferId, source, destination);
        long equipmentId = equipmentId(locked, itemId);
        if (transition(() -> locked.aggregate().receive(equipmentId, actor.identityId(), now()))) {
            equipment.receiveTransfer(actor, transferId, source, destination,
                    locked.aggregate().type() == TransferType.PERMANENT, List.of(equipmentId));
            persistence.save(locked);
            audit.appendSuccess(actor, "EQUIPMENT_TRANSFER", transferId, "TRANSFER_ITEM_RECEIVED",
                    "itemId=" + itemId + ";equipmentId=" + equipmentId);
        }
        return locked;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransferViews.TransferRecord reportException(ActorContext actor, long transferId,
            long itemId, TransferItem.Phase phase, String note) {
        TransferViews.StorePair observed = observedStores(transferId);
        if (phase == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TRANSFER_PHASE", "Phase is required.");
        }
        long source = observed.sourceStoreId();
        long destination = observed.destinationStoreId();
        requireEmployeeAtStore(actor, phase == TransferItem.Phase.DISPATCH ? source : destination);
        lockStoresForUse(source, destination);
        employees.lockForUse(actor.identityId());
        TransferViews.TransferRecord locked = lock(transferId, source, destination);
        long equipmentId = equipmentId(locked, itemId);
        try {
            locked.aggregate().reportException(equipmentId, phase, note, actor.identityId(), now());
        } catch (IllegalStateException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "TRANSFER_STATE_CONFLICT", exception.getMessage());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TRANSFER_EXCEPTION", exception.getMessage());
        }
        persistence.save(locked);
        audit.appendSuccess(actor, "EQUIPMENT_TRANSFER", transferId, "TRANSFER_ITEM_EXCEPTION",
                "itemId=" + itemId + ";phase=" + phase + ";note=" + note);
        return locked;
    }

    @Transactional(readOnly = true)
    public TransferViews.TransferRecord detail(ActorContext actor, long transferId) {
        TransferViews.TransferRecord record = required(transferId);
        requireVisible(actor, record);
        return record;
    }

    @Transactional(readOnly = true)
    public List<TransferViews.TransferRecord> list(ActorContext actor, long storeId) {
        if (actor == null || actor.identityType() != IdentityType.EMPLOYEE || storeId <= 0
                || (!hasGrant(actor, com.bsr.identity.api.RoleCode.MANAGER, storeId)
                    && !hasGrant(actor, com.bsr.identity.api.RoleCode.EMPLOYEE, storeId))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TRANSFER_SCOPE_DENIED",
                    "Transfer store is outside the current account scope.");
        }
        return persistence.listByStore(storeId).stream().filter(record -> visible(actor, record)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransferBlocker> activeForStore(long storeId) {
        if (storeId <= 0) {
            throw new IllegalArgumentException("storeId must be positive");
        }
        return persistence.activeBlockers(storeId).stream().map(item -> new TransferBlocker(
                item.transferId(), item.itemId(), item.equipmentId(),
                item.transferStatus().name(), item.dispatchResult().name(), item.receiptResult().name()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TransferBlocker> activeBlockers(long storeId) {
        return activeForStore(storeId);
    }

    private TransferViews.TransferRecord required(long transferId) {
        if (transferId <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TRANSFER_ID", "Transfer ID must be positive.");
        }
        return persistence.find(transferId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "TRANSFER_NOT_FOUND", "Transfer does not exist."));
    }

    private TransferViews.StorePair observedStores(long transferId) {
        if (transferId <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TRANSFER_ID", "Transfer ID must be positive.");
        }
        return persistence.stores(transferId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "TRANSFER_NOT_FOUND", "Transfer does not exist."));
    }

    private TransferViews.TransferRecord lock(long transferId, long source, long destination) {
        TransferViews.TransferRecord locked = persistence.lock(transferId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TRANSFER_NOT_FOUND",
                        "Transfer does not exist."));
        if (locked.aggregate().sourceStoreId() != source
                || locked.aggregate().destinationStoreId() != destination) {
            throw new ApiException(HttpStatus.CONFLICT, "TRANSFER_CHANGED", "Transfer stores changed.");
        }
        return locked;
    }

    private long equipmentId(TransferViews.TransferRecord record, long itemId) {
        return record.itemIds().entrySet().stream()
                .filter(entry -> entry.getValue() == itemId)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TRANSFER_ITEM_NOT_FOUND",
                        "Transfer item does not belong to this transfer."));
    }

    private void requireEmployeeAtStore(ActorContext actor, long storeId) {
        if (actor == null || actor.identityType() != IdentityType.EMPLOYEE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EMPLOYEE_IDENTITY_REQUIRED",
                    "An employee account is required.");
        }
        authorization.require(actor, ActionPermission.EMPLOYEE_TRANSFER_HANDOFF, storeId);
    }

    private boolean transition(BooleanSupplier action) {
        try {
            return action.getAsBoolean();
        } catch (IllegalStateException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "TRANSFER_STATE_CONFLICT", exception.getMessage());
        }
    }

    private void requireVisible(ActorContext actor, TransferViews.TransferRecord record) {
        if (!visible(actor, record)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TRANSFER_NOT_FOUND", "Transfer does not exist.");
        }
    }

    private boolean visible(ActorContext actor, TransferViews.TransferRecord record) {
        if (actor == null || actor.identityType() != IdentityType.EMPLOYEE) {
            return false;
        }
        long source = record.aggregate().sourceStoreId();
        long destination = record.aggregate().destinationStoreId();
        return (hasGrant(actor, com.bsr.identity.api.RoleCode.MANAGER, source)
                    && hasGrant(actor, com.bsr.identity.api.RoleCode.MANAGER, destination))
                || hasGrant(actor, com.bsr.identity.api.RoleCode.EMPLOYEE, source)
                || hasGrant(actor, com.bsr.identity.api.RoleCode.EMPLOYEE, destination);
    }

    private boolean hasGrant(ActorContext actor, com.bsr.identity.api.RoleCode role, long storeId) {
        return actor.activeGrants().stream().anyMatch(grant -> grant.role() == role
                && grant.storeId() != null && grant.storeId() == storeId);
    }

    private void requireManagerAtBothStores(ActorContext actor, long source, long destination) {
        if (actor == null || actor.identityType() != IdentityType.EMPLOYEE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "MANAGER_IDENTITY_REQUIRED",
                    "An employee manager account is required.");
        }
        if (source <= 0 || destination <= 0 || source == destination) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TRANSFER_STORES",
                    "Source and destination must be distinct positive stores.");
        }
        authorization.require(actor, ActionPermission.MANAGER_TRANSFER, source);
        authorization.require(actor, ActionPermission.MANAGER_TRANSFER, destination);
    }

    private void lockStoresForUse(long source, long destination) {
        stores.lockForUse(Math.min(source, destination));
        stores.lockForUse(Math.max(source, destination));
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
    }
}
