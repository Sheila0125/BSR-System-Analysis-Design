package com.bsr.rental.infrastructure.persistence;

import com.bsr.rental.api.RentalStatusProjection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcRentalStatusProjection implements RentalStatusProjection {
    private final JdbcTemplate jdbc;

    public JdbcRentalStatusProjection(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> countsAtStore(long storeId) {
        return jdbc.query("SELECT contract_status, COUNT(*) FROM rental_contract "
                        + "WHERE service_store_id = ? GROUP BY contract_status",
                (rs, row) -> Map.entry(rs.getString(1), rs.getLong(2)), storeId)
                .stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
