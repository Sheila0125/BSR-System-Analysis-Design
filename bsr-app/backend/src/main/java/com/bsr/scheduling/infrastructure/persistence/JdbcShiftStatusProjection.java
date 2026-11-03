package com.bsr.scheduling.infrastructure.persistence;

import com.bsr.scheduling.shift.api.ShiftStatusProjection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcShiftStatusProjection implements ShiftStatusProjection {
    private final JdbcTemplate jdbc;

    public JdbcShiftStatusProjection(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> countsAtStore(long storeId) {
        return jdbc.query("SELECT shift_status, COUNT(*) FROM employee_shift "
                        + "WHERE store_id = ? GROUP BY shift_status",
                (rs, row) -> Map.entry(rs.getString(1), rs.getLong(2)), storeId)
                .stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
