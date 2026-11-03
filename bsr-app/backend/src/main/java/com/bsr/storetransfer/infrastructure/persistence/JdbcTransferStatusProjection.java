package com.bsr.storetransfer.infrastructure.persistence;

import com.bsr.storetransfer.transfer.api.TransferStatusProjection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcTransferStatusProjection implements TransferStatusProjection {
    private final JdbcTemplate jdbc;

    public JdbcTransferStatusProjection(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> countsAtStore(long storeId) {
        return jdbc.query("SELECT transfer_status, COUNT(*) FROM equipment_transfer "
                        + "WHERE source_store_id = ? OR destination_store_id = ? GROUP BY transfer_status",
                (rs, row) -> Map.entry(rs.getString(1), rs.getLong(2)), storeId, storeId)
                .stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
