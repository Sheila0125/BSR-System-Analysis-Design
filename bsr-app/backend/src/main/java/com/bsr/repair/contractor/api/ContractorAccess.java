package com.bsr.repair.contractor.api;

import java.util.Optional;

public interface ContractorAccess {

    Optional<ContractorSnapshot> findById(long contractorId);

    ContractorSnapshot lockForAssignment(long contractorId);

    ContractorSnapshot lockForRateChange(long contractorId);

    ContractorSnapshot lockForDeactivation(long contractorId);
}
