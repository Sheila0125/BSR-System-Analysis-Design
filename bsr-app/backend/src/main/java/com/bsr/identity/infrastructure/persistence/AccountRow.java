package com.bsr.identity.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity(name = "AccountRow")
@Table(name = "account")
public class AccountRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id", nullable = false)
    Long accountId;

    @Column(name = "login_name", nullable = false, length = 100)
    String loginName;

    @Column(name = "password_hash", nullable = false, length = 255)
    String passwordHash;

    @Column(name = "enabled", nullable = false)
    Boolean enabled;

    @Column(name = "customer_id", nullable = true)
    Long customerId;

    @Column(name = "employee_id", nullable = true)
    Long employeeId;

    @Column(name = "contractor_id", nullable = true)
    Long contractorId;

    protected AccountRow() {
    }

    public Long accountId() {
        return accountId;
    }

    public String loginName() {
        return loginName;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public boolean enabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public Long customerId() {
        return customerId;
    }

    public Long employeeId() {
        return employeeId;
    }

    public Long contractorId() {
        return contractorId;
    }
}
