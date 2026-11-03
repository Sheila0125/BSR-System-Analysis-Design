package com.bsr.identity.api;

import java.util.List;

public record ActorContext(
        long accountId,
        String loginName,
        IdentityType identityType,
        long identityId,
        List<ActiveRoleGrant> activeGrants) {

    public ActorContext {
        activeGrants = List.copyOf(activeGrants);
    }
}
