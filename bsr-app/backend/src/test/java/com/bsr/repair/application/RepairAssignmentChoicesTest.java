package com.bsr.repair.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bsr.identity.api.ActionPermission;
import com.bsr.identity.api.ActiveRoleGrant;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.Authorization;
import com.bsr.identity.api.IdentityType;
import com.bsr.identity.api.RoleCode;
import com.bsr.platform.error.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RepairAssignmentChoicesTest {
    private final RepairManagementPersistence persistence = mock(RepairManagementPersistence.class);
    private final Authorization authorization = mock(Authorization.class);
    private final RepairContractorService service =
            new RepairContractorService(persistence, null, null, null, null, authorization, null, null);

    @Test
    void includesOnlyActiveContractorsForEmployeesWithPermissionAtTheStore() {
        var active = new RepairViews.Contractor(7, "R7", "Active", null, "a@test.invalid", null, true);
        var inactive = new RepairViews.Contractor(8, "R8", "Inactive", null, "b@test.invalid", null, false);
        when(persistence.listContractors()).thenReturn(List.of(active, inactive));

        var employee = actor(RoleCode.EMPLOYEE);
        assertEquals(List.of(active), service.assignmentChoices(employee, 11L));
        verify(authorization).require(employee, ActionPermission.EMPLOYEE_REPAIR_PROCESS, 11L);
    }

    @Test
    void doesNotReadContractorsWhenStoreAssignmentPermissionIsDenied() {
        var employee = actor(RoleCode.EMPLOYEE);
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "ACTION_NOT_AUTHORIZED", "Outside store scope"))
                .when(authorization).require(employee, ActionPermission.EMPLOYEE_REPAIR_PROCESS, 12L);
        assertThrows(ApiException.class, () -> service.assignmentChoices(employee, 12L));
        verifyNoInteractions(persistence);
    }

    private ActorContext actor(RoleCode role) {
        return new ActorContext(1L, "employee", IdentityType.EMPLOYEE, 2L,
                List.of(new ActiveRoleGrant(role, 11L)));
    }
}
