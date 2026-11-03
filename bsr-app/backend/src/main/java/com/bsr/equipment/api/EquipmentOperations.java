package com.bsr.equipment.api;

import com.bsr.identity.api.ActorContext;
import java.util.List;

public interface EquipmentOperations {

    List<EquipmentSnapshot> releaseRejectedAddition(ActorContext customer,long contractId,
            long changeId,long expectedStoreId,List<Long> equipmentIds);

    List<EquipmentSnapshot> replaceBeforeIssue(ActorContext actor,long rentalContractId,
            long expectedStoreId,List<Long> releasedEquipmentIds,List<Long> reservedEquipmentIds);

    List<EquipmentSnapshot> selectAvailable(
            ActorContext actor,
            long storeId,
            String category,
            String skillLevel);

    List<EquipmentSnapshot> reserveForRental(
            ActorContext actor,
            long rentalContractId,
            long expectedStoreId,
            List<Long> equipmentIds);

    List<EquipmentSnapshot> releaseBeforeIssue(
            ActorContext actor,
            long rentalContractId,
            long expectedStoreId,
            List<Long> equipmentIds);

    List<EquipmentSnapshot> issue(
            ActorContext actor,
            long rentalContractId,
            long expectedStoreId,
            List<Long> equipmentIds);

    EquipmentSnapshot receiveReturn(
            ActorContext actor,
            long rentalItemId,
            long equipmentId,
            long returnStoreId);

    /** Holds the equipment row lock until the caller's transaction commits. */
    EquipmentSnapshot lockForRepairTaskCreation(ActorContext actor, long equipmentId);

    EquipmentSnapshot startRepair(ActorContext actor, long repairTaskId, long equipmentId);

    EquipmentSnapshot submitRepair(ActorContext actor, long repairTaskId, long equipmentId);

    List<EquipmentSnapshot> dispatchTransfer(
            ActorContext actor,
            long transferId,
            long sourceStoreId,
            long destinationStoreId,
            List<Long> equipmentIds);

    List<EquipmentSnapshot> receiveTransfer(
            ActorContext actor,
            long transferId,
            long sourceStoreId,
            long destinationStoreId,
            boolean permanent,
            List<Long> equipmentIds);
}
