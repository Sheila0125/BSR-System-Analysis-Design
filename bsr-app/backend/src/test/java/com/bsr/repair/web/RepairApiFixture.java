package com.bsr.repair.web;

import static com.bsr.repair.application.RepairTestData.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.bsr.bootstrap.security.BsrAccountPrincipal;
import com.bsr.repair.application.RepairTestData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

final class RepairApiFixture {

    private RepairApiFixture() {
    }

    static void prepare(JdbcTemplate jdbc) {
        RepairTestData.cleanup(jdbc);
        RepairTestData.insert(jdbc);
        jdbc.update("""
                INSERT INTO role_grant(account_id,role_code,store_id,valid_from)
                VALUES(?,'EMPLOYEE',?,'2020-01-01 00:00:00'),
                      (?,'CONTRACTOR',NULL,'2020-01-01 00:00:00'),
                      (?,'CONTRACTOR',NULL,'2020-01-01 00:00:00')
                """, EMPLOYEE_ACCOUNT_ID, STORE_ID, CONTRACTOR_ACCOUNT_ID, CONTRACTOR2_ACCOUNT_ID);
    }

    static RequestPostProcessor employee() {
        return auth(EMPLOYEE_ACCOUNT_ID, "repair-employee");
    }

    static RequestPostProcessor contractorA() {
        return auth(CONTRACTOR_ACCOUNT_ID, "repair-contractor-a");
    }

    static RequestPostProcessor contractorB() {
        return auth(CONTRACTOR2_ACCOUNT_ID, "repair-contractor-b");
    }

    private static RequestPostProcessor auth(long accountId, String login) {
        var principal = new BsrAccountPrincipal(accountId, login, "{noop}unused", true);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                principal.getAuthorities()));
    }
}
