package com.bsr.storetransfer.store.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bsr.identity.api.ActiveRoleGrant;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.IdentityType;
import com.bsr.identity.api.RoleCode;
import com.bsr.storetransfer.infrastructure.persistence.StoreRepository;
import com.bsr.storetransfer.infrastructure.persistence.StoreRow;
import com.bsr.storetransfer.store.api.StoreSnapshot;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class StoreChoicesTest {
    private final StoreRepository repository = mock(StoreRepository.class);
    private final StoreAdministrationService service =
            new StoreAdministrationService(repository, null, null, null, null);

    @Test
    void readsOnlyExplicitEmployeeAndManagerStoresWithoutDuplicates() {
        var employeeStore = snapshot(11L);
        var managerStore = snapshot(12L);
        var rows = List.of(row(employeeStore), row(managerStore));
        when(repository.findAllById(List.of(11L, 12L))).thenReturn(rows);

        var actor = new ActorContext(1L, "employee", IdentityType.EMPLOYEE, 2L, List.of(
                new ActiveRoleGrant(RoleCode.EMPLOYEE, 11L),
                new ActiveRoleGrant(RoleCode.MANAGER, 11L),
                new ActiveRoleGrant(RoleCode.MANAGER, 12L)));

        assertEquals(List.of(employeeStore, managerStore), service.choices(actor));
        verify(repository).findAllById(List.of(11L, 12L));
    }

    @Test
    void customerCannotReadAdministrationStoreChoices() {
        var actor = new ActorContext(1L, "customer", IdentityType.CUSTOMER, 2L,
                List.of(new ActiveRoleGrant(RoleCode.CUSTOMER, null)));
        assertEquals(List.of(), service.choices(actor));
        verifyNoInteractions(repository);
    }

    private StoreSnapshot snapshot(long id) {
        return new StoreSnapshot(id, "S-" + id, "Store " + id, null, true,
                LocalTime.of(8, 0), LocalTime.of(22, 0));
    }

    private StoreRow row(StoreSnapshot store) {
        var row = mock(StoreRow.class);
        when(row.snapshot()).thenReturn(store);
        return row;
    }
}
