package com.bsr.identity.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bsr.identity.api.ActionPermission;
import com.bsr.identity.api.ActiveRoleGrant;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.IdentityType;
import com.bsr.identity.api.RoleCode;
import com.bsr.platform.error.ApiException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FixedAuthorizationTest {

    private final FixedAuthorization authorization = new FixedAuthorization();

    @Test
    @DisplayName("AC-BR02 manager does not inherit employee rental processing")
    void AC_BR02_managerDoesNotInheritEmployeeActions() {
        ActorContext manager = actor(IdentityType.EMPLOYEE, 10L, RoleCode.MANAGER, 2L);

        assertDoesNotThrow(() -> authorization.require(
                manager, ActionPermission.MANAGER_TRANSFER, 2L));
        assertThrows(ApiException.class, () -> authorization.require(
                manager, ActionPermission.EMPLOYEE_RENTAL_PROCESS, 2L));
    }

    @Test
    @DisplayName("AC-BR02 explicit store scope is required and cannot become global")
    void AC_BR02_storeScopeMustMatchExactly() {
        ActorContext employee = actor(IdentityType.EMPLOYEE, 10L, RoleCode.EMPLOYEE, 2L);

        assertDoesNotThrow(() -> authorization.require(
                employee, ActionPermission.EMPLOYEE_RETURN_AND_INSPECT, 2L));
        assertThrows(ApiException.class, () -> authorization.require(
                employee, ActionPermission.EMPLOYEE_RETURN_AND_INSPECT, 3L));
        assertThrows(ApiException.class, () -> authorization.require(
                employee, ActionPermission.EMPLOYEE_RETURN_AND_INSPECT, null));
    }

    @Test
    @DisplayName("AC-BR02 role administration requires an explicit manager grant at the target store")
    void AC_BR02_roleAdministrationUsesManagerStoreScope() {
        ActorContext manager = actor(IdentityType.EMPLOYEE, 10L, RoleCode.MANAGER, 2L);

        assertDoesNotThrow(() -> authorization.requireRoleAtStore(manager, RoleCode.MANAGER, 2L));
        assertThrows(ApiException.class, () ->
                authorization.requireRoleAtStore(manager, RoleCode.MANAGER, 3L));
        assertThrows(ApiException.class, () ->
                authorization.requireRoleAtStore(manager, RoleCode.EMPLOYEE, 2L));
    }

    @Test
    @DisplayName("AC-BR03 customer and contractor object ownership is enforced")
    void AC_BR03_businessIdentityOwnsOnlyItsObjects() {
        ActorContext customer = actor(IdentityType.CUSTOMER, 21L, RoleCode.CUSTOMER, null);
        ActorContext contractor = actor(IdentityType.CONTRACTOR, 31L, RoleCode.CONTRACTOR, null);

        assertDoesNotThrow(() -> authorization.requireCustomerOwnership(customer, 21L));
        assertThrows(ApiException.class, () -> authorization.requireCustomerOwnership(customer, 22L));
        assertDoesNotThrow(() -> authorization.requireContractorOwnership(contractor, 31L));
        assertThrows(ApiException.class, () -> authorization.requireContractorOwnership(contractor, 32L));
    }

    @Test
    @DisplayName("AC-BR02 customer and contractor actions require their explicit roles")
    void AC_BR02_customerAndContractorActionsAreExplicit() {
        ActorContext customer = actor(IdentityType.CUSTOMER, 21L, RoleCode.CUSTOMER, null);
        ActorContext contractor = actor(IdentityType.CONTRACTOR, 31L, RoleCode.CONTRACTOR, null);

        assertDoesNotThrow(() -> authorization.require(
                customer, ActionPermission.CUSTOMER_RENTAL_READ, null));
        assertThrows(ApiException.class, () -> authorization.require(
                customer, ActionPermission.CONTRACTOR_REPAIR_TASK, null));
        assertDoesNotThrow(() -> authorization.require(
                contractor, ActionPermission.CONTRACTOR_REPAIR_TASK, null));
        assertThrows(ApiException.class, () -> authorization.require(
                contractor, ActionPermission.CUSTOMER_RENTAL_READ, null));
    }

    @Test
    @DisplayName("AC-BR02 an employee receives only the explicitly combined roles and store scopes")
    void AC_BR02_multiRoleEmployeeCombinesOnlyExplicitGrants() {
        ActorContext multiRole = new ActorContext(
                1L,
                "multi-role",
                IdentityType.EMPLOYEE,
                10L,
                List.of(
                        new ActiveRoleGrant(RoleCode.MANAGER, 2L),
                        new ActiveRoleGrant(RoleCode.EMPLOYEE, 3L)));

        assertDoesNotThrow(() -> authorization.require(
                multiRole, ActionPermission.MANAGER_TRANSFER, 2L));
        assertDoesNotThrow(() -> authorization.require(
                multiRole, ActionPermission.EMPLOYEE_RENTAL_PROCESS, 3L));
        assertThrows(ApiException.class, () -> authorization.require(
                multiRole, ActionPermission.EMPLOYEE_RENTAL_PROCESS, 2L));
        assertThrows(ApiException.class, () -> authorization.require(
                multiRole, ActionPermission.MANAGER_TRANSFER, 3L));
    }

    private ActorContext actor(
            IdentityType identityType,
            long identityId,
            RoleCode role,
            Long storeId) {
        return new ActorContext(
                1L,
                "test-account",
                identityType,
                identityId,
                List.of(new ActiveRoleGrant(role, storeId)));
    }
}
