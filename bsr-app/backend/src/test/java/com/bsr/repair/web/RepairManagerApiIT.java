package com.bsr.repair.web;

import static com.bsr.repair.application.RepairTestData.*;
import static com.bsr.repair.web.RepairApiFixture.employee;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bsr.schema.IsolatedTestDatabaseInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {"spring.profiles.active=test", "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = IsolatedTestDatabaseInitializer.class)
@Transactional
class RepairManagerApiIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        RepairApiFixture.prepare(jdbc);
    }

    @Test
    @DisplayName("AC-UC15 manager rate HTTP enforces explicit store role, validates dates and maps overlap")
    void AC_UC15_rateBoundary() throws Exception {
        String rate = """
                {"storeId":"36001","effectiveFrom":"2026-01-01","effectiveTo":"2026-01-31","hourlyRate":"100.00"}
                """;
        mvc.perform(post("/api/repair-contractors/{id}/rates", CONTRACTOR_ID)
                        .with(employee()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(rate))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACTION_NOT_AUTHORIZED"));
        jdbc.update("UPDATE role_grant SET role_code='MANAGER' WHERE account_id=?", EMPLOYEE_ACCOUNT_ID);
        mvc.perform(post("/api/repair-contractors/{id}/rates", CONTRACTOR_ID)
                        .with(employee()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(rate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rateId").isString());
        mvc.perform(post("/api/repair-contractors/{id}/rates", CONTRACTOR_ID)
                        .with(employee()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(rate.replace("2026-01-01", "2026-01-31")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RATE_OVERLAP"));
        mvc.perform(get("/api/repair-contractors/{id}/rates", CONTRACTOR_ID)
                        .with(employee()).param("storeId", Long.toString(STORE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hourlyRate").value("100.00"));
        mvc.perform(post("/api/repair-contractors/{id}/rates", CONTRACTOR_ID)
                        .with(employee()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(rate.replace("2026-01-31", "2025-12-31")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
