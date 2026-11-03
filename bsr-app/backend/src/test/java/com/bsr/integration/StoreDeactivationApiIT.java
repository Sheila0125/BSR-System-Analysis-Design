package com.bsr.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bsr.bootstrap.security.BsrAccountPrincipal;
import com.bsr.schema.IsolatedTestDatabaseInitializer;
import com.bsr.storetransfer.transfer.application.TransferTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {"spring.profiles.active=test", "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = IsolatedTestDatabaseInitializer.class)
@Transactional
class StoreDeactivationApiIT {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void fixture() { TransferTestData.insert(jdbc); }

    @Test @DisplayName("AC-UC14 stop-store HTTP requires session CSRF manager scope and string IDs")
    void stopStoreHttpIsAuthorizedAndReturnsStringId() throws Exception {
        mvc.perform(post("/api/stores/37001/deactivate").with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/stores/37001/deactivate").with(manager()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/stores/37001/deactivate").with(employee()).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/stores/37001/deactivate").with(manager()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"businessStatus\":\"INACTIVE\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/stores/37001/deactivate").with(manager()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storeId").value("37001"))
                .andExpect(jsonPath("$.businessStatus").value("INACTIVE"));
        assertEquals("INACTIVE", jdbc.queryForObject(
                "SELECT business_status FROM store WHERE store_id=37001", String.class));
    }

    private RequestPostProcessor manager() { return actor(37004L, "t07-manager"); }
    private RequestPostProcessor employee() { return actor(37006L, "t07-source"); }
    private RequestPostProcessor actor(long id, String login) {
        var principal = new BsrAccountPrincipal(id, login, "{noop}unused", true);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                principal.getAuthorities()));
    }
}
