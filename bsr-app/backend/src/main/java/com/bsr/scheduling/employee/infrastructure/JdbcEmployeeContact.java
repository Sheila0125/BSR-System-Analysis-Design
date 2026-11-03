package com.bsr.scheduling.employee.infrastructure;

import com.bsr.scheduling.employee.application.EmployeeContactPersistence;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEmployeeContact implements EmployeeContactPersistence {
    private final JdbcTemplate jdbc;
    public JdbcEmployeeContact(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Optional<Contact> findAtStore(long employeeId, long storeId, LocalDateTime now) {
        return jdbc.query("""
                SELECT e.* FROM employee e WHERE e.employee_id=? AND EXISTS (
                    SELECT 1 FROM account a JOIN role_grant g ON g.account_id=a.account_id
                    WHERE a.employee_id=e.employee_id AND a.enabled=TRUE AND g.store_id=?
                      AND g.role_code IN ('EMPLOYEE','MANAGER') AND g.valid_from<=?
                      AND (g.valid_to IS NULL OR g.valid_to>?)
                )
                """, (r,i) -> new Contact(r.getLong("employee_id"),r.getString("employee_number"),
                r.getString("full_name"),r.getString("email"),r.getString("phone"),
                r.getString("address"),r.getString("note")),employeeId,storeId,now,now).stream().findFirst();
    }

    @Override public void updateSupplement(long employeeId, String address, String note) {
        jdbc.update("UPDATE employee SET address=?,note=? WHERE employee_id=?",address,note,employeeId);
    }

    @Override public List<Choice> choicesAtStore(long storeId, LocalDateTime now) {
        return jdbc.query("""
                SELECT e.employee_id,e.employee_number,e.full_name FROM employee e
                WHERE e.business_status='ACTIVE' AND EXISTS (
                    SELECT 1 FROM account a JOIN role_grant g ON g.account_id=a.account_id
                    WHERE a.employee_id=e.employee_id AND a.enabled=TRUE AND g.store_id=?
                      AND g.role_code IN ('EMPLOYEE','MANAGER') AND g.valid_from<=?
                      AND (g.valid_to IS NULL OR g.valid_to>?)
                ) ORDER BY e.employee_number,e.employee_id
                """, (r,i) -> new Choice(r.getLong("employee_id"),r.getString("employee_number"),r.getString("full_name")),storeId,now,now);
    }
}
