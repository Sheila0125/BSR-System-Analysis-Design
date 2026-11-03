package com.bsr.identity.application;

import com.bsr.identity.api.ActionPermission;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.Authorization;
import com.bsr.identity.api.IdentityType;
import com.bsr.identity.api.RoleCode;
import com.bsr.platform.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public final class FixedAuthorization implements Authorization {

    @Override
    public void require(ActorContext actor, ActionPermission action, Long storeId) {
        boolean granted = actor.activeGrants().stream().anyMatch(grant ->
                grant.role() == action.requiredRole()
                        && (!action.storeScoped() || grant.storeId() != null && grant.storeId().equals(storeId)));
        if (!granted) {
            throw forbidden("ACTION_NOT_AUTHORIZED", "The current account is not authorized for this action.");
        }
    }

    @Override
    public void requireRoleAtStore(ActorContext actor, RoleCode role, long storeId) {
        boolean granted = actor.activeGrants().stream().anyMatch(grant ->
                grant.role() == role && grant.storeId() != null && grant.storeId() == storeId);
        if (!granted) {
            throw forbidden("STORE_SCOPE_DENIED", "The requested store is outside the current account scope.");
        }
    }

    @Override
    public void requireCustomerOwnership(ActorContext actor, long customerId) {
        if (actor.identityType() != IdentityType.CUSTOMER || actor.identityId() != customerId) {
            throw forbidden("CUSTOMER_SCOPE_DENIED", "The requested customer object is outside the current account scope.");
        }
    }

    @Override
    public void requireContractorOwnership(ActorContext actor, long contractorId) {
        if (actor.identityType() != IdentityType.CONTRACTOR || actor.identityId() != contractorId) {
            throw forbidden("CONTRACTOR_SCOPE_DENIED", "The requested contractor object is outside the current account scope.");
        }
    }

    private ApiException forbidden(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message);
    }
}
