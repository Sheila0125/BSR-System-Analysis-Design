package com.bsr.identity.web;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.endsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bsr.schema.IsolatedTestDatabaseInitializer;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
    "spring.profiles.active=test",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = IsolatedTestDatabaseInitializer.class)
@Transactional
class RoleGrantSecurityIT {

    private static final long STORE_ID = 22001L;
    private static final long OTHER_STORE_ID = 22002L;
    private static final long MANAGER_EMPLOYEE_ID = 22003L;
    private static final long TARGET_EMPLOYEE_ID = 22004L;
    private static final long MANAGER_ACCOUNT_ID = 22005L;
    private static final long TARGET_ACCOUNT_ID = 22006L;
    private static final long MANAGER_GRANT_ID = 22007L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void insertAccounts() {
        insertStore(STORE_ID, "S-RG-01");
        insertStore(OTHER_STORE_ID, "S-RG-02");
        insertEmployee(MANAGER_EMPLOYEE_ID, "E-RG-01", "manager-role@test.invalid");
        insertEmployee(TARGET_EMPLOYEE_ID, "E-RG-02", "target-role@test.invalid");
        insertAccount(MANAGER_ACCOUNT_ID, "role-manager", MANAGER_EMPLOYEE_ID);
        insertAccount(TARGET_ACCOUNT_ID, "role-target", TARGET_EMPLOYEE_ID);
        jdbcTemplate.update(
                """
                INSERT INTO role_grant (
                    role_grant_id, account_id, role_code, store_id, valid_from, valid_to
                ) VALUES (?, ?, 'MANAGER', ?, '2020-01-01 00:00:00', NULL)
                """,
                MANAGER_GRANT_ID,
                MANAGER_ACCOUNT_ID,
                STORE_ID);
    }

    @Test
    @DisplayName("AC-BR02 manager creates and revokes a store-scoped grant with same-transaction audit")
    void AC_BR02_managerCreatesAndRevokesGrantWithAudit() throws Exception {
        AuthSession auth = login();
        MvcResult created = mockMvc.perform(post("/api/role-grants")
                        .session(auth.session())
                        .cookie(auth.cookie())
                        .header("X-XSRF-TOKEN", auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantJson(STORE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(Long.toString(TARGET_ACCOUNT_ID)))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.storeId").value(Long.toString(STORE_ID)))
                .andReturn();

        String grantId = objectMapper.readTree(created.getResponse().getContentAsByteArray())
                .get("roleGrantId")
                .asText();
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE object_type = 'ROLE_GRANT' AND object_id = ? AND action_code = 'ROLE_GRANT_CREATED'",
                Integer.class,
                Long.parseLong(grantId)));

        mockMvc.perform(get("/api/audit-events")
                        .session(auth.session())
                        .param("objectType", "ROLE_GRANT")
                        .param("objectId", grantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].auditEventId", not(blankOrNullString())))
                .andExpect(jsonPath("$[0].objectId").value(grantId))
                .andExpect(jsonPath("$[0].actionCode").value("ROLE_GRANT_CREATED"))
                .andExpect(jsonPath("$[0].occurredAt", endsWith("Z")));

        AuthSession freshCsrf = csrf(auth.session());
        mockMvc.perform(post("/api/role-grants/{id}/revoke", grantId)
                        .session(auth.session())
                        .cookie(freshCsrf.cookie())
                        .header("X-XSRF-TOKEN", freshCsrf.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validTo", not(blankOrNullString())));

        LocalDateTime validTo = jdbcTemplate.queryForObject(
                "SELECT valid_to FROM role_grant WHERE role_grant_id = ?",
                LocalDateTime.class,
                Long.parseLong(grantId));
        assertNotNull(validTo);
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE object_type = 'ROLE_GRANT' AND object_id = ? AND action_code = 'ROLE_GRANT_REVOKED'",
                Integer.class,
                Long.parseLong(grantId)));
    }

    @Test
    @DisplayName("AC-BR02 manager cannot grant a role at a store outside explicit scope")
    void AC_BR02_managerCannotGrantAtAnotherStore() throws Exception {
        AuthSession auth = login();

        mockMvc.perform(post("/api/role-grants")
                        .session(auth.session())
                        .cookie(auth.cookie())
                        .header("X-XSRF-TOKEN", auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantJson(OTHER_STORE_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STORE_SCOPE_DENIED"));

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM role_grant WHERE account_id = ?",
                Integer.class,
                TARGET_ACCOUNT_ID));
    }

    @Test
    @DisplayName("AC-BR02 role changes never change the target account identity type")
    void AC_BR02_roleMustMatchBoundIdentity() throws Exception {
        AuthSession auth = login();
        String body = """
                {"accountId":"%d","role":"CUSTOMER","validFrom":"2026-01-01T00:00:00"}
                """.formatted(TARGET_ACCOUNT_ID);

        mockMvc.perform(post("/api/role-grants")
                        .session(auth.session())
                        .cookie(auth.cookie())
                        .header("X-XSRF-TOKEN", auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_IDENTITY_MISMATCH"));

        assertEquals(TARGET_EMPLOYEE_ID, jdbcTemplate.queryForObject(
                "SELECT employee_id FROM account WHERE account_id = ?",
                Long.class,
                TARGET_ACCOUNT_ID));
    }

    @Test
    @DisplayName("AC-BR03 audit query rejects an object outside the manager store scope")
    void AC_BR03_auditQueryEnforcesObjectStoreScope() throws Exception {
        long otherGrantId = 22008L;
        jdbcTemplate.update(
                "INSERT INTO role_grant (role_grant_id, account_id, role_code, store_id, valid_from, valid_to) VALUES (?, ?, 'EMPLOYEE', ?, '2020-01-01 00:00:00', NULL)",
                otherGrantId,
                TARGET_ACCOUNT_ID,
                OTHER_STORE_ID);
        jdbcTemplate.update(
                "INSERT INTO audit_event (actor_account_id, object_type, object_id, action_code, occurred_at, result_code, detail_summary) VALUES (?, 'ROLE_GRANT', ?, 'ROLE_GRANT_CREATED', '2026-01-01 00:00:00', 'SUCCESS', 'role=EMPLOYEE')",
                MANAGER_ACCOUNT_ID,
                otherGrantId);
        AuthSession auth = login();

        mockMvc.perform(get("/api/audit-events")
                        .session(auth.session())
                        .param("objectType", "ROLE_GRANT")
                        .param("objectId", Long.toString(otherGrantId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STORE_SCOPE_DENIED"));
    }

    @Test
    @DisplayName("AC-UC00 path and query validation retain the common ProblemDetail protocol")
    void AC_UC00_securityEndpointsUseCommonValidationProblems() throws Exception {
        AuthSession auth = login();

        mockMvc.perform(get("/api/audit-events")
                        .session(auth.session())
                        .param("objectType", "role grant")
                        .param("objectId", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        AuthSession freshCsrf = csrf(auth.session());
        mockMvc.perform(post("/api/role-grants/not-an-id/revoke")
                        .session(auth.session())
                        .cookie(freshCsrf.cookie())
                        .header("X-XSRF-TOKEN", freshCsrf.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private AuthSession login() throws Exception {
        MockHttpSession session = new MockHttpSession();
        AuthSession csrf = csrf(session);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginName":"role-manager","password":"manager test password"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return new AuthSession(
                (MockHttpSession) result.getRequest().getSession(false),
                csrf.cookie(),
                csrf.token());
    }

    private AuthSession csrf(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return new AuthSession(session, result.getResponse().getCookie("XSRF-TOKEN"), body.get("token").asText());
    }

    private String grantJson(long storeId) {
        return """
                {"accountId":"%d","role":"EMPLOYEE","storeId":"%d","validFrom":"2026-01-01T00:00:00"}
                """.formatted(TARGET_ACCOUNT_ID, storeId);
    }

    private void insertStore(long id, String number) {
        jdbcTemplate.update(
                "INSERT INTO store (store_id, store_number, store_name, address, business_status, opening_time, closing_time) VALUES (?, ?, ?, NULL, 'ACTIVE', '08:00:00', '22:00:00')",
                id,
                number,
                number);
    }

    private void insertEmployee(long id, String number, String email) {
        jdbcTemplate.update(
                "INSERT INTO employee (employee_id, employee_number, full_name, email, phone, business_status) VALUES (?, ?, ?, ?, NULL, 'ACTIVE')",
                id,
                number,
                number,
                email);
    }

    private void insertAccount(long id, String loginName, long employeeId) {
        jdbcTemplate.update(
                "INSERT INTO account (account_id, login_name, password_hash, enabled, customer_id, employee_id, contractor_id) VALUES (?, ?, ?, TRUE, NULL, ?, NULL)",
                id,
                loginName,
                passwordEncoder.encode("manager test password"),
                employeeId);
    }

    private record AuthSession(MockHttpSession session, Cookie cookie, String token) {
    }
}
