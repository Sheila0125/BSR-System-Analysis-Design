package com.bsr.storetransfer.transfer.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransferItemTest {
    private static final LocalDateTime FIRST = LocalDateTime.of(2026, 10, 13, 2, 0);

    @Test
    @DisplayName("AC-BR30 exception retry preserves physical facts and trace note")
    void exceptionAndRetryPreservePhysicalFactsAndNote() {
        TransferItem item = TransferItem.pending(101L);
        item.reportException(TransferItem.Phase.DISPATCH, "Tag mismatch", 31L, FIRST);
        assertEquals(TransferItem.DispatchResult.EXCEPTION, item.dispatchResult());
        assertEquals(31L, item.dispatchedBy());
        assertEquals(FIRST, item.dispatchedAt());
        assertEquals(TransferItem.ReceiptResult.PENDING, item.receiptResult());

        assertTrue(item.dispatch(31L, FIRST));
        assertEquals(TransferItem.DispatchResult.DISPATCHED, item.dispatchResult());
        assertEquals(31L, item.dispatchedBy());
        assertEquals(FIRST, item.dispatchedAt());
        assertEquals("Tag mismatch", item.exceptionNote());

        item.reportException(TransferItem.Phase.RECEIPT, "Box missing", 51L, FIRST.plusSeconds(30));
        assertEquals(TransferItem.ReceiptResult.EXCEPTION, item.receiptResult());
        assertEquals(51L, item.receivedBy());
        assertEquals(FIRST.plusSeconds(30), item.receivedAt());
        assertTrue(item.receive(51L, FIRST.plusMinutes(1)));
        assertEquals(TransferItem.ReceiptResult.RECEIVED, item.receiptResult());
        assertEquals("Box missing", item.exceptionNote());
    }

    @Test
    @DisplayName("AC-BR31 repeated handoff preserves original actor and time")
    void repeatHandoffDoesNotRewriteOriginalActorOrTime() {
        TransferItem item = TransferItem.pending(101L);
        assertThrows(IllegalStateException.class, () -> item.receive(51L, FIRST));
        assertTrue(item.dispatch(31L, FIRST));
        assertFalse(item.dispatch(32L, FIRST.plusMinutes(1)));
        assertEquals(31L, item.dispatchedBy());
        assertEquals(FIRST, item.dispatchedAt());
        assertTrue(item.receive(51L, FIRST.plusMinutes(2)));
        assertFalse(item.receive(52L, FIRST.plusMinutes(3)));
        assertEquals(51L, item.receivedBy());
        assertEquals(FIRST.plusMinutes(2), item.receivedAt());
        assertThrows(IllegalStateException.class,
                () -> item.reportException(TransferItem.Phase.RECEIPT, "Too late", 51L, FIRST.plusMinutes(4)));
    }

    @Test
    @DisplayName("AC-BR30 rejects wrong exception phase and unbounded note")
    void exceptionRequiresCorrectPhaseAndBoundedNote() {
        TransferItem item = TransferItem.pending(101L);
        assertThrows(IllegalStateException.class,
                () -> item.reportException(TransferItem.Phase.RECEIPT, "Before dispatch", 51L, FIRST));
        assertThrows(IllegalArgumentException.class,
                () -> item.reportException(TransferItem.Phase.DISPATCH, " ", 31L, FIRST));
        assertThrows(IllegalArgumentException.class,
                () -> item.reportException(TransferItem.Phase.DISPATCH, "x".repeat(501), 31L, FIRST));
        item.dispatch(31L, FIRST);
        assertThrows(IllegalStateException.class,
                () -> item.reportException(TransferItem.Phase.DISPATCH, "Too late", 31L, FIRST.plusMinutes(1)));
    }
}
