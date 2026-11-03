package com.bsr.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bsr.identity.api.ActiveRoleGrant;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.IdentityType;
import com.bsr.identity.api.RoleCode;
import com.bsr.platform.error.ApiException;
import com.bsr.schema.IsolatedTestDatabaseInitializer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(properties = {
    "spring.profiles.active=test",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@ContextConfiguration(initializers = IsolatedTestDatabaseInitializer.class)
class RoleGrantConcurrencyIT {

    private static final long STORE_ID = 24001L;
    private static final long MANAGER_EMPLOYEE_ID = 24002L;
    private static final long TARGET_EMPLOYEE_ID = 24003L;
    private static final long MANAGER_ACCOUNT_ID = 24004L;
    private static final long TARGET_ACCOUNT_ID = 24005L;
    private static final long MANAGER_GRANT_ID = 24006L;
    private static final LocalDateTime VALID_FROM = LocalDateTime.of(2026, 1, 1, 0, 0);

    @Autowired
    private RoleGrantService roleGrantService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void insertActors() {
        cleanup();
        jdbcTemplate.update(
                "INSERT INTO store (store_id, store_number, store_name, address, business_status, opening_time, closing_time) VALUES (?, 'S-RGC-01', 'Role Grant Concurrency Store', NULL, 'ACTIVE', '08:00:00', '22:00:00')",
                STORE_ID);
        insertEmployee(MANAGER_EMPLOYEE_ID, "E-RGC-01", "role-grant-manager@test.invalid");
        insertEmployee(TARGET_EMPLOYEE_ID, "E-RGC-02", "role-grant-target@test.invalid");
        insertAccount(MANAGER_ACCOUNT_ID, "role-grant-concurrency-manager", MANAGER_EMPLOYEE_ID);
        insertAccount(TARGET_ACCOUNT_ID, "role-grant-concurrency-target", TARGET_EMPLOYEE_ID);
        jdbcTemplate.update(
                "INSERT INTO role_grant (role_grant_id, account_id, role_code, store_id, valid_from, valid_to) VALUES (?, ?, 'MANAGER', ?, '2020-01-01 00:00:00', NULL)",
                MANAGER_GRANT_ID,
                MANAGER_ACCOUNT_ID,
                STORE_ID);
    }

    @AfterEach
    void removeActors() {
        cleanup();
    }

    @Test
    @DisplayName("AC-BR02 concurrent overlapping grants serialize on the account parent row")
    void AC_BR02_concurrentOverlappingGrantsCommitExactlyOne() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Attempt> first = executor.submit(() -> attemptGrant(ready, start));
            Future<Attempt> second = executor.submit(() -> attemptGrant(ready, start));

            assertTrue(ready.await(10, TimeUnit.SECONDS), "Both database workers must reach the start barrier.");
            start.countDown();
            Attempt firstResult = first.get(30, TimeUnit.SECONDS);
            Attempt secondResult = second.get(30, TimeUnit.SECONDS);

            assertEquals(1, List.of(firstResult, secondResult).stream().filter(Attempt::success).count());
            assertEquals(1, List.of(firstResult, secondResult).stream()
                    .filter(result -> "ROLE_GRANT_OVERLAP".equals(result.errorCode()))
                    .count());
        }

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM role_grant WHERE account_id = ? AND role_code = 'EMPLOYEE' AND store_id = ?",
                Integer.class,
                TARGET_ACCOUNT_ID,
                STORE_ID));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE actor_account_id = ? AND object_type = 'ROLE_GRANT' AND action_code = 'ROLE_GRANT_CREATED'",
                Integer.class,
                MANAGER_ACCOUNT_ID));
    }

    private Attempt attemptGrant(CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS), "The concurrency start signal must be released.");
        try {
            roleGrantService.grant(
                    manager(),
                    TARGET_ACCOUNT_ID,
                    RoleCode.EMPLOYEE,
                    STORE_ID,
                    VALID_FROM,
                    null);
            return new Attempt(true, null);
        } catch (ApiException exception) {
            return new Attempt(false, exception.code());
        }
    }

    private ActorContext manager() {
        return new ActorContext(
                MANAGER_ACCOUNT_ID,
                "role-grant-concurrency-manager",
                IdentityType.EMPLOYEE,
                MANAGER_EMPLOYEE_ID,
                List.of(new ActiveRoleGrant(RoleCode.MANAGER, STORE_ID)));
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
                passwordEncoder.encode("unused role grant concurrency password"),
                employeeId);
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM audit_event WHERE actor_account_id = ?", MANAGER_ACCOUNT_ID);
        jdbcTemplate.update("DELETE FROM role_grant WHERE account_id IN (?, ?)", MANAGER_ACCOUNT_ID, TARGET_ACCOUNT_ID);
        jdbcTemplate.update("DELETE FROM account WHERE account_id IN (?, ?)", MANAGER_ACCOUNT_ID, TARGET_ACCOUNT_ID);
        jdbcTemplate.update("DELETE FROM employee WHERE employee_id IN (?, ?)", MANAGER_EMPLOYEE_ID, TARGET_EMPLOYEE_ID);
        jdbcTemplate.update("DELETE FROM store WHERE store_id = ?", STORE_ID);
    }

    private record Attempt(boolean success, String errorCode) {
    }
}
