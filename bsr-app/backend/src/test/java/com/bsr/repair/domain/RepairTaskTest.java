package com.bsr.repair.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RepairTaskTest {

    @Test
    @DisplayName("AC-UC07/08/09 all seven approved repair states follow semantic actions")
    void AC_UC07_UC08_UC09_sevenStateLifecycle() {
        RepairTask task = RepairTask.create(null);
        assertEquals(RepairTaskStatus.UNASSIGNED, task.status());

        task.assign(41L);
        assertEquals(RepairTaskStatus.PENDING, task.status());
        task.start(41L);
        assertEquals(RepairTaskStatus.IN_PROGRESS, task.status());
        task.submit(41L, 1);
        assertEquals(RepairTaskStatus.PENDING_ACCEPTANCE, task.status());

        task.reject("Repair still fails under load");
        assertEquals(RepairTaskStatus.RETURNED, task.status());
        assertEquals(RepairAcceptanceResult.FAILED, task.acceptanceResult());
        task.start(41L);
        task.submit(41L, 2);
        task.accept();
        assertEquals(RepairTaskStatus.PENDING_CHARGE, task.status());
        assertEquals(RepairAcceptanceResult.PASSED, task.acceptanceResult());

        assertThrows(IllegalStateException.class, () -> task.complete(false));
        task.complete(true);
        assertEquals(RepairTaskStatus.COMPLETED, task.status());
        assertThrows(IllegalStateException.class, () -> task.assign(42L));
        assertThrows(IllegalStateException.class, () -> task.start(41L));
    }

    @Test
    @DisplayName("AC-BR23 active reassignment revokes old owner without resetting equipment progress")
    void AC_BR23_reassignmentAndOwnerChecks() {
        RepairTask task = RepairTask.create(41L);
        task.assign(42L);
        assertEquals(42L, task.currentContractorId());
        assertThrows(IllegalStateException.class, () -> task.start(41L));
        task.start(42L);
        task.assign(41L);
        assertEquals(RepairTaskStatus.IN_PROGRESS, task.status());
        assertThrows(IllegalStateException.class, () -> task.submit(42L, 1));
        assertThrows(IllegalStateException.class, () -> task.submit(41L, 0));
        task.submit(41L, 1);
        assertThrows(IllegalArgumentException.class, () -> task.reject("   "));
        assertEquals(RepairTaskStatus.PENDING_ACCEPTANCE, task.status());
    }

    @Test
    @DisplayName("AC-UC09 accepting before submission and repeating terminal actions are rejected")
    void AC_UC09_illegalTransitionsLeaveStateUnchanged() {
        RepairTask task = RepairTask.create(null);
        assertThrows(IllegalStateException.class, task::accept);
        assertThrows(IllegalStateException.class, () -> task.submit(41L, 1));
        assertEquals(RepairTaskStatus.UNASSIGNED, task.status());
    }

    @Test
    @DisplayName("AC-UC08 reloaded task keeps current assignee and rejects another contractor")
    void AC_UC08_restoredTaskPreservesOwnerAndState() {
        RepairTask task = RepairTask.restore(RepairTaskStatus.IN_PROGRESS, 42L, null);
        assertEquals(42L, task.currentContractorId());
        assertThrows(IllegalStateException.class, () -> task.submit(41L, 1));
        task.submit(42L, 1);
        assertEquals(RepairTaskStatus.PENDING_ACCEPTANCE, task.status());
    }
}
