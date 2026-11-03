package com.bsr.audit.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bsr.identity.api.ActiveRoleGrant;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.IdentityType;
import com.bsr.identity.api.RoleCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class TransactionalAuditAppenderTest {

    @Test
    @DisplayName("AC-BR40 success audit refuses to run outside a business transaction")
    void AC_BR40_successAuditRequiresExistingTransaction() throws NoSuchMethodException {
        Transactional annotation = TransactionalAuditAppender.class
                .getMethod(
                        "appendSuccess",
                        ActorContext.class,
                        String.class,
                        long.class,
                        String.class,
                        String.class)
                .getAnnotation(Transactional.class);

        assertEquals(Propagation.MANDATORY, annotation.propagation());
    }

    @Test
    @DisplayName("AC-BR40 audit technical codes reject free-form values")
    void AC_BR40_auditCodesAreBoundedTechnicalValues() {
        TransactionalAuditAppender target = new TransactionalAuditAppender(
                null,
                Clock.fixed(Instant.parse("2026-09-29T00:00:00Z"), ZoneOffset.UTC));

        assertThrows(IllegalArgumentException.class, () -> target.appendSuccess(
                actor(), "not a code", 1L, "LOGIN", null));
        assertThrows(IllegalArgumentException.class, () -> target.appendSuccess(
                actor(), "ACCOUNT", 1L, "LOGIN", "session=secret"));
        assertThrows(IllegalArgumentException.class, () -> target.appendSuccess(
                actor(), "ACCOUNT", 1L, "LOGIN", "csrfToken=masked"));
    }

    private ActorContext actor() {
        return new ActorContext(
                1L,
                "manager",
                IdentityType.EMPLOYEE,
                2L,
                List.of(new ActiveRoleGrant(RoleCode.MANAGER, 3L)));
    }
}
