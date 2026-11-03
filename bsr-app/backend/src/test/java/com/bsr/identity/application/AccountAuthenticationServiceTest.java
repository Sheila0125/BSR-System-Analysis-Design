package com.bsr.identity.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bsr.identity.api.RoleCode;
import com.bsr.identity.infrastructure.IdentityStatusReader;
import com.bsr.identity.infrastructure.persistence.AccountRepository;
import com.bsr.identity.infrastructure.persistence.AccountRow;
import com.bsr.identity.infrastructure.persistence.RoleGrantRepository;
import com.bsr.identity.infrastructure.persistence.RoleGrantRow;
import com.bsr.platform.error.ApiException;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountAuthenticationServiceTest {

    private static final long ACCOUNT_ID = 101L;
    private static final long EMPLOYEE_ID = 102L;
    private static final long STORE_ID = 103L;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Oslo");
    private static final Clock BOUNDARY_CLOCK = Clock.fixed(
            Instant.parse("2026-09-30T07:00:00Z"),
            BUSINESS_ZONE);
    private static final LocalDateTime BOUNDARY = LocalDateTime.of(2026, 9, 30, 9, 0);

    @Test
    @DisplayName("AC-BR02 role grant valid_from is inclusive")
    void AC_BR02_validFromBoundaryIsActive() {
        AccountAuthenticationService service = serviceWithGrant(
                new RoleGrantRow(
                        ACCOUNT_ID,
                        RoleCode.EMPLOYEE.name(),
                        STORE_ID,
                        BOUNDARY,
                        BOUNDARY.plusHours(1)));

        var actor = assertDoesNotThrow(() -> service.loadActor(ACCOUNT_ID));

        assertEquals(RoleCode.EMPLOYEE, actor.activeGrants().getFirst().role());
        assertEquals(STORE_ID, actor.activeGrants().getFirst().storeId());
    }

    @Test
    @DisplayName("AC-BR02 role grant valid_to is exclusive")
    void AC_BR02_validToBoundaryIsInactive() {
        AccountAuthenticationService service = serviceWithGrant(
                new RoleGrantRow(
                        ACCOUNT_ID,
                        RoleCode.EMPLOYEE.name(),
                        STORE_ID,
                        BOUNDARY.minusHours(1),
                        BOUNDARY));

        ApiException exception = assertThrows(ApiException.class, () -> service.loadActor(ACCOUNT_ID));

        assertEquals("SESSION_REVOKED", exception.code());
    }

    private AccountAuthenticationService serviceWithGrant(RoleGrantRow grant) {
        AccountRepository accounts = mock(AccountRepository.class);
        RoleGrantRepository grants = mock(RoleGrantRepository.class);
        IdentityStatusReader identities = mock(IdentityStatusReader.class);
        AccountRow account = mock(AccountRow.class);

        when(accounts.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(account.accountId()).thenReturn(ACCOUNT_ID);
        when(account.loginName()).thenReturn("boundary-employee");
        when(account.enabled()).thenReturn(true);
        when(account.customerId()).thenReturn(null);
        when(account.employeeId()).thenReturn(EMPLOYEE_ID);
        when(account.contractorId()).thenReturn(null);
        when(identities.isActive(com.bsr.identity.api.IdentityType.EMPLOYEE, EMPLOYEE_ID)).thenReturn(true);
        when(grants.findByAccountIdOrderByRoleGrantId(ACCOUNT_ID)).thenReturn(List.of(grant));

        return new AccountAuthenticationService(
                accounts,
                grants,
                identities,
                mock(EntityManager.class),
                BOUNDARY_CLOCK);
    }
}
