package com.bsr.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bsr.identity.api.AccountAuthentication;
import com.bsr.identity.api.IdentityType;
import com.bsr.identity.api.RoleCode;
import com.bsr.schema.IsolatedTestDatabaseInitializer;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(properties = {
    "spring.profiles.active=test",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@ContextConfiguration(initializers = IsolatedTestDatabaseInitializer.class)
class IdentityRoleFixtureIT {

    private static final long CUSTOMER_ACCOUNT_ID = 25010L;
    private static final long EMPLOYEE_ACCOUNT_ID = 25011L;
    private static final long MANAGER_ACCOUNT_ID = 25012L;
    private static final long CONTRACTOR_ACCOUNT_ID = 25013L;

    @Autowired
    private AccountAuthentication authentication;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void loadFixture() {
        cleanup();
        new ResourceDatabasePopulator(new ClassPathResource("fixtures/identity/t03-role-scenarios.sql"))
                .execute(jdbcTemplate.getDataSource());
    }

    @AfterEach
    void removeFixture() {
        cleanup();
    }

    @Test
    @DisplayName("AC-BR01/02 four identities, explicit multi-role grants, and expired grants load from MySQL")
    void AC_BR01_BR02_fourRoleFixtureLoadsOnlyCurrentExplicitGrants() {
        var customer = authentication.loadActor(CUSTOMER_ACCOUNT_ID);
        var employee = authentication.loadActor(EMPLOYEE_ACCOUNT_ID);
        var manager = authentication.loadActor(MANAGER_ACCOUNT_ID);
        var contractor = authentication.loadActor(CONTRACTOR_ACCOUNT_ID);

        assertEquals(IdentityType.CUSTOMER, customer.identityType());
        assertEquals(Set.of("CUSTOMER@GLOBAL"), grants(customer));
        assertEquals(IdentityType.EMPLOYEE, employee.identityType());
        assertEquals(Set.of("EMPLOYEE@25001"), grants(employee));
        assertEquals(IdentityType.EMPLOYEE, manager.identityType());
        assertEquals(Set.of("MANAGER@25001", "EMPLOYEE@25002"), grants(manager));
        assertFalse(grants(manager).contains("EMPLOYEE@25001"), "The expired grant must be excluded.");
        assertEquals(IdentityType.CONTRACTOR, contractor.identityType());
        assertEquals(Set.of("CONTRACTOR@GLOBAL"), grants(contractor));
        assertTrue(customer.activeGrants().stream().allMatch(grant -> grant.role() == RoleCode.CUSTOMER));
    }

    private Set<String> grants(com.bsr.identity.api.ActorContext actor) {
        return actor.activeGrants().stream()
                .map(grant -> grant.role().name() + "@" + (grant.storeId() == null ? "GLOBAL" : grant.storeId()))
                .collect(Collectors.toSet());
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM role_grant WHERE account_id BETWEEN 25010 AND 25013");
        jdbcTemplate.update("DELETE FROM account WHERE account_id BETWEEN 25010 AND 25013");
        jdbcTemplate.update("DELETE FROM repair_contractor WHERE contractor_id = 25006");
        jdbcTemplate.update("DELETE FROM employee WHERE employee_id IN (25004, 25005)");
        jdbcTemplate.update("DELETE FROM customer WHERE customer_id = 25003");
        jdbcTemplate.update("DELETE FROM store WHERE store_id IN (25001, 25002)");
    }
}
