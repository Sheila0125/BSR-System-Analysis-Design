package com.bsr.repair.contractor.infrastructure;

import com.bsr.repair.contractor.api.ContractorAccess;
import com.bsr.repair.contractor.api.ContractorAccessException;
import com.bsr.repair.contractor.api.ContractorSnapshot;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaContractorAccess implements ContractorAccess {

    private static final String SELECT = """
            SELECT contractor_id, contractor_number, contractor_name, contact_name,
                   email, phone, business_status
            FROM repair_contractor
            WHERE contractor_id = :contractorId
            """;

    private final EntityManager entityManager;

    public JpaContractorAccess(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ContractorSnapshot> findById(long contractorId) {
        requirePositive(contractorId);
        return query(contractorId, "");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ContractorSnapshot lockForAssignment(long contractorId) {
        ContractorSnapshot contractor = required(contractorId, " FOR SHARE");
        requireActive(contractor);
        return contractor;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ContractorSnapshot lockForRateChange(long contractorId) {
        ContractorSnapshot contractor = required(contractorId, " FOR UPDATE");
        requireActive(contractor);
        return contractor;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ContractorSnapshot lockForDeactivation(long contractorId) {
        return required(contractorId, " FOR UPDATE");
    }

    private ContractorSnapshot required(long contractorId, String lockClause) {
        requirePositive(contractorId);
        return query(contractorId, lockClause).orElseThrow(() -> new ContractorAccessException(
                ContractorAccessException.Code.CONTRACTOR_NOT_FOUND, "Contractor does not exist."));
    }

    private Optional<ContractorSnapshot> query(long contractorId, String lockClause) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(SELECT + lockClause)
                .setParameter("contractorId", contractorId)
                .getResultList();
        return rows.stream().findFirst().map(JpaContractorAccess::snapshot);
    }

    private static ContractorSnapshot snapshot(Object[] row) {
        return new ContractorSnapshot(
                ((Number) row[0]).longValue(),
                (String) row[1],
                (String) row[2],
                (String) row[3],
                (String) row[4],
                (String) row[5],
                "ACTIVE".equals(row[6]));
    }

    private static void requireActive(ContractorSnapshot contractor) {
        if (!contractor.active()) {
            throw new ContractorAccessException(
                    ContractorAccessException.Code.CONTRACTOR_INACTIVE, "Contractor is inactive.");
        }
    }

    private static void requirePositive(long contractorId) {
        if (contractorId <= 0) {
            throw new IllegalArgumentException("contractorId must be positive.");
        }
    }
}
