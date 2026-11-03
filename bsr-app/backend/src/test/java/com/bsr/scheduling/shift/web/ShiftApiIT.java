package com.bsr.scheduling.shift.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bsr.bootstrap.security.BsrAccountPrincipal;
import com.bsr.schema.IsolatedTestDatabaseInitializer;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
    "spring.profiles.active=test",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = IsolatedTestDatabaseInitializer.class)
@Transactional
class ShiftApiIT {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void fixture() {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("fixtures/scheduling/t08-shifts.sql"));
            return null;
        });
    }

    @Test
    @DisplayName("AC-UC12/AC-BR33/AC-BR34 HTTP requires session, CSRF, string IDs and valid 08:00-22:00 time")
    void createBoundary() throws Exception {
        mvc.perform(get("/api/shifts").param("storeId", "48001"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/shifts").with(manager()).contentType(MediaType.APPLICATION_JSON)
                .content(proposal())).andExpect(status().isForbidden());
        mvc.perform(post("/api/shifts").with(worker()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(proposal()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/shifts").with(manager()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(proposal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shiftId").isString())
                .andExpect(jsonPath("$.employeeId").value("48005"))
                .andExpect(jsonPath("$.startTime").value("08:00"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(post("/api/shifts").with(manager()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(proposal().replace("\"startTime\":\"08:00\"", "\"startTime\":\"07:59\"")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/shifts").with(manager()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(proposal().replace("\"employeeId\":\"48005\"", "\"employeeId\":48005")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/shifts").with(manager()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(proposal().replace("\"endTime\":\"22:00\"",
                        "\"endTime\":\"22:00\",\"status\":\"CANCELLED\"")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("AC-UC12/AC-UC13 own-list and manager-list enforce explicit identity and store scope")
    void scopedReadsAndObjectVisibility() throws Exception {
        mvc.perform(post("/api/shifts").with(manager()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(proposal()))
                .andExpect(status().isOk());
        String id = jdbc.queryForObject("SELECT shift_id FROM employee_shift WHERE employee_id=48005",
                Long.class).toString();
        mvc.perform(get("/api/me/shifts").with(worker()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].shiftId").value(id));
        mvc.perform(get("/api/me/shifts").with(otherWorker()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/me/shifts").with(manager()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/shifts").param("storeId", "48001").with(worker()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/shifts").param("storeId", "48001").with(manager()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].shiftId").value(id));
        mvc.perform(get("/api/shifts/{id}", "0").with(manager()))
                .andExpect(status().isBadRequest());
        jdbc.update("DELETE FROM role_grant WHERE account_id=48004 AND store_id=48001");
        mvc.perform(get("/api/shifts").param("storeId", "48001").with(manager()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/shifts/{id}", id).with(manager()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SHIFT_NOT_FOUND"));
    }

    @Test
    @DisplayName("AC-UC12/AC-UC13 adjustment and cancellation preserve provenance and reject forged fields")
    void adjustmentAndCancellationBoundary() throws Exception {
        mvc.perform(post("/api/shifts").with(manager()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(proposal()))
                .andExpect(status().isOk());
        String id = jdbc.queryForObject("SELECT shift_id FROM employee_shift WHERE employee_id=48005",
                Long.class).toString();
        mvc.perform(post("/api/shifts/{id}/adjust", id).with(manager()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(proposal().replace("\"employeeId\":\"48005\"", "\"employeeId\":\"48007\"")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.employeeId").value("48007"));
        mvc.perform(post("/api/shifts/{id}/cancel", id).with(manager()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Unavailable\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Unavailable"));
        mvc.perform(post("/api/shifts/{id}/cancel", id).with(manager()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Again\",\"changedByEmployeeId\":\"48005\"}"))
                .andExpect(status().isBadRequest());
    }

    private static String proposal() {
        LocalDate day = LocalDate.now(ZoneId.of("Europe/Oslo")).plusDays(3);
        return "{\"shiftDate\":\"" + day + "\",\"storeId\":\"48001\","
                + "\"employeeId\":\"48005\",\"startTime\":\"08:00\",\"endTime\":\"22:00\"}";
    }

    private RequestPostProcessor manager() { return actor(48004L, "t08-manager"); }
    private RequestPostProcessor worker() { return actor(48006L, "t08-a"); }
    private RequestPostProcessor otherWorker() { return actor(48008L, "t08-b"); }

    private RequestPostProcessor actor(long id, String login) {
        var principal = new BsrAccountPrincipal(id, login, "{noop}unused", true);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                principal.getAuthorities()));
    }
}
