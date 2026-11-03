package com.bsr.equipment.api;

import com.bsr.equipment.domain.InspectionResult;
import com.bsr.equipment.domain.InspectionType;
import com.bsr.identity.api.ActorContext;
import java.time.LocalDateTime;

public interface InspectionOperations {

    InspectionSnapshot inspectRentalReturn(
            ActorContext actor, long rentalItemId, long equipmentId,
            InspectionOutcome result, String faultDescription);

    InspectionSnapshot inspectReturn(
            ActorContext actor,
            long rentalItemId,
            long equipmentId,
            InspectionResult result,
            String faultDescription);

    InspectionSnapshot inspectRepairAcceptance(
            ActorContext actor,
            long repairTaskId,
            long equipmentId,
            InspectionResult result,
            String faultDescription);

    InspectionSnapshot inspectRepairAcceptance(
            ActorContext actor,
            long repairTaskId,
            long equipmentId,
            InspectionOutcome result,
            String faultDescription);

    record InspectionSnapshot(
            long inspectionId,
            long equipmentId,
            long inspectedByEmployeeId,
            Long rentalItemId,
            Long repairTaskId,
            InspectionType type,
            LocalDateTime inspectedAt,
            InspectionResult result,
            String faultDescription,
            EquipmentSnapshot equipment) {
    }
}
