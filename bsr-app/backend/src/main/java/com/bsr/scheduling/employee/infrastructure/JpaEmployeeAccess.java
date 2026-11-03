package com.bsr.scheduling.employee.infrastructure;

import com.bsr.scheduling.employee.api.EmployeeAccess;
import com.bsr.scheduling.employee.api.EmployeeAccessException;
import com.bsr.scheduling.employee.api.EmployeeSnapshot;
import com.bsr.scheduling.employee.domain.Employee;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaEmployeeAccess implements EmployeeAccess {

    private static final String SELECT = """
            SELECT employee_id, employee_number, full_name, email, phone, business_status
            FROM employee
            WHERE employee_id = :employeeId
            """;

    private final EntityManager entityManager;

    public JpaEmployeeAccess(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmployeeSnapshot> findById(long employeeId) {
        requirePositive(employeeId);
        return query(employeeId, "");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Employee lockForUse(long employeeId) {
        Employee employee = required(employeeId, " FOR SHARE");
        employee.requireActive();
        return employee;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Employee lockForScheduling(long employeeId) {
        Employee employee = required(employeeId, " FOR UPDATE");
        employee.requireActive();
        return employee;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Employee lockForDeactivation(long employeeId) {
        return required(employeeId, " FOR UPDATE");
    }

    private Employee required(long employeeId, String lockClause) {
        requirePositive(employeeId);
        return query(employeeId, lockClause)
                .map(Employee::new)
                .orElseThrow(() -> new EmployeeAccessException(
                        EmployeeAccessException.Code.EMPLOYEE_NOT_FOUND, "Employee does not exist."));
    }

    private Optional<EmployeeSnapshot> query(long employeeId, String lockClause) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(SELECT + lockClause)
                .setParameter("employeeId", employeeId)
                .getResultList();
        return rows.stream().findFirst().map(JpaEmployeeAccess::snapshot);
    }

    private static EmployeeSnapshot snapshot(Object[] row) {
        return new EmployeeSnapshot(
                ((Number) row[0]).longValue(),
                (String) row[1],
                (String) row[2],
                (String) row[3],
                (String) row[4],
                "ACTIVE".equals(row[5]));
    }

    private static void requirePositive(long employeeId) {
        if (employeeId <= 0) {
            throw new IllegalArgumentException("employeeId must be positive.");
        }
    }
}
