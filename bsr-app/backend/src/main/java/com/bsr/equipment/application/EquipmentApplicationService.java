package com.bsr.equipment.application;

import com.bsr.audit.api.AuditAppender;
import com.bsr.equipment.api.EquipmentOperationException;
import com.bsr.equipment.api.EquipmentOperationException.Code;
import com.bsr.equipment.api.EquipmentOperations;
import com.bsr.equipment.api.EquipmentSnapshot;
import com.bsr.equipment.api.InspectionOperations;
import com.bsr.equipment.api.InspectionOutcome;
import com.bsr.equipment.domain.Equipment;
import com.bsr.equipment.domain.EquipmentInspection;
import com.bsr.equipment.domain.EquipmentStatus;
import com.bsr.equipment.domain.InspectionResult;
import com.bsr.equipment.domain.InspectionType;
import com.bsr.identity.api.ActionPermission;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.Authorization;
import com.bsr.identity.api.IdentityType;
import com.bsr.identity.api.RoleCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EquipmentApplicationService implements EquipmentOperations, InspectionOperations {

    @Override
    @Transactional(propagation=Propagation.MANDATORY)
    public List<EquipmentSnapshot> releaseRejectedAddition(ActorContext actor,long contractId,long changeId,long storeId,List<Long> ids) {
        requireActor(actor); requirePositiveId(contractId,"rentalContractId");
        requirePositiveId(changeId,"rentalChangeId"); requirePositiveId(storeId,"expectedStoreId");
        authorization.require(actor,ActionPermission.CUSTOMER_CHANGE_REQUEST,null);
        return mutateMany(actor,ids,equipment -> {
            requireReference(persistence.rejectedAdditionBelongsToCustomer(changeId,contractId,actor.identityId(),equipment.equipmentId(),storeId),
                    "The incoming equipment does not belong to the customer's rejected adjustment");
            equipment.releaseBeforeIssue(storeId);
        },"EQUIPMENT_REJECTION_RELEASED","rentalContractId="+contractId+";changeId="+changeId);
    }

    @Override
    @Transactional(propagation=Propagation.MANDATORY)
    public List<EquipmentSnapshot> replaceBeforeIssue(ActorContext actor,long contractId,long storeId,
            List<Long> releasedIds,List<Long> reservedIds) {
        requireActor(actor); requirePositiveId(contractId,"rentalContractId"); requirePositiveId(storeId,"expectedStoreId");
        authorization.require(actor,ActionPermission.EMPLOYEE_RENTAL_PROCESS,storeId);
        var released=sortedDistinctIds(releasedIds);
        var reserved=sortedDistinctIds(reservedIds);
        var all=new ArrayList<Long>(released); all.addAll(reserved);
        // mutateMany rejects overlap and locks the full union once, globally ascending.
        return mutateMany(actor,all,equipment -> {
            requireRentalContractReference(contractId,equipment.equipmentId());
            if(released.contains(equipment.equipmentId())) equipment.releaseBeforeIssue(storeId);
            else equipment.reserveForIssue(storeId);
        },"EQUIPMENT_DRAFT_REPLACED","rentalContractId="+contractId+";storeId="+storeId);
    }

    private final EquipmentPersistence persistence;
    private final AuditAppender auditAppender;
    private final Authorization authorization;
    private final Clock clock;

    public EquipmentApplicationService(
            EquipmentPersistence persistence,
            AuditAppender auditAppender,
            Authorization authorization,
            Clock clock) {
        this.persistence = persistence;
        this.auditAppender = auditAppender;
        this.authorization = authorization;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EquipmentSnapshot> selectAvailable(
            ActorContext actor,
            long storeId,
            String category,
            String skillLevel) {
        requireActor(actor);
        requirePositiveId(storeId, "storeId");
        if (hasGrant(actor, RoleCode.MANAGER, storeId)) {
            authorization.require(actor, ActionPermission.MANAGER_EQUIPMENT_OVERVIEW, storeId);
        } else {
            authorization.require(actor, ActionPermission.EMPLOYEE_EQUIPMENT_QUERY, storeId);
        }
        return persistence.findAvailable(storeId, optionalCode(category, "category"), optionalCode(skillLevel, "skillLevel"))
                .stream()
                .map(this::snapshot)
                .toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<EquipmentSnapshot> reserveForRental(
            ActorContext actor,
            long rentalContractId,
            long expectedStoreId,
            List<Long> equipmentIds) {
        requireActor(actor);
        requirePositiveId(rentalContractId, "rentalContractId");
        requirePositiveId(expectedStoreId, "expectedStoreId");
        authorization.require(actor, ActionPermission.EMPLOYEE_RENTAL_PROCESS, expectedStoreId);
        return mutateMany(
                actor,
                equipmentIds,
                equipment -> {
                    requireRentalContractReference(rentalContractId, equipment.equipmentId());
                    equipment.reserveForIssue(expectedStoreId);
                },
                "EQUIPMENT_RESERVED",
                "rentalContractId=%d;storeId=%d".formatted(rentalContractId, expectedStoreId));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<EquipmentSnapshot> releaseBeforeIssue(
            ActorContext actor,
            long rentalContractId,
            long expectedStoreId,
            List<Long> equipmentIds) {
        requireActor(actor);
        requirePositiveId(rentalContractId, "rentalContractId");
        requirePositiveId(expectedStoreId, "expectedStoreId");
        authorization.require(actor, ActionPermission.EMPLOYEE_RENTAL_PROCESS, expectedStoreId);
        return mutateMany(
                actor,
                equipmentIds,
                equipment -> {
                    requireRentalContractReference(rentalContractId, equipment.equipmentId());
                    equipment.releaseBeforeIssue(expectedStoreId);
                },
                "EQUIPMENT_RELEASED",
                "rentalContractId=%d;storeId=%d".formatted(rentalContractId, expectedStoreId));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<EquipmentSnapshot> issue(
            ActorContext actor,
            long rentalContractId,
            long expectedStoreId,
            List<Long> equipmentIds) {
        requireActor(actor);
        requirePositiveId(rentalContractId, "rentalContractId");
        requirePositiveId(expectedStoreId, "expectedStoreId");
        authorization.require(actor, ActionPermission.EMPLOYEE_RENTAL_PROCESS, expectedStoreId);
        return mutateMany(
                actor,
                equipmentIds,
                equipment -> {
                    requireRentalContractReference(rentalContractId, equipment.equipmentId());
                    equipment.issue(expectedStoreId);
                },
                "EQUIPMENT_ISSUED",
                "rentalContractId=%d;storeId=%d".formatted(rentalContractId, expectedStoreId));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public EquipmentSnapshot receiveReturn(
            ActorContext actor,
            long rentalItemId,
            long equipmentId,
            long returnStoreId) {
        requireActor(actor);
        requirePositiveId(rentalItemId, "rentalItemId");
        requirePositiveId(returnStoreId, "returnStoreId");
        authorization.require(actor, ActionPermission.EMPLOYEE_RETURN_AND_INSPECT, returnStoreId);
        return mutateOne(
                actor,
                equipmentId,
                equipment -> {
                    requireReference(
                            persistence.rentalItemReferencesEquipment(rentalItemId, equipment.equipmentId()),
                            "The rental item does not belong to the locked equipment.");
                    equipment.receiveReturn(returnStoreId);
                },
                "EQUIPMENT_RETURN_RECEIVED",
                "rentalItemId=%d;returnStoreId=%d".formatted(rentalItemId, returnStoreId));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public EquipmentSnapshot lockForRepairTaskCreation(ActorContext actor, long equipmentId) {
        requireActor(actor);
        Equipment equipment = lockOne(equipmentId);
        authorization.require(actor, ActionPermission.EMPLOYEE_REPAIR_PROCESS, equipment.currentStoreId());
        if (equipment.status() != EquipmentStatus.WAITING_REPAIR) {
            throw new EquipmentOperationException(Code.EQUIPMENT_STATE_CONFLICT,
                    "Equipment must be waiting for repair to create a task.");
        }
        return snapshot(equipment);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public EquipmentSnapshot startRepair(ActorContext actor, long repairTaskId, long equipmentId) {
        requireActor(actor);
        requirePositiveId(repairTaskId, "repairTaskId");
        return mutateOne(
                actor,
                equipmentId,
                equipment -> {
                    requireReference(
                            persistence.repairTaskReferencesEquipment(repairTaskId, equipment.equipmentId()),
                            "The repair task does not belong to the locked equipment.");
                    authorizeRepairActor(actor, equipment);
                    equipment.startRepair();
                },
                "EQUIPMENT_REPAIR_STARTED",
                "repairTaskId=%d".formatted(repairTaskId));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public EquipmentSnapshot submitRepair(ActorContext actor, long repairTaskId, long equipmentId) {
        requireActor(actor);
        requirePositiveId(repairTaskId, "repairTaskId");
        return mutateOne(
                actor,
                equipmentId,
                equipment -> {
                    requireReference(
                            persistence.repairTaskReferencesEquipment(repairTaskId, equipment.equipmentId()),
                            "The repair task does not belong to the locked equipment.");
                    authorizeRepairActor(actor, equipment);
                    equipment.submitRepair();
                },
                "EQUIPMENT_REPAIR_SUBMITTED",
                "repairTaskId=%d".formatted(repairTaskId));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<EquipmentSnapshot> dispatchTransfer(
            ActorContext actor,
            long transferId,
            long sourceStoreId,
            long destinationStoreId,
            List<Long> equipmentIds) {
        requireActor(actor);
        requirePositiveId(transferId, "transferId");
        authorization.require(actor, ActionPermission.EMPLOYEE_TRANSFER_HANDOFF, sourceStoreId);
        return mutateMany(
                actor,
                equipmentIds,
                equipment -> {
                    requireReference(
                            persistence.transferReferencesEquipment(transferId, equipment.equipmentId()),
                            "The transfer item does not belong to the locked equipment.");
                    equipment.dispatchTransfer(sourceStoreId, destinationStoreId);
                },
                "EQUIPMENT_TRANSFER_DISPATCHED",
                "transferId=%d;sourceStoreId=%d;destinationStoreId=%d".formatted(
                        transferId,
                        sourceStoreId,
                        destinationStoreId));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<EquipmentSnapshot> receiveTransfer(
            ActorContext actor,
            long transferId,
            long sourceStoreId,
            long destinationStoreId,
            boolean permanent,
            List<Long> equipmentIds) {
        requireActor(actor);
        requirePositiveId(transferId, "transferId");
        authorization.require(actor, ActionPermission.EMPLOYEE_TRANSFER_HANDOFF, destinationStoreId);
        return mutateMany(
                actor,
                equipmentIds,
                equipment -> {
                    requireReference(
                            persistence.transferReferencesEquipment(transferId, equipment.equipmentId()),
                            "The transfer item does not belong to the locked equipment.");
                    equipment.receiveTransfer(sourceStoreId, destinationStoreId, permanent);
                },
                "EQUIPMENT_TRANSFER_RECEIVED",
                "transferId=%d;sourceStoreId=%d;destinationStoreId=%d;permanent=%s".formatted(
                        transferId,
                        sourceStoreId,
                        destinationStoreId,
                        permanent));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public InspectionSnapshot inspectRentalReturn(ActorContext actor, long rentalItemId,
            long equipmentId, InspectionOutcome result, String faultDescription) {
        if (result == null) throw new IllegalArgumentException("Inspection outcome is required");
        return inspectReturn(actor, rentalItemId, equipmentId,
                InspectionResult.valueOf(result.name()), faultDescription);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public InspectionSnapshot inspectReturn(
            ActorContext actor,
            long rentalItemId,
            long equipmentId,
            InspectionResult result,
            String faultDescription) {
        requireActor(actor);
        requirePositiveId(rentalItemId, "rentalItemId");
        return inspect(
                actor,
                equipmentId,
                rentalItemId,
                null,
                InspectionType.RETURN,
                result,
                faultDescription,
                "EQUIPMENT_RETURN_INSPECTED");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public InspectionSnapshot inspectRepairAcceptance(
            ActorContext actor, long repairTaskId, long equipmentId,
            InspectionOutcome result, String faultDescription) {
        if (result == null || result == InspectionOutcome.PENDING_REVIEW) {
            throw new IllegalArgumentException("Repair acceptance outcome must be NORMAL or FAULT");
        }
        return inspectRepairAcceptance(actor, repairTaskId, equipmentId,
                InspectionResult.valueOf(result.name()), faultDescription);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public InspectionSnapshot inspectRepairAcceptance(
            ActorContext actor,
            long repairTaskId,
            long equipmentId,
            InspectionResult result,
            String faultDescription) {
        requireActor(actor);
        requirePositiveId(repairTaskId, "repairTaskId");
        return inspect(
                actor,
                equipmentId,
                null,
                repairTaskId,
                InspectionType.REPAIR_ACCEPTANCE,
                result,
                faultDescription,
                "EQUIPMENT_REPAIR_INSPECTED");
    }

    private InspectionSnapshot inspect(
            ActorContext actor,
            long equipmentId,
            Long rentalItemId,
            Long repairTaskId,
            InspectionType type,
            InspectionResult result,
            String faultDescription,
            String auditAction) {
        if (actor.identityType() != IdentityType.EMPLOYEE) {
            throw new IllegalArgumentException("equipment inspections require an employee actor");
        }
        Equipment equipment = lockOne(equipmentId);
        if (type == InspectionType.RETURN) {
            authorization.require(actor, ActionPermission.EMPLOYEE_RETURN_AND_INSPECT, equipment.currentStoreId());
        } else {
            authorization.require(actor, ActionPermission.EMPLOYEE_REPAIR_PROCESS, equipment.currentStoreId());
        }
        boolean matchingReference = type == InspectionType.RETURN
                ? persistence.rentalItemReferencesEquipment(rentalItemId, equipmentId)
                : persistence.repairTaskReferencesEquipment(repairTaskId, equipmentId);
        if (!matchingReference) {
            throw new EquipmentOperationException(
                    Code.EQUIPMENT_REFERENCE_CONFLICT,
                    "The inspection reference does not belong to the locked equipment.");
        }
        EquipmentInspection inspection = new EquipmentInspection(
                equipmentId,
                actor.identityId(),
                rentalItemId,
                repairTaskId,
                type,
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
                result,
                faultDescription);
        try {
            if (type == InspectionType.RETURN) {
                equipment.applyReturnInspection(inspection);
            } else {
                equipment.applyRepairAcceptance(inspection);
            }
        } catch (IllegalStateException exception) {
            throw conflict(exception);
        }
        persistence.saveAll(List.of(equipment));
        long inspectionId = persistence.appendInspection(inspection);
        String reference = rentalItemId != null
                ? "rentalItemId=" + rentalItemId
                : "repairTaskId=" + repairTaskId;
        auditAppender.appendSuccess(
                actor,
                "EQUIPMENT",
                equipmentId,
                auditAction,
                "%s;inspectionId=%d;result=%s".formatted(reference, inspectionId, result));
        return new InspectionSnapshot(
                inspectionId,
                equipmentId,
                inspection.inspectedByEmployeeId(),
                rentalItemId,
                repairTaskId,
                type,
                inspection.inspectedAt(),
                result,
                inspection.faultDescription(),
                snapshot(equipment));
    }

    private List<EquipmentSnapshot> mutateMany(
            ActorContext actor,
            List<Long> equipmentIds,
            EquipmentMutation mutation,
            String auditAction,
            String auditDetail) {
        List<Equipment> locked = lockAll(equipmentIds);
        for (Equipment equipment : locked) {
            try {
                mutation.apply(equipment);
            } catch (IllegalStateException exception) {
                throw conflict(exception);
            }
        }
        persistence.saveAll(locked);
        for (Equipment equipment : locked) {
            auditAppender.appendSuccess(
                    actor,
                    "EQUIPMENT",
                    equipment.equipmentId(),
                    auditAction,
                    auditDetail);
        }
        return locked.stream().map(this::snapshot).toList();
    }

    private EquipmentSnapshot mutateOne(
            ActorContext actor,
            long equipmentId,
            EquipmentMutation mutation,
            String auditAction,
            String auditDetail) {
        return mutateMany(actor, List.of(equipmentId), mutation, auditAction, auditDetail).getFirst();
    }

    private List<Equipment> lockAll(List<Long> equipmentIds) {
        List<Long> sorted = sortedDistinctIds(equipmentIds);
        List<Equipment> locked = persistence.lockAllInAscendingOrder(sorted);
        if (locked.size() != sorted.size()) {
            throw new EquipmentOperationException(Code.EQUIPMENT_NOT_FOUND, "One or more equipment items do not exist.");
        }
        return locked;
    }

    private Equipment lockOne(long equipmentId) {
        requirePositiveId(equipmentId, "equipmentId");
        return lockAll(List.of(equipmentId)).getFirst();
    }

    private List<Long> sortedDistinctIds(List<Long> equipmentIds) {
        if (equipmentIds == null || equipmentIds.isEmpty()) {
            throw new IllegalArgumentException("equipmentIds must not be empty");
        }
        ArrayList<Long> sorted = new ArrayList<>(equipmentIds.size());
        for (Long equipmentId : equipmentIds) {
            if (equipmentId == null) {
                throw new IllegalArgumentException("equipmentIds must not contain null");
            }
            requirePositiveId(equipmentId, "equipmentId");
            sorted.add(equipmentId);
        }
        if (new HashSet<>(sorted).size() != sorted.size()) {
            throw new IllegalArgumentException("equipmentIds must not contain duplicates");
        }
        sorted.sort(Long::compareTo);
        return List.copyOf(sorted);
    }

    private EquipmentOperationException conflict(IllegalStateException cause) {
        Code code = cause.getMessage() != null && cause.getMessage().contains("expected store")
                ? Code.EQUIPMENT_STORE_CONFLICT
                : Code.EQUIPMENT_STATE_CONFLICT;
        return new EquipmentOperationException(code, cause.getMessage());
    }

    private void authorizeRepairActor(ActorContext actor, Equipment equipment) {
        if (actor.identityType() == IdentityType.CONTRACTOR) {
            authorization.require(actor, ActionPermission.CONTRACTOR_REPAIR_TASK, null);
        } else {
            authorization.require(actor, ActionPermission.EMPLOYEE_REPAIR_PROCESS, equipment.currentStoreId());
        }
    }

    private void requireRentalContractReference(long rentalContractId, long equipmentId) {
        requireReference(
                persistence.rentalContractReferencesEquipment(rentalContractId, equipmentId),
                "The rental contract does not contain the locked equipment.");
    }

    private void requireReference(boolean matches, String message) {
        if (!matches) {
            throw new EquipmentOperationException(Code.EQUIPMENT_REFERENCE_CONFLICT, message);
        }
    }

    private boolean hasGrant(ActorContext actor, RoleCode role, long storeId) {
        return actor.activeGrants().stream().anyMatch(grant ->
                grant.role() == role && grant.storeId() != null && grant.storeId() == storeId);
    }

    private EquipmentSnapshot snapshot(Equipment equipment) {
        return new EquipmentSnapshot(
                equipment.equipmentId(),
                equipment.inventoryNumber(),
                equipment.equipmentName(),
                equipment.equipmentCategory(),
                equipment.skillLevel(),
                equipment.homeStoreId(),
                equipment.currentStoreId(),
                equipment.status(),
                equipment.currentDailyRate());
    }

    private void requireActor(ActorContext actor) {
        if (actor == null || actor.accountId() <= 0 || actor.identityId() <= 0) {
            throw new IllegalArgumentException("a trusted actor is required");
        }
    }

    private void requirePositiveId(long value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private String optionalCode(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 40) {
            throw new IllegalArgumentException(field + " must not exceed 40 characters");
        }
        return normalized;
    }

    @FunctionalInterface
    private interface EquipmentMutation {
        void apply(Equipment equipment);
    }
}
