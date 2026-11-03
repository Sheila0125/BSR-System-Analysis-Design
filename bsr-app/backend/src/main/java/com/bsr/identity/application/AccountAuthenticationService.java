package com.bsr.identity.application;

import com.bsr.identity.api.AccountAuthentication;
import com.bsr.identity.api.AccountCredentials;
import com.bsr.identity.api.ActiveRoleGrant;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.IdentityType;
import com.bsr.identity.api.RoleCode;
import com.bsr.identity.infrastructure.IdentityStatusReader;
import com.bsr.identity.infrastructure.persistence.AccountRepository;
import com.bsr.identity.infrastructure.persistence.AccountRow;
import com.bsr.identity.infrastructure.persistence.RoleGrantRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.bsr.platform.error.ApiException;

@Service
public class AccountAuthenticationService implements AccountAuthentication {

    private final AccountRepository accountRepository;
    private final RoleGrantRepository roleGrantRepository;
    private final IdentityStatusReader identityStatusReader;
    private final EntityManager entityManager;
    private final Clock clock;

    public AccountAuthenticationService(
            AccountRepository accountRepository,
            RoleGrantRepository roleGrantRepository,
            IdentityStatusReader identityStatusReader,
            EntityManager entityManager,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.roleGrantRepository = roleGrantRepository;
        this.identityStatusReader = identityStatusReader;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public AccountCredentials loadByLoginName(String loginName) {
        AccountRow account = accountRepository.findByLoginNameIgnoreCase(loginName.strip())
                .orElseThrow(() -> unauthorized("BAD_CREDENTIALS", "The login name or password is incorrect."));
        return new AccountCredentials(
                account.accountId(), account.loginName(), account.passwordHash(), account.enabled());
    }

    @Override
    @Transactional(readOnly = true)
    public ActorContext loadActor(long accountId) {
        // Session authorization must observe account/grant revocation on the very next request.
        entityManager.clear();
        AccountRow account = accountRepository.findById(accountId)
                .orElseThrow(() -> unauthorized("SESSION_INVALID", "The authenticated account no longer exists."));
        if (!account.enabled()) {
            throw unauthorized("ACCOUNT_DISABLED", "The authenticated account is disabled.");
        }

        BoundIdentity identity = boundIdentity(account);
        if (!identityStatusReader.isActive(identity.type(), identity.id())) {
            throw unauthorized("IDENTITY_INACTIVE", "The authenticated business identity is inactive.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        List<ActiveRoleGrant> grants = roleGrantRepository.findByAccountIdOrderByRoleGrantId(accountId).stream()
                .filter(grant -> !grant.validFrom().isAfter(now))
                .filter(grant -> grant.validTo() == null || now.isBefore(grant.validTo()))
                .map(grant -> new ActiveRoleGrant(RoleCode.valueOf(grant.roleCode()), grant.storeId()))
                .peek(grant -> validateGrant(identity.type(), grant))
                .distinct()
                .toList();
        if (grants.isEmpty()) {
            throw unauthorized("SESSION_REVOKED", "The authenticated account has no active role grant.");
        }
        return new ActorContext(
                account.accountId(), account.loginName(), identity.type(), identity.id(), grants);
    }

    private BoundIdentity boundIdentity(AccountRow account) {
        int count = (account.customerId() == null ? 0 : 1)
                + (account.employeeId() == null ? 0 : 1)
                + (account.contractorId() == null ? 0 : 1);
        if (count != 1) {
            throw unauthorized("ACCOUNT_IDENTITY_INVALID", "The account does not have exactly one business identity.");
        }
        if (account.customerId() != null) {
            return new BoundIdentity(IdentityType.CUSTOMER, account.customerId());
        }
        if (account.employeeId() != null) {
            return new BoundIdentity(IdentityType.EMPLOYEE, account.employeeId());
        }
        return new BoundIdentity(IdentityType.CONTRACTOR, account.contractorId());
    }

    private void validateGrant(IdentityType identityType, ActiveRoleGrant grant) {
        boolean roleMatches = switch (identityType) {
            case CUSTOMER -> grant.role() == RoleCode.CUSTOMER && grant.storeId() == null;
            case EMPLOYEE -> (grant.role() == RoleCode.EMPLOYEE || grant.role() == RoleCode.MANAGER)
                    && grant.storeId() != null;
            case CONTRACTOR -> grant.role() == RoleCode.CONTRACTOR && grant.storeId() == null;
        };
        if (!roleMatches) {
            throw unauthorized("ROLE_IDENTITY_MISMATCH", "An active role grant is incompatible with the bound identity.");
        }
    }

    private ApiException unauthorized(String code, String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, message);
    }

    private record BoundIdentity(IdentityType type, long id) {
    }
}
