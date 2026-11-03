package com.bsr.bootstrap.usecase;

import com.bsr.equipment.api.EquipmentStatusProjection;
import com.bsr.identity.api.ActionPermission;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.Authorization;
import com.bsr.identity.api.IdentityType;
import com.bsr.platform.error.ApiException;
import com.bsr.rental.api.RentalStatusProjection;
import com.bsr.repair.api.RepairStatusProjection;
import com.bsr.scheduling.shift.api.ShiftStatusProjection;
import com.bsr.storetransfer.store.api.StoreAccess;
import com.bsr.storetransfer.transfer.api.TransferStatusProjection;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationsQuery {
    private final Authorization authorization;
    private final StoreAccess stores;
    private final RentalStatusProjection rentals;
    private final EquipmentStatusProjection equipment;
    private final RepairStatusProjection repairs;
    private final TransferStatusProjection transfers;
    private final ShiftStatusProjection shifts;

    public OperationsQuery(Authorization authorization, StoreAccess stores,
            RentalStatusProjection rentals, EquipmentStatusProjection equipment,
            RepairStatusProjection repairs, TransferStatusProjection transfers,
            ShiftStatusProjection shifts) {
        this.authorization = authorization;
        this.stores = stores;
        this.rentals = rentals;
        this.equipment = equipment;
        this.repairs = repairs;
        this.transfers = transfers;
        this.shifts = shifts;
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public OperationsState forStore(ActorContext actor, long storeId) {
        if (actor == null || actor.identityType() != IdentityType.EMPLOYEE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "MANAGER_IDENTITY_REQUIRED",
                    "An employee manager account is required.");
        }
        authorization.require(actor, ActionPermission.MANAGER_OPERATIONS_READ, storeId);
        if (stores.findById(storeId).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "STORE_NOT_FOUND", "Store does not exist.");
        }
        return new OperationsState(Long.toString(storeId), rentals.countsAtStore(storeId),
                equipment.countsAtStore(storeId), repairs.countsAtStore(storeId),
                transfers.countsAtStore(storeId), shifts.countsAtStore(storeId));
    }

    public record OperationsState(String storeId, Map<String, Long> rentalStatuses,
            Map<String, Long> equipmentStatuses, Map<String, Long> repairStatuses,
            Map<String, Long> transferStatuses, Map<String, Long> shiftStatuses) { }
}
