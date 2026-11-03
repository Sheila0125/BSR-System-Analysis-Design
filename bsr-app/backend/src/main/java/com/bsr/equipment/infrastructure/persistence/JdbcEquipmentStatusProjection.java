package com.bsr.equipment.infrastructure.persistence;

import com.bsr.equipment.api.EquipmentStatusProjection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcEquipmentStatusProjection implements EquipmentStatusProjection {
    private final JdbcTemplate jdbc;

    public JdbcEquipmentStatusProjection(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> countsAtStore(long storeId) {
        return jdbc.query("SELECT equipment_status, COUNT(*) FROM equipment "
                        + "WHERE current_store_id = ? GROUP BY equipment_status",
                (rs, row) -> Map.entry(rs.getString(1), rs.getLong(2)), storeId)
                .stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
