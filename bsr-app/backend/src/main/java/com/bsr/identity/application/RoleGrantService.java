package com.bsr.identity.application;

import com.bsr.audit.api.AuditAppender;
import com.bsr.identity.api.ActionPermission;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.Authorization;
import com.bsr.identity.api.IdentityType;
import com.bsr.identity.api.RoleCode;
import com.bsr.identity.infrastructure.IdentityStatusReader;
import com.bsr.identity.infrastructure.persistence.AccountRepository;
import com.bsr.identity.infrastructure.persistence.AccountRow;
import com.bsr.identity.infrastructure.persistence.RoleGrantRepository;
import com.bsr.identity.infrastructure.persistence.RoleGrantRow;
import com.bsr.platform.error.ApiException;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleGrantService {

    private final Authorization authorization;
    private final AccountRepository accountRepository;
    private final RoleGrantRepository roleGrantRepository;
    private final IdentityStatusReader identityStatusReader;
    private final AuditAppender auditAppender;
    private final Clock clock;

    public RoleGrantService(
            Authorization authorization,
            AccountRepository accountRepository,
            RoleGrantRepository roleGrantRepository,
            IdentityStatusReader identityStatusReader,
            AuditAppender auditAppender,
            Clock clock) {
        this.authorization = authorization;
        this.accountRepository = accountRepository;
        this.roleGrantRepository = roleGrantRepository;
        this.identityStatusReader = identityStatusReader;
        this.auditAppender = auditAppender;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RoleGrantView grant(
            ActorContext actor,
            long accountId,
            RoleCode role,
            Long storeId,
            LocalDateTime validFrom,
            LocalDateTime validTo) {
        authorization.require(actor, ActionPermission.MANAGER_ROLE_GRANT, null);
        validateRange(validFrom, validTo);

        AccountRow account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> notFound("ACCOUNT_NOT_FOUND", "The target account does not exist."));
        BoundIdentity identity = boundIdentity(account);
        if (!identityStatusReader.isActive(identity.type(), identity.id())) {
            throw conflict("IDENTITY_INACTIVE", "The target business identity is inactive.");
        }
        validateRoleAndScope(actor, identity.type(), role, storeId);

        boolean overlaps = roleGrantRepository.findByAccountIdOrderByRoleGrantId(accountId).stream()
                .filter(existing -> existing.roleCode().equals(role.name()))
                .filter(existing -> java.util.Objects.equals(existing.storeId(), storeId))
                .anyMatch(existing -> overlaps(existing.validFrom(), existing.validTo(), validFrom, validTo));
        if (overlaps) {
            throw conflict("ROLE_GRANT_OVERLAP", "The requested role grant overlaps an existing grant.");
        }

        RoleGrantRow saved = roleGrantRepository.saveAndFlush(
                new RoleGrantRow(accountId, role.name(), storeId, validFrom, validTo));
        auditAppender.appendSuccess(
                actor,
                "ROLE_GRANT",
                saved.roleGrantId(),
                "ROLE_GRANT_CREATED",
                summary(role, storeId));
        return RoleGrantView.from(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RoleGrantView revoke(ActorContext actor, long roleGrantId) {
        authorization.require(actor, ActionPermission.MANAGER_ROLE_GRANT, null);
        RoleGrantRow snapshot = roleGrantRepository.findById(roleGrantId)
                .orElseThrow(() -> notFound("ROLE_GRANT_NOT_FOUND", "The role grant does not exist."));
        accountRepository.findByIdForUpdate(snapshot.accountId())
                .orElseThrow(() -> notFound("ACCOUNT_NOT_FOUND", "The target account does not exist."));
        RoleGrantRow grant = roleGrantRepository.findByIdForUpdate(roleGrantId)
                .orElseThrow(() -> notFound("ROLE_GRANT_NOT_FOUND", "The role grant does not exist."));
        if (grant.storeId() != null) {
            authorization.requireRoleAtStore(actor, RoleCode.MANAGER, grant.storeId());
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (grant.validFrom().isAfter(now) || !now.isBefore(nullSafeEnd(grant.validTo()))) {
            throw conflict("ROLE_GRANT_NOT_ACTIVE", "Only a currently active role grant can be revoked.");
        }
        grant.revokeAt(now);
        roleGrantRepository.flush();
        auditAppender.appendSuccess(
                actor,
                "ROLE_GRANT",
                grant.roleGrantId(),
                "ROLE_GRANT_REVOKED",
                summary(RoleCode.valueOf(grant.roleCode()), grant.storeId()));
        return RoleGrantView.from(grant);
    }

    private void validateRoleAndScope(
            ActorContext actor,
            IdentityType identityType,
            RoleCode role,
            Long storeId) {
        boolean compatible = switch (identityType) {
            case CUSTOMER -> role == RoleCode.CUSTOMER && storeId == null;
            case CONTRACTOR -> role == RoleCode.CONTRACTOR && storeId == null;
            case EMPLOYEE -> (role == RoleCode.EMPLOYEE || role == RoleCode.MANAGER) && storeId != null;
        };
        if (!compatible) {
            throw conflict("ROLE_IDENTITY_MISMATCH", "The role and store scope are incompatible with the target identity.");
        }
        if (storeId != null) {
            authorization.requireRoleAtStore(actor, RoleCode.MANAGER, storeId);
            if (!identityStatusReader.isActiveStore(storeId)) {
                throw conflict("STORE_INACTIVE", "The target store is not active.");
            }
        }
    }

    private BoundIdentity boundIdentity(AccountRow account) {
        int count = (account.customerId() == null ? 0 : 1)
                + (account.employeeId() == null ? 0 : 1)
                + (account.contractorId() == null ? 0 : 1);
        if (count != 1) {
            throw conflict("ACCOUNT_IDENTITY_INVALID", "The target account does not have exactly one business identity.");
        }
        if (account.customerId() != null) {
            return new BoundIdentity(IdentityType.CUSTOMER, account.customerId());
        }
        if (account.employeeId() != null) {
            return new BoundIdentity(IdentityType.EMPLOYEE, account.employeeId());
        }
        return new BoundIdentity(IdentityType.CONTRACTOR, account.contractorId());
    }

    private void validateRange(LocalDateTime validFrom, LocalDateTime validTo) {
        if (validFrom == null || validTo != null && !validTo.isAfter(validFrom)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ROLE_GRANT_RANGE_INVALID", "validTo must be after validFrom.");
        }
    }

    private boolean overlaps(
            LocalDateTime existingFrom,
            LocalDateTime existingTo,
            LocalDateTime requestedFrom,
            LocalDateTime requestedTo) {
        return existingFrom.isBefore(nullSafeEnd(requestedTo))
                && requestedFrom.isBefore(nullSafeEnd(existingTo));
    }

    private LocalDateTime nullSafeEnd(LocalDateTime value) {
        return value == null ? LocalDateTime.MAX : value;
    }

    private String summary(RoleCode role, Long storeId) {
        return "role=" + role.name() + (storeId == null ? "" : ";storeId=" + storeId);
    }

    private ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private record BoundIdentity(IdentityType type, long id) {
    }

    public record RoleGrantView(
            String roleGrantId,
            String accountId,
            String role,
            String storeId,
            LocalDateTime validFrom,
            LocalDateTime validTo) {

        static RoleGrantView from(RoleGrantRow row) {
            return new RoleGrantView(
                    Long.toString(row.roleGrantId()),
                    Long.toString(row.accountId()),
                    row.roleCode(),
                    row.storeId() == null ? null : Long.toString(row.storeId()),
                    row.validFrom(),
                    row.validTo());
        }
    }
}
