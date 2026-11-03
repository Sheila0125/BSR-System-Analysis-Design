package com.bsr.identity.application;

import com.bsr.audit.api.AuditObjectScopeResolver;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.Authorization;
import com.bsr.identity.api.RoleCode;
import com.bsr.identity.infrastructure.persistence.RoleGrantRepository;
import com.bsr.identity.infrastructure.persistence.RoleGrantRow;
import com.bsr.platform.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public final class RoleGrantAuditScopeResolver implements AuditObjectScopeResolver {

    private final RoleGrantRepository roleGrantRepository;
    private final Authorization authorization;

    public RoleGrantAuditScopeResolver(
            RoleGrantRepository roleGrantRepository,
            Authorization authorization) {
        this.roleGrantRepository = roleGrantRepository;
        this.authorization = authorization;
    }

    @Override
    public boolean supports(String objectType) {
        return "ROLE_GRANT".equals(objectType);
    }

    @Override
    public void requireRead(ActorContext actor, long objectId) {
        RoleGrantRow grant = roleGrantRepository.findById(objectId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "AUDIT_OBJECT_NOT_FOUND",
                        "The requested audit object does not exist."));
        if (grant.storeId() != null) {
            authorization.requireRoleAtStore(actor, RoleCode.MANAGER, grant.storeId());
        }
    }
}
