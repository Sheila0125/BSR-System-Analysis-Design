package com.bsr.audit.api;

import com.bsr.identity.api.ActorContext;

public interface AuditObjectScopeResolver {

    boolean supports(String objectType);

    void requireRead(ActorContext actor, long objectId);
}
