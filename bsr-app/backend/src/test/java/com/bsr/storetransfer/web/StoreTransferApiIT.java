package com.bsr.storetransfer.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bsr.bootstrap.security.BsrAccountPrincipal;
import com.bsr.schema.IsolatedTestDatabaseInitializer;
import com.bsr.storetransfer.transfer.application.TransferService;
import com.bsr.storetransfer.transfer.application.TransferTestData;
import com.bsr.storetransfer.transfer.domain.TransferType;
import java.util.List;
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

@SpringBootTest(properties = {
    "spring.profiles.active=test",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = IsolatedTestDatabaseInitializer.class)
@Transactional
class StoreTransferApiIT {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransferService service;

    @BeforeEach
    void fixture() {
        TransferTestData.insert(jdbc);
    }

    @Test
    @DisplayName("AC-UC14 store HTTP enforces session, CSRF, manager scope and immutable status")
    void storeHttpRequiresSessionCsrfManagerScopeAndRejectsStatusInjection() throws Exception {
        mvc.perform(get("/api/stores").param("storeId", "37001"))
                .andExpect(status().isUnauthorized());
        String creation = """
                {"authorityStoreId":"37001","storeNumber":"S-T07-NEW", "storeName":"T07 New",
                 "openingTime":"09:00:00","closingTime":"20:00:00"}
                """;
        mvc.perform(post("/api/stores").with(manager()).contentType(MediaType.APPLICATION_JSON)
                .content(creation)).andExpect(status().isForbidden());
        mvc.perform(post("/api/stores").with(sourceEmployee()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(creation))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/stores").with(manager()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(creation))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storeId").isString())
                .andExpect(jsonPath("$.businessStatus").value("ACTIVE"));
        mvc.perform(post("/api/stores").with(manager()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(creation.replace(
                        "\"storeName\":\"T07 New\"", "\"storeName\":\"T07 New\",\"businessStatus\":\"INACTIVE\"")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/stores/37001/update").with(manager()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"storeName\":\"Renamed\",\"openingTime\":\"08:00:00\",\"closingTime\":\"20:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storeNumber").value("S-T07-01"));
        mvc.perform(get("/api/stores").param("storeId", "37001").with(manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storeName").value("Renamed"));
        mvc.perform(post("/api/stores/37001/deactivate").with(manager()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storeId").value("37001"))
                .andExpect(jsonPath("$.businessStatus").value("INACTIVE"));
        assertEquals("INACTIVE", jdbc.queryForObject(
                "SELECT business_status FROM store WHERE store_id=37001", String.class));
    }

    @Test
    @DisplayName("AC-UC11/AC-BR27/AC-BR28 transfer HTTP uses string IDs and both-store scope")
    void transferHttpUsesStringIdsBothStoreManagerScopeAndProblemDetail() throws Exception {
        String proposal = """
                {"sourceStoreId":"37001","destinationStoreId":"37002",
                 "transferType":"TEMPORARY","equipmentIds":["37009","37010"]}
                """;
        mvc.perform(post("/api/transfers").with(manager()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(proposal.replace("\"transferType\":\"TEMPORARY\"",
                        "\"transferType\":\"TEMPORARY\",\"transferStatus\":\"COMPLETED\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        jdbc.update("DELETE FROM role_grant WHERE account_id=37004 AND store_id=37002");
        mvc.perform(post("/api/transfers").with(manager()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(proposal))
                .andExpect(status().isForbidden());
        jdbc.update("INSERT INTO role_grant(account_id,role_code,store_id,valid_from) VALUES(37004,'MANAGER',37002,'2020-01-01 00:00:00')");
        mvc.perform(post("/api/transfers").with(manager()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(proposal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferId").isString())
                .andExpect(jsonPath("$.status").value("INITIATED"))
                .andExpect(jsonPath("$.items[0].itemId").isString());
        String id = jdbc.queryForObject("SELECT transfer_id FROM equipment_transfer WHERE source_store_id=37001 AND destination_store_id=37002", Long.class).toString();
        mvc.perform(post("/api/transfers/{id}/approve", id).with(manager()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(get("/api/transfers/{id}", id).with(manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));
        jdbc.update("DELETE FROM role_grant WHERE account_id=37004 AND store_id=37002");
        mvc.perform(get("/api/transfers/{id}", id).with(manager()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSFER_NOT_FOUND"));
        jdbc.update("INSERT INTO role_grant(account_id,role_code,store_id,valid_from) VALUES(37004,'MANAGER',37002,'2020-01-01 00:00:00')");
        jdbc.update("UPDATE equipment SET equipment_status='RENTED' WHERE equipment_id=37011");
        mvc.perform(post("/api/transfers").with(manager()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(proposal.replace("\"37009\",\"37010\"", "\"37011\"")))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("TRANSFER_EQUIPMENT_UNAVAILABLE"))
                .andExpect(jsonPath("$.fieldErrors.invalidEquipmentIds").value("37011"));
        mvc.perform(get("/api/transfers/{id}", "0").with(manager()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/transfers/{id}", "99999999").with(manager()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AC-BR29/AC-BR30/AC-BR31 handoff HTTP enforces store and item scope")
    void employeeHandoffCommandsEnforceStoreAndItemScope() throws Exception {
        var transfer = service.initiate(TransferTestData.manager(), 37001L, 37002L,
                TransferType.TEMPORARY, List.of(37009L));
        service.approve(TransferTestData.manager(), transfer.id());
        String id = Long.toString(transfer.id());
        String itemId = transfer.itemIds().get(37009L).toString();
        mvc.perform(post("/api/transfers/{id}/items/{item}/dispatch", id, itemId)
                .with(manager()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/transfers/{id}/items/{item}/dispatch", id, itemId)
                .with(sourceEmployee()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"));
        mvc.perform(post("/api/transfers/{id}/items/{item}/exception", id, itemId)
                .with(destinationEmployee()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"phase\":\"RECEIPT\",\"note\":\"Seal issue\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].receiptResult").value("EXCEPTION"));
        mvc.perform(post("/api/transfers/{id}/items/{item}/receive", id, itemId)
                .with(sourceEmployee()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/transfers/{id}/items/{item}/receive", id, itemId)
                .with(destinationEmployee()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(get("/api/transfers/{id}", id).with(sourceEmployee()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/transfers").param("storeId", "37001").with(sourceEmployee()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].transferId").value(id));
        mvc.perform(post("/api/transfers/{id}/items/999999/dispatch", id)
                .with(sourceEmployee()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    private RequestPostProcessor manager() { return actor(37004L, "t07-manager"); }
    private RequestPostProcessor sourceEmployee() { return actor(37006L, "t07-source"); }
    private RequestPostProcessor destinationEmployee() { return actor(37008L, "t07-destination"); }

    private RequestPostProcessor actor(long id, String login) {
        var principal = new BsrAccountPrincipal(id, login, "{noop}unused", true);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                principal.getAuthorities()));
    }
}
