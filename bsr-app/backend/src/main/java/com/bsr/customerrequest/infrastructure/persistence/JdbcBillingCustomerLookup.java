package com.bsr.customerrequest.infrastructure.persistence;

import com.bsr.customerrequest.api.BillingCustomerLookup;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcBillingCustomerLookup implements BillingCustomerLookup {
    private final JdbcTemplate jdbc;
    public JdbcBillingCustomerLookup(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Override public Optional<Customer> find(long customerId) {
        return jdbc.query("SELECT customer_number, full_name FROM customer WHERE customer_id=?",
                (rs,row)->new Customer(rs.getString(1),rs.getString(2)),customerId).stream().findFirst();
    }
}
