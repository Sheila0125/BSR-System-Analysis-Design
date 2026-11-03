package com.bsr.rental.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RentalContractTest {

    @Test
    @DisplayName("AC-BR10/16 reload preserves adjusted amount, never recharges cumulative historical items")
    void reloadUsesPersistedAdjustedAmount() {
        var restored=RentalContract.restore("RC-HISTORY",11,21,31,41,
                LocalDate.of(2026,12,19),LocalDate.of(2026,12,20),3,
                List.of(item(101,"9999999999.99")),RentalContractStatus.ACTIVE,new BigDecimal("219.50"));
        assertEquals(new BigDecimal("219.50"),restored.amountDue());
        restored.confirmAdjustment(new BigDecimal("-199.00"));
        assertEquals(new BigDecimal("20.50"),restored.amountDue());
        assertThrows(IllegalArgumentException.class,()->restored.confirmAdjustment(new BigDecimal("-20.51")));
    }

    @Test
    @DisplayName("AC-UC04/AC-BR07/AC-BR09 draft snapshots every item and calculates 99.50/40 x 3 as 418.50")
    void draftUsesPerItemWholeDaySnapshots() {
        List<RentalItem> selected = new ArrayList<>(List.of(
                RentalItem.pendingIssue(101L, new BigDecimal("99.50")),
                RentalItem.pendingIssue(102L, new BigDecimal("40.00"))));

        RentalContract contract = contract(selected);
        selected.clear();

        assertEquals(RentalContractStatus.DRAFT, contract.status());
        assertEquals(new BigDecimal("418.50"), contract.amountDue());
        assertEquals(LocalDate.of(2026, 12, 23), contract.rentalEndExclusive());
        assertEquals(2, contract.items().size());
        assertThrows(UnsupportedOperationException.class, () -> contract.items().clear());
    }

    @Test
    @DisplayName("AC-WX04/AC-BR11 customer confirmation and rejection use semantic contract actions")
    void confirmationLifecycleIsControlled() {
        RentalContract rejected = contract(List.of(item(101L, "99.50")));
        rejected.submitForCustomerConfirmation();
        rejected.rejectByCustomer();
        assertEquals(RentalContractStatus.DRAFT, rejected.status());

        RentalContract confirmed = contract(List.of(item(102L, "40.00")));
        confirmed.submitForCustomerConfirmation();
        confirmed.confirmByCustomer();
        assertEquals(RentalContractStatus.PENDING_PAYMENT, confirmed.status());

        assertThrows(IllegalStateException.class, confirmed::confirmByCustomer);
        assertEquals(RentalContractStatus.PENDING_PAYMENT, confirmed.status());
    }

    @Test
    @DisplayName("AC-BR07/AC-BR08 empty and duplicate per-equipment contracts are rejected")
    void contractRequiresUniquePhysicalItems() {
        assertThrows(IllegalArgumentException.class, () -> contract(List.of()));
        assertThrows(IllegalArgumentException.class, () -> contract(List.of(
                item(101L, "99.50"),
                item(101L, "99.50"))));
        assertThrows(IllegalArgumentException.class, () -> RentalItem.pendingIssue(101L, new BigDecimal("1.999")));
    }

    private RentalContract contract(List<RentalItem> items) {
        return RentalContract.createDraft(
                "CON-1001",
                11L,
                21L,
                31L,
                41L,
                LocalDate.of(2026, 10, 10),
                LocalDate.of(2026, 12, 20),
                3,
                items);
    }

    private RentalItem item(long equipmentId, String rate) {
        return RentalItem.pendingIssue(equipmentId, new BigDecimal(rate));
    }
}
