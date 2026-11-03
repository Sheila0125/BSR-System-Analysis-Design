package com.bsr.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

import com.bsr.audit.api.AuditAppender;
import com.bsr.identity.api.ActiveRoleGrant;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.IdentityType;
import com.bsr.identity.api.RoleCode;
import com.bsr.schema.IsolatedTestDatabaseInitializer;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
    "spring.profiles.active=test",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@ContextConfiguration(initializers = IsolatedTestDatabaseInitializer.class)
class RoleGrantRollbackIT {

    private static final long STORE_ID = 23001L;
    private static final long EMPLOYEE_ID = 23002L;
    private static final long ACCOUNT_ID = 23003L;

    @Autowired
    private RoleGrantService roleGrantService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private AuditAppender auditAppender;

    @BeforeEach
    void insertTarget() {
        cleanup();
        jdbcTemplate.update(
                "INSERT INTO store (store_id, store_number, store_name, address, business_status, opening_time, closing_time) VALUES (?, 'S-RB-01', 'Rollback Store', NULL, 'ACTIVE', '08:00:00', '22:00:00')",
                STORE_ID);
        jdbcTemplate.update(
                "INSERT INTO employee (employee_id, employee_number, full_name, email, phone, business_status) VALUES (?, 'E-RB-01', 'Rollback Employee', 'rollback-role@test.invalid', NULL, 'ACTIVE')",
                EMPLOYEE_ID);
        jdbcTemplate.update(
                "INSERT INTO account (account_id, login_name, password_hash, enabled, customer_id, employee_id, contractor_id) VALUES (?, 'rollback-target', ?, TRUE, NULL, ?, NULL)",
                ACCOUNT_ID,
                passwordEncoder.encode("unused rollback target password"),
                EMPLOYEE_ID);
    }

    @AfterEach
    void removeTarget() {
        cleanup();
    }

    @Test
    @DisplayName("AC-BR40 audit failure rolls back the role grant business change")
    void AC_BR40_auditFailureRollsBackRoleGrant() {
        doThrow(new IllegalStateException("simulated audit persistence failure"))
                .when(auditAppender)
                .appendSuccess(any(), anyString(), anyLong(), anyString(), anyString());

        assertThrows(IllegalStateException.class, () -> roleGrantService.grant(
                manager(),
                ACCOUNT_ID,
                RoleCode.EMPLOYEE,
                STORE_ID,
                LocalDateTime.of(2026, 1, 1, 0, 0),
                null));

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM role_grant WHERE account_id = ?",
                Integer.class,
                ACCOUNT_ID));
    }

    private ActorContext manager() {
        return new ActorContext(
                999L,
                "rollback-manager",
                IdentityType.EMPLOYEE,
                998L,
                List.of(new ActiveRoleGrant(RoleCode.MANAGER, STORE_ID)));
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM role_grant WHERE account_id = ?", ACCOUNT_ID);
        jdbcTemplate.update("DELETE FROM account WHERE account_id = ?", ACCOUNT_ID);
        jdbcTemplate.update("DELETE FROM employee WHERE employee_id = ?", EMPLOYEE_ID);
        jdbcTemplate.update("DELETE FROM store WHERE store_id = ?", STORE_ID);
    }
}
