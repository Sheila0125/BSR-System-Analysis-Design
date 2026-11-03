package com.bsr.repair.web;

import static com.bsr.repair.application.RepairTestData.*;
import static com.bsr.repair.web.RepairApiFixture.contractorA;
import static com.bsr.repair.web.RepairApiFixture.contractorB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bsr.repair.application.RepairTaskService;
import com.bsr.repair.application.RepairTestData;
import com.bsr.schema.IsolatedTestDatabaseInitializer;
import java.math.BigDecimal;
import java.time.LocalDate;
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
class RepairContractorApiIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired RepairTaskService service;

    @BeforeEach
    void setUp() {
        RepairApiFixture.prepare(jdbc);
    }

    @Test
    @DisplayName("AC-UC08 contractor four views and actions exclude another contractor and all customer fields")
    void AC_UC08_mineViewsAndOwnership() throws Exception {
        long taskId = service.create(RepairTestData.employee(), STORE_ID, EQUIPMENT_ID,
                "Binding damaged", CONTRACTOR_ID).id();
        mvc.perform(get("/api/repair-tasks/mine").with(contractorA()).param("view", "unstarted"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].taskId").value(Long.toString(taskId)))
                .andExpect(jsonPath("$[0].customerId").doesNotExist());
        mvc.perform(get("/api/repair-tasks/mine/{taskId}", taskId).with(contractorB()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
        mvc.perform(post("/api/repair-tasks/mine/{taskId}/start", taskId)
                        .with(contractorB()).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/repair-tasks/mine/{taskId}/start", taskId)
                        .with(contractorA()).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mvc.perform(post("/api/repair-tasks/mine/{taskId}/labor", taskId)
                        .with(contractorA()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"repairDate":"2026-10-01","durationHours":"0.30","handlingResult":"Fixed"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/repair-tasks/mine/{taskId}/labor", taskId)
                        .with(contractorA()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"repairDate":"2026-10-01","durationHours":"0.25","handlingResult":"Fixed"}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contractorId")
                        .value(Long.toString(CONTRACTOR_ID)));
        mvc.perform(get("/api/repair-tasks/mine").with(contractorA()).param("view", "active"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].taskId").value(Long.toString(taskId)));
        mvc.perform(post("/api/repair-tasks/mine/{taskId}/submit", taskId)
                        .with(contractorA()).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_ACCEPTANCE"));
        mvc.perform(get("/api/repair-tasks/mine").with(contractorA()).param("view", "submitted"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].taskId").value(Long.toString(taskId)));
        mvc.perform(get("/api/repair-tasks/mine/charges").with(contractorA()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].contractorId")
                        .value(Long.toString(CONTRACTOR_ID)));
    }

    @Test
    @DisplayName("AC-BR23 reassignment hides task and other contractor labor but preserves own fee history")
    void AC_BR23_reassignmentScopesFeeHistory() throws Exception {
        long taskId = service.create(RepairTestData.employee(), STORE_ID, EQUIPMENT_ID,
                "Binding damaged", CONTRACTOR_ID).id();
        var first = RepairTestData.contractor(CONTRACTOR_ID);
        service.start(first, taskId);
        service.addLabor(first, taskId, LocalDate.of(2026, 10, 1),
                new BigDecimal("0.25"), "A repaired", null);
        service.assign(RepairTestData.employee(), taskId, CONTRACTOR2_ID);
        service.addLabor(RepairTestData.contractor(CONTRACTOR2_ID), taskId,
                LocalDate.of(2026, 10, 2), new BigDecimal("0.25"), "B repaired", null);
        mvc.perform(get("/api/repair-tasks/mine/{taskId}", taskId).with(contractorA()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/repair-tasks/mine/{taskId}/labors", taskId).with(contractorB()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].handlingResult").value("B repaired"));
        mvc.perform(get("/api/repair-tasks/mine/charges").with(contractorA()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].handlingResult").value("A repaired"));
    }
}
