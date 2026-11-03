package com.bsr.identity.api;

public interface Authorization {

    void require(ActorContext actor, ActionPermission action, Long storeId);

    void requireRoleAtStore(ActorContext actor, RoleCode role, long storeId);

    void requireCustomerOwnership(ActorContext actor, long customerId);

    void requireContractorOwnership(ActorContext actor, long contractorId);
}
