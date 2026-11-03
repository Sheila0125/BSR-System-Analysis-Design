package com.bsr.repair.infrastructure.persistence;

import com.bsr.repair.api.RepairStatusProjection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcRepairStatusProjection implements RepairStatusProjection {
    private final JdbcTemplate jdbc;

    public JdbcRepairStatusProjection(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> countsAtStore(long storeId) {
        return jdbc.query("SELECT task_status, COUNT(*) FROM repair_task "
                        + "WHERE service_store_id = ? GROUP BY task_status",
                (rs, row) -> Map.entry(rs.getString(1), rs.getLong(2)), storeId)
                .stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
