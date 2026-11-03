package com.bsr.storetransfer.transfer.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransferAggregateTest {
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 13, 1, 0);

    @Test
    @DisplayName("AC-BR27/AC-BR32 rejects same stores and non-unique or empty equipment")
    void initiationRequiresDistinctStoresAndUniqueNonemptyEquipment() {
        assertThrows(IllegalArgumentException.class, () -> create(21, 21, List.of(101L)));
        assertThrows(IllegalArgumentException.class, () -> create(21, 22, List.of()));
        assertThrows(IllegalArgumentException.class, () -> create(21, 22, List.of(101L, 101L)));
        assertThrows(IllegalArgumentException.class, () -> create(21, 22, List.of(0L)));
    }

    @Test
    @DisplayName("AC-BR28/AC-BR31 derives all five root states from three item facts")
    void threeItemsProgressThroughAllFiveFrozenStatuses() {
        TransferAggregate transfer = create(21, 22, List.of(101L, 102L, 103L));
        assertEquals(TransferStatus.INITIATED, transfer.status());
        assertThrows(IllegalStateException.class, () -> transfer.dispatch(101L, 31L, START.plusMinutes(1)));

        assertTrue(transfer.approve(41L, START.plusMinutes(2)));
        assertEquals(TransferStatus.APPROVED, transfer.status());
        assertFalse(transfer.approve(42L, START.plusMinutes(3)));
        assertEquals(41L, transfer.approvedBy());
        assertEquals(START.plusMinutes(2), transfer.approvedAt());

        transfer.dispatch(101L, 31L, START.plusMinutes(4));
        transfer.dispatch(102L, 31L, START.plusMinutes(5));
        transfer.dispatch(103L, 31L, START.plusMinutes(6));
        assertEquals(TransferStatus.IN_TRANSIT, transfer.status());

        transfer.receive(101L, 51L, START.plusMinutes(7));
        assertEquals(TransferStatus.PARTIALLY_RECEIVED, transfer.status());
        transfer.receive(102L, 51L, START.plusMinutes(8));
        assertEquals(TransferStatus.PARTIALLY_RECEIVED, transfer.status());
        transfer.receive(103L, 51L, START.plusMinutes(9));
        assertEquals(TransferStatus.COMPLETED, transfer.status());
        assertEquals(START.plusMinutes(9), transfer.completedAt());
        assertFalse(transfer.receive(103L, 52L, START.plusMinutes(10)));
        assertEquals(START.plusMinutes(9), transfer.completedAt());
    }

    @Test
    @DisplayName("AC-BR30/AC-BR31 exception facts do not fabricate handoff or completion")
    void exceptionDoesNotFabricateHandoffOrCompleteRoot() {
        TransferAggregate transfer = create(21, 22, List.of(101L, 102L));
        transfer.approve(41L, START.plusMinutes(1));
        transfer.reportException(101L, TransferItem.Phase.DISPATCH, "Not found", 31L, START.plusMinutes(1));
        assertEquals(TransferStatus.APPROVED, transfer.status());
        transfer.dispatch(101L, 31L, START.plusMinutes(2));
        transfer.reportException(101L, TransferItem.Phase.RECEIPT, "Seal damaged", 51L, START.plusMinutes(3));
        assertEquals(TransferStatus.IN_TRANSIT, transfer.status());
        transfer.receive(101L, 51L, START.plusMinutes(3));
        assertEquals(TransferStatus.PARTIALLY_RECEIVED, transfer.status());
        assertThrows(IllegalArgumentException.class,
                () -> transfer.dispatch(999L, 31L, START.plusMinutes(4)));
    }

    private TransferAggregate create(long source, long destination, List<Long> items) {
        return TransferAggregate.initiate("TR-001", TransferType.TEMPORARY, source, destination,
                41L, START, items);
    }
}
