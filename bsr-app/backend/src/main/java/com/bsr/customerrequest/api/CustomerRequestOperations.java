package com.bsr.customerrequest.api;

import com.bsr.identity.api.ActorContext;

public interface CustomerRequestOperations {

    RentalRequestSnapshot loadOwned(ActorContext actor, long requestId);

    VerifiedCustomer verifyCustomer(ActorContext actor, long storeId, String customerNumber, String email);

    RentalRequestSnapshot accept(ActorContext actor, long requestId);

    RentalRequestSnapshot lockAcceptedForConversion(ActorContext actor, long requestId);

    RentalRequestSnapshot markConverted(ActorContext actor, long requestId, long contractId);
}
