package com.bsr.identity.web;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bsr.schema.IsolatedTestDatabaseInitializer;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
    "spring.profiles.active=test",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = IsolatedTestDatabaseInitializer.class)
@Transactional
class AuthSecurityIT {

    private static final long CUSTOMER_ID = 21001L;
    private static final long ACCOUNT_ID = 21002L;
    private static final long GRANT_ID = 21003L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void insertAccount() {
        jdbcTemplate.update(
                """
                INSERT INTO customer (
                    customer_id, customer_number, full_name, email, phone,
                    height_estimated, weight_estimated, boot_size_estimated
                ) VALUES (?, ?, ?, ?, ?, FALSE, FALSE, FALSE)
                """,
                CUSTOMER_ID,
                "C-AUTH-21001",
                "Auth Test Customer",
                "auth-customer@test.invalid",
                "+47000021001");
        jdbcTemplate.update(
                """
                INSERT INTO account (
                    account_id, login_name, password_hash, enabled,
                    customer_id, employee_id, contractor_id
                ) VALUES (?, ?, ?, TRUE, ?, NULL, NULL)
                """,
                ACCOUNT_ID,
                "auth-customer",
                passwordEncoder.encode("correct horse battery staple"),
                CUSTOMER_ID);
        jdbcTemplate.update(
                """
                INSERT INTO role_grant (
                    role_grant_id, account_id, role_code, store_id, valid_from, valid_to
                ) VALUES (?, ?, 'CUSTOMER', NULL, '2020-01-01 00:00:00', NULL)
                """,
                GRANT_ID,
                ACCOUNT_ID);
    }

    @Test
    @DisplayName("AC-UC00 CSRF is issued and missing CSRF rejects login")
    void AC_UC00_csrfIsRequiredForLogin() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", true))
                .andExpect(jsonPath("$.headerName", not(blankOrNullString())))
                .andExpect(jsonPath("$.token", not(blankOrNullString())));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("correct horse battery staple")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("AC-UC00 login rotates session and returns string identity IDs")
    void AC_UC00_loginRotatesSessionAndBuildsActorContext() throws Exception {
        MockHttpSession preLogin = new MockHttpSession();
        String priorSessionId = preLogin.getId();
        CsrfSession csrfSession = csrfSession(preLogin);

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .session(preLogin)
                        .cookie(csrfSession.cookie())
                        .header("X-XSRF-TOKEN", csrfSession.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("correct horse battery staple")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(Long.toString(ACCOUNT_ID)))
                .andExpect(jsonPath("$.identityType").value("CUSTOMER"))
                .andExpect(jsonPath("$.identityId").value(Long.toString(CUSTOMER_ID)))
                .andExpect(jsonPath("$.grants[0].role").value("CUSTOMER"))
                .andReturn();

        MockHttpSession authenticated = (MockHttpSession) login.getRequest().getSession(false);
        assertNotEquals(priorSessionId, authenticated.getId());

        mockMvc.perform(get("/api/auth/me").session(authenticated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(Long.toString(ACCOUNT_ID)));
    }

    @Test
    @DisplayName("AC-BR01 disabled account invalidates the next authenticated request")
    void AC_BR01_disabledAccountInvalidatesNextRequest() throws Exception {
        MockHttpSession session = login();
        jdbcTemplate.update("UPDATE account SET enabled = FALSE WHERE account_id = ?", ACCOUNT_ID);

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    @DisplayName("AC-BR02 revoking the final grant invalidates the next request")
    void AC_BR02_revokedGrantInvalidatesNextRequest() throws Exception {
        MockHttpSession session = login();
        jdbcTemplate.update(
                "UPDATE role_grant SET valid_to = '2021-01-01 00:00:00' WHERE role_grant_id = ?",
                GRANT_ID);

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
    }

    @Test
    @DisplayName("AC-UC00 bad credentials do not disclose account existence")
    void AC_UC00_badCredentialsUseGenericFailure() throws Exception {
        CsrfSession csrfSession = csrfSession(new MockHttpSession());
        mockMvc.perform(post("/api/auth/login")
                        .session(csrfSession.session())
                        .cookie(csrfSession.cookie())
                        .header("X-XSRF-TOKEN", csrfSession.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("wrong password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    private MockHttpSession login() throws Exception {
        CsrfSession csrfSession = csrfSession(new MockHttpSession());
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .session(csrfSession.session())
                        .cookie(csrfSession.cookie())
                        .header("X-XSRF-TOKEN", csrfSession.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("correct horse battery staple")))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private CsrfSession csrfSession(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn();
        String token = objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .get("token")
                .asText();
        return new CsrfSession(session, result.getResponse().getCookie("XSRF-TOKEN"), token);
    }

    private String loginJson(String password) {
        return """
                {"loginName":"auth-customer","password":"%s"}
                """.formatted(password);
    }

    private record CsrfSession(MockHttpSession session, Cookie cookie, String token) {
    }
}
