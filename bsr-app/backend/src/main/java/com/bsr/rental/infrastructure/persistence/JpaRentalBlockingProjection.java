package com.bsr.rental.infrastructure.persistence;

import com.bsr.rental.api.RentalBlockingProjection;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaRentalBlockingProjection implements RentalBlockingProjection {
    private final RentalContractRepository contracts;

    public JpaRentalBlockingProjection(RentalContractRepository contracts) {
        this.contracts = contracts;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UnfinishedContract> unfinishedAtStore(long storeId) {
        return contracts.unfinishedAtStore(storeId).stream()
                .map(row -> new UnfinishedContract(row.contractId, row.serviceStoreId, row.contractStatus))
                .toList();
    }
}
