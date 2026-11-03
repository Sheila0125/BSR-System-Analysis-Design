package com.bsr.repair.web;

import static com.bsr.repair.application.RepairTestData.*;
import static com.bsr.repair.web.RepairApiFixture.employee;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bsr.schema.IsolatedTestDatabaseInitializer;
import com.bsr.repair.application.RepairTaskService;
import com.bsr.repair.application.RepairContractorService;
import com.bsr.repair.application.RepairTestData;
import com.bsr.repair.domain.RepairRatePeriod;
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
class RepairEmployeeApiIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired RepairTaskService service;
    @Autowired RepairContractorService management;

    @BeforeEach
    void setUp() {
        RepairApiFixture.prepare(jdbc);
    }

    @Test
    @DisplayName("AC-UC07 employee repair API enforces Session, CSRF, store role and string identifiers")
    void AC_UC07_employeeCreateAndAssignBoundary() throws Exception {
        mvc.perform(get("/api/repair-tasks").param("storeId", Long.toString(STORE_ID)))
                .andExpect(status().isUnauthorized());
        String body = """
                {"storeId":"36001","equipmentId":"36004","faultDescription":"Broken binding"}
                """;
        mvc.perform(post("/api/repair-tasks").with(employee()).contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/repair-tasks").with(employee()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").isString())
                .andExpect(jsonPath("$.status").value("UNASSIGNED"))
                .andExpect(jsonPath("$.customerId").doesNotExist());
        mvc.perform(get("/api/repair-tasks").with(employee())
                        .param("storeId", Long.toString(STORE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].equipmentId").value(Long.toString(EQUIPMENT_ID)));
        jdbc.update("UPDATE role_grant SET role_code='MANAGER' WHERE account_id=?", EMPLOYEE_ACCOUNT_ID);
        mvc.perform(get("/api/repair-tasks").with(employee())
                        .param("storeId", Long.toString(STORE_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACTION_NOT_AUTHORIZED"));
    }

    @Test
    @DisplayName("AC-UC07 malformed repair input returns Problem Detail and no partial task")
    void AC_UC07_badInputProblemDetail() throws Exception {
        mvc.perform(post("/api/repair-tasks").with(employee()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"storeId":"36001","equipmentId":"0","faultDescription":"Broken"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("AC-UC09 employee rejection, acceptance and later charge confirmation expose distinct states")
    void AC_UC09_acceptanceAndChargeBoundary() throws Exception {
        long taskId = service.create(RepairTestData.employee(), STORE_ID, EQUIPMENT_ID,
                "Binding damaged", CONTRACTOR_ID).id();
        var contractor = RepairTestData.contractor(CONTRACTOR_ID);
        service.start(contractor, taskId);
        service.addLabor(contractor, taskId, LocalDate.of(2026, 10, 1),
                new BigDecimal("0.25"), "Repaired", null);
        service.submit(contractor, taskId);
        mvc.perform(post("/api/repair-tasks/{id}/reject", taskId).with(employee()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/repair-tasks/{id}/reject", taskId).with(employee()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Still loose\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RETURNED"));
        service.start(contractor, taskId);
        service.addLabor(contractor, taskId, LocalDate.of(2026, 10, 2),
                new BigDecimal("0.25"), "Rework", null);
        service.submit(contractor, taskId);
        mvc.perform(post("/api/repair-tasks/{id}/accept", taskId).with(employee()).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_CHARGE"));
        mvc.perform(post("/api/repair-tasks/{id}/confirm-charge", taskId)
                        .with(employee()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_STATE_CONFLICT"));
        management.addRate(RepairTestData.managerOnly(), STORE_ID,
                new RepairRatePeriod(CONTRACTOR_ID, LocalDate.of(2026, 1, 1), null,
                        new BigDecimal("50.00")));
        mvc.perform(post("/api/repair-tasks/{id}/confirm-charge", taskId)
                        .with(employee()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

}
