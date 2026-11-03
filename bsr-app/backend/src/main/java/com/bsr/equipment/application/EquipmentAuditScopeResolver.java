package com.bsr.equipment.application;

import com.bsr.audit.api.AuditObjectScopeResolver;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.Authorization;
import com.bsr.identity.api.RoleCode;
import com.bsr.platform.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Equipment audit access follows the equipment's current store, not a client-supplied scope. */
@Component
public final class EquipmentAuditScopeResolver implements AuditObjectScopeResolver {
    private final EquipmentCatalogPersistence equipment;
    private final Authorization authorization;

    public EquipmentAuditScopeResolver(EquipmentCatalogPersistence equipment, Authorization authorization) {
        this.equipment = equipment;
        this.authorization = authorization;
    }

    @Override public boolean supports(String type) { return "EQUIPMENT".equals(type); }

    @Override public void requireRead(ActorContext actor, long objectId) {
        var entry = equipment.findById(objectId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "AUDIT_OBJECT_NOT_FOUND", "Equipment not found."));
        authorization.requireRoleAtStore(actor, RoleCode.MANAGER, entry.currentStoreId());
    }
}
