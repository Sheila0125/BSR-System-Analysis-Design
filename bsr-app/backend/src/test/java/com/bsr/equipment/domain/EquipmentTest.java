package com.bsr.equipment.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EquipmentTest {

    @Test
    @DisplayName("AC-UC04/AC-BR12 reserve, release and issue use controlled semantic actions")
    void rentalIssueLifecycle() {
        Equipment equipment = availableEquipment();

        equipment.reserveForIssue(21L);
        assertEquals(EquipmentStatus.PENDING_ISSUE, equipment.status());

        equipment.releaseBeforeIssue(21L);
        assertEquals(EquipmentStatus.AVAILABLE, equipment.status());

        equipment.reserveForIssue(21L);
        equipment.issue(21L);
        assertEquals(EquipmentStatus.RENTED, equipment.status());
    }

    @Test
    @DisplayName("AC-UC06/AC-BR14 return updates only current store before a normal inspection")
    void crossStoreReturnPreservesHomeStore() {
        Equipment equipment = equipment(EquipmentStatus.RENTED, 21L, 21L);

        equipment.receiveReturn(22L);
        assertEquals(EquipmentStatus.RETURN_INSPECTION, equipment.status());
        assertEquals(21L, equipment.homeStoreId());
        assertEquals(22L, equipment.currentStoreId());

        equipment.applyReturnInspection(returnInspection(InspectionResult.NORMAL, null));
        assertEquals(EquipmentStatus.AVAILABLE, equipment.status());
        assertEquals(21L, equipment.homeStoreId());
        assertEquals(22L, equipment.currentStoreId());
    }

    @Test
    @DisplayName("AC-UC06/AC-BR15 return inspection maps fault and identity differences to frozen states")
    void returnInspectionUsesAllOutcomes() {
        Equipment fault = equipment(EquipmentStatus.RETURN_INSPECTION, 21L, 22L);
        fault.applyReturnInspection(returnInspection(InspectionResult.FAULT, "Broken binding"));
        assertEquals(EquipmentStatus.WAITING_REPAIR, fault.status());

        Equipment review = equipment(EquipmentStatus.RETURN_INSPECTION, 21L, 22L);
        review.applyReturnInspection(returnInspection(InspectionResult.PENDING_REVIEW, "Inventory tag differs"));
        assertEquals(EquipmentStatus.PENDING_REVIEW, review.status());

        assertThrows(IllegalStateException.class, () -> review.reserveForIssue(22L));
        assertEquals(EquipmentStatus.PENDING_REVIEW, review.status());
    }

    @Test
    @DisplayName("AC-UC07/AC-UC09/AC-BR15 repair acceptance alone restores availability")
    void repairLifecycleRequiresAcceptance() {
        Equipment equipment = equipment(EquipmentStatus.WAITING_REPAIR, 21L, 22L);

        equipment.startRepair();
        equipment.submitRepair();
        assertEquals(EquipmentStatus.PENDING_ACCEPTANCE, equipment.status());

        equipment.applyRepairAcceptance(repairInspection(InspectionResult.FAULT, "Still unsafe"));
        assertEquals(EquipmentStatus.WAITING_REPAIR, equipment.status());

        equipment.startRepair();
        equipment.submitRepair();
        equipment.applyRepairAcceptance(repairInspection(InspectionResult.NORMAL, null));
        assertEquals(EquipmentStatus.AVAILABLE, equipment.status());
    }

    @Test
    @DisplayName("AC-UC11/AC-BR14 temporary and permanent transfer update different store roles")
    void transferPreservesOrMovesHomeStore() {
        Equipment temporary = availableEquipment();
        temporary.dispatchTransfer(21L, 22L);
        assertEquals(EquipmentStatus.IN_TRANSFER, temporary.status());
        assertEquals(21L, temporary.currentStoreId());
        temporary.receiveTransfer(21L, 22L, false);
        assertEquals(EquipmentStatus.AVAILABLE, temporary.status());
        assertEquals(21L, temporary.homeStoreId());
        assertEquals(22L, temporary.currentStoreId());

        Equipment permanent = availableEquipment();
        permanent.dispatchTransfer(21L, 22L);
        permanent.receiveTransfer(21L, 22L, true);
        assertEquals(22L, permanent.homeStoreId());
        assertEquals(22L, permanent.currentStoreId());
    }

    @Test
    @DisplayName("AC-BR13/AC-BR15 illegal stage, store and inspection associations fail without mutation")
    void invalidActionsDoNotMutateEquipment() {
        Equipment equipment = availableEquipment();

        assertThrows(IllegalStateException.class, () -> equipment.issue(21L));
        assertThrows(IllegalStateException.class, () -> equipment.reserveForIssue(22L));
        assertThrows(IllegalArgumentException.class, () -> equipment.dispatchTransfer(21L, 21L));
        assertEquals(EquipmentStatus.AVAILABLE, equipment.status());
        assertEquals(21L, equipment.currentStoreId());

        Equipment returned = equipment(EquipmentStatus.RETURN_INSPECTION, 21L, 21L);
        EquipmentInspection wrongEquipment = new EquipmentInspection(
                999L,
                31L,
                41L,
                null,
                InspectionType.RETURN,
                LocalDateTime.of(2026, 10, 7, 9, 0),
                InspectionResult.NORMAL,
                null);
        assertThrows(IllegalArgumentException.class, () -> returned.applyReturnInspection(wrongEquipment));
        assertEquals(EquipmentStatus.RETURN_INSPECTION, returned.status());
    }

    @Test
    @DisplayName("AC-BR15 inspection facts require the correct source reference and result domain")
    void inspectionFactIsComplete() {
        assertThrows(IllegalArgumentException.class, () -> new EquipmentInspection(
                11L,
                31L,
                null,
                null,
                InspectionType.RETURN,
                LocalDateTime.of(2026, 10, 7, 9, 0),
                InspectionResult.NORMAL,
                null));
        assertThrows(IllegalArgumentException.class, () -> new EquipmentInspection(
                11L,
                31L,
                null,
                51L,
                InspectionType.REPAIR_ACCEPTANCE,
                LocalDateTime.of(2026, 10, 7, 9, 0),
                InspectionResult.PENDING_REVIEW,
                "Unclear"));

        EquipmentInspection optionalDescription = returnInspection(InspectionResult.FAULT, " ");
        assertNull(optionalDescription.faultDescription());
    }

    private Equipment availableEquipment() {
        return equipment(EquipmentStatus.AVAILABLE, 21L, 21L);
    }

    private Equipment equipment(EquipmentStatus status, long homeStoreId, long currentStoreId) {
        return Equipment.restore(
                11L,
                "INV-1001",
                "All-mountain skis",
                "SKI",
                "INTERMEDIATE",
                homeStoreId,
                currentStoreId,
                status,
                LocalDate.of(2025, 1, 1),
                new BigDecimal("1000.00"),
                new BigDecimal("99.50"));
    }

    private EquipmentInspection returnInspection(InspectionResult result, String description) {
        return new EquipmentInspection(
                11L,
                31L,
                41L,
                null,
                InspectionType.RETURN,
                LocalDateTime.of(2026, 10, 7, 9, 0),
                result,
                description);
    }

    private EquipmentInspection repairInspection(InspectionResult result, String description) {
        return new EquipmentInspection(
                11L,
                31L,
                null,
                51L,
                InspectionType.REPAIR_ACCEPTANCE,
                LocalDateTime.of(2026, 10, 7, 9, 0),
                result,
                description);
    }
}
