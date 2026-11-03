package com.bsr.identity.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity(name = "RoleGrantRow")
@Table(name = "role_grant")
public class RoleGrantRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_grant_id", nullable = false)
    Long roleGrantId;

    @Column(name = "account_id", nullable = false)
    Long accountId;

    @Column(name = "role_code", nullable = false, length = 30)
    String roleCode;

    @Column(name = "store_id", nullable = true)
    Long storeId;

    @Column(name = "valid_from", nullable = false)
    LocalDateTime validFrom;

    @Column(name = "valid_to", nullable = true)
    LocalDateTime validTo;

    protected RoleGrantRow() {
    }

    public RoleGrantRow(
            long accountId,
            String roleCode,
            Long storeId,
            LocalDateTime validFrom,
            LocalDateTime validTo) {
        this.accountId = accountId;
        this.roleCode = roleCode;
        this.storeId = storeId;
        this.validFrom = validFrom;
        this.validTo = validTo;
    }

    public void revokeAt(LocalDateTime revokedAt) {
        if (!revokedAt.isAfter(validFrom)) {
            throw new IllegalArgumentException("revokedAt must be after validFrom.");
        }
        this.validTo = revokedAt;
    }

    public Long roleGrantId() {
        return roleGrantId;
    }

    public Long accountId() {
        return accountId;
    }

    public String roleCode() {
        return roleCode;
    }

    public Long storeId() {
        return storeId;
    }

    public LocalDateTime validFrom() {
        return validFrom;
    }

    public LocalDateTime validTo() {
        return validTo;
    }
}
