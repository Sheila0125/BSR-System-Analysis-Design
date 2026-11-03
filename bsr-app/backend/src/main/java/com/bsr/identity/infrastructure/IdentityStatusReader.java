package com.bsr.identity.infrastructure;

import com.bsr.identity.api.IdentityType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IdentityStatusReader {

    private final JdbcTemplate jdbcTemplate;

    public IdentityStatusReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isActive(IdentityType type, long identityId) {
        String sql = switch (type) {
            case CUSTOMER -> "SELECT COUNT(*) FROM customer WHERE customer_id = ?";
            case EMPLOYEE -> "SELECT COUNT(*) FROM employee WHERE employee_id = ? AND business_status = 'ACTIVE'";
            case CONTRACTOR -> "SELECT COUNT(*) FROM repair_contractor WHERE contractor_id = ? AND business_status = 'ACTIVE'";
        };
        Long count = jdbcTemplate.queryForObject(sql, Long.class, identityId);
        return count != null && count == 1L;
    }

    public boolean isActiveStore(long storeId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM store WHERE store_id = ? AND business_status = 'ACTIVE'",
                Long.class,
                storeId);
        return count != null && count == 1L;
    }
}
