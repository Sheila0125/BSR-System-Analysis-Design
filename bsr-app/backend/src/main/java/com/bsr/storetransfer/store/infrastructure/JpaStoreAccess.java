package com.bsr.storetransfer.store.infrastructure;

import com.bsr.storetransfer.store.api.StoreAccess;
import com.bsr.storetransfer.store.api.StoreAccessException;
import com.bsr.storetransfer.store.api.StoreSnapshot;
import jakarta.persistence.EntityManager;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaStoreAccess implements StoreAccess {

    private static final String SELECT = """
            SELECT store_id, store_number, store_name, address, business_status,
                   TIME_FORMAT(opening_time, '%H:%i:%s'), TIME_FORMAT(closing_time, '%H:%i:%s')
            FROM store
            WHERE store_id = :storeId
            """;

    private final EntityManager entityManager;

    public JpaStoreAccess(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoreSnapshot> findById(long storeId) {
        requirePositive(storeId);
        return query(storeId, "");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public StoreSnapshot lockForUse(long storeId) {
        StoreSnapshot store = required(storeId, " FOR SHARE");
        if (!store.active()) {
            throw new StoreAccessException(StoreAccessException.Code.STORE_INACTIVE, "Store is inactive.");
        }
        return store;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public StoreSnapshot lockForDeactivation(long storeId) {
        return required(storeId, " FOR UPDATE");
    }

    private StoreSnapshot required(long storeId, String lockClause) {
        requirePositive(storeId);
        return query(storeId, lockClause).orElseThrow(() -> new StoreAccessException(
                StoreAccessException.Code.STORE_NOT_FOUND, "Store does not exist."));
    }

    private Optional<StoreSnapshot> query(long storeId, String lockClause) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(SELECT + lockClause)
                .setParameter("storeId", storeId)
                .getResultList();
        return rows.stream().findFirst().map(JpaStoreAccess::snapshot);
    }

    private static StoreSnapshot snapshot(Object[] row) {
        return new StoreSnapshot(
                ((Number) row[0]).longValue(),
                (String) row[1],
                (String) row[2],
                (String) row[3],
                "ACTIVE".equals(row[4]),
                LocalTime.parse((String) row[5]),
                LocalTime.parse((String) row[6]));
    }

    private static void requirePositive(long storeId) {
        if (storeId <= 0) {
            throw new IllegalArgumentException("storeId must be positive.");
        }
    }
}
