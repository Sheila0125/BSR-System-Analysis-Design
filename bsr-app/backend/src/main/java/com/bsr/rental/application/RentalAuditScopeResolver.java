package com.bsr.rental.application;

import com.bsr.audit.api.AuditObjectScopeResolver;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.Authorization;
import com.bsr.identity.api.RoleCode;
import com.bsr.platform.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public final class RentalAuditScopeResolver implements AuditObjectScopeResolver {
    private final RentalPersistence rentals;
    private final Authorization authorization;
    public RentalAuditScopeResolver(RentalPersistence rentals,Authorization authorization) { this.rentals=rentals; this.authorization=authorization; }
    @Override public boolean supports(String type) { return "RENTAL_CONTRACT".equals(type); }
    @Override public void requireRead(ActorContext actor,long id) {
        var contract=rentals.find(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"AUDIT_OBJECT_NOT_FOUND","Rental contract not found."));
        authorization.requireRoleAtStore(actor,RoleCode.MANAGER,contract.contract().serviceStoreId());
    }
}
