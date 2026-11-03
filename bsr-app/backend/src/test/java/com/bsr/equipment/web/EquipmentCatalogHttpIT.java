package com.bsr.equipment.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bsr.bootstrap.security.BsrAccountPrincipal;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties={"spring.profiles.active=test","spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureMockMvc
@ContextConfiguration(initializers=IsolatedTestDatabaseInitializer.class)
@Transactional
class EquipmentCatalogHttpIT {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    private BsrAccountPrincipal principal(long id) { return new BsrAccountPrincipal(id,"catalog-test","unused",true); }

    // Exercise the real cookie repository. SecurityMockMvc's csrf() swaps the shared filter
    // repository for a session-backed test repository and contaminates later auth-cookie tests.
    private RequestPostProcessor csrf() throws Exception {
        var response=mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
        var body=json.readTree(response.getContentAsByteArray());
        var cookie=response.getCookie("XSRF-TOKEN");
        org.junit.jupiter.api.Assertions.assertNotNull(cookie);
        return request -> {
            request.setCookies(cookie);
            request.addHeader(body.get("headerName").asText(),body.get("token").asText());
            return request;
        };
    }

    @BeforeEach void fixture() {
        jdbc.update("INSERT INTO store(store_id,store_number,store_name,business_status,opening_time,closing_time) VALUES(55001,'CAT-S1','Catalog store','ACTIVE','08:00','22:00'),(55002,'CAT-S2','Other store','ACTIVE','08:00','22:00')");
        jdbc.update("INSERT INTO employee(employee_id,employee_number,full_name,email,business_status) VALUES(55003,'CAT-E','Catalog manager','catalog@test.invalid','ACTIVE'),(55010,'CAT-E2','Catalog employee','catalog-employee@test.invalid','ACTIVE')");
        jdbc.update("INSERT INTO customer(customer_id,customer_number,full_name,email,phone) VALUES(55004,'CAT-C','Catalog customer','customer-cat@test.invalid','123')");
        jdbc.update("INSERT INTO account(account_id,login_name,password_hash,enabled,employee_id,customer_id) VALUES(55005,'catalog-manager','unused',TRUE,55003,NULL),(55006,'catalog-employee','unused',TRUE,55010,NULL),(55007,'catalog-customer','unused',TRUE,NULL,55004)");
        jdbc.update("INSERT INTO role_grant(account_id,role_code,store_id,valid_from) VALUES(55005,'MANAGER',55001,'2020-01-01'),(55006,'EMPLOYEE',55001,'2020-01-01'),(55007,'CUSTOMER',NULL,'2020-01-01')");
        jdbc.update("INSERT INTO equipment(equipment_id,inventory_number,equipment_name,equipment_category,skill_level,home_store_id,current_store_id,equipment_status,current_daily_rate) VALUES(55008,'CAT-FAULT','Faulty ski','SKI','BEGINNER',55001,55001,'WAITING_REPAIR',99.50),(55009,'CAT-OTHER','Private ski','SKI','BEGINNER',55002,55002,'AVAILABLE',120.00)");
    }

    @Test @DisplayName("AC-UC10 catalog includes faulty equipment, filters exact inventory and preserves current prices")
    void allStatesAndScope() throws Exception {
        mvc.perform(get("/api/equipment/catalog").param("storeId","55001").param("status","WAITING_REPAIR").param("inventoryNumber","CAT-FAULT").with(user(principal(55006))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].equipmentId").value("55008"))
                .andExpect(jsonPath("$[0].status").value("WAITING_REPAIR"))
                .andExpect(jsonPath("$[0].currentDailyRate").value("99.50"));
        mvc.perform(get("/api/equipment/catalog").param("storeId","55002").with(user(principal(55006)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/equipment/catalog").param("storeId","55001").with(user(principal(55007)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/equipment/catalog").param("storeId","55001").param("status","UNKNOWN").with(user(principal(55006)))).andExpect(status().isBadRequest());
    }

    @Test @DisplayName("Equipment audit enforces manager scope and object existence")
    void equipmentAuditIsScoped() throws Exception {
        jdbc.update("INSERT INTO audit_event(actor_account_id,object_type,object_id,action_code,occurred_at,result_code,detail_summary) VALUES(55005,'EQUIPMENT',55008,'CATALOG_TEST',UTC_TIMESTAMP(),'SUCCESS','Synthetic fixture')");
        mvc.perform(get("/api/audit-events").param("objectType","EQUIPMENT").param("objectId","55008").with(user(principal(55005))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].actionCode").value("CATALOG_TEST"));
        mvc.perform(get("/api/audit-events").param("objectType","EQUIPMENT").param("objectId","55009").with(user(principal(55005))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/audit-events").param("objectType","EQUIPMENT").param("objectId","55008").with(user(principal(55006))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/audit-events").param("objectType","EQUIPMENT").param("objectId","999999999").with(user(principal(55005))))
                .andExpect(status().isNotFound());
    }

    @Test @DisplayName("AC-UC10 manager creates priced archive, updates metadata with revision and cannot overwrite state")
    void createAndGuardedUpdate() throws Exception {
        String create="""
                {"storeId":"55001","inventoryNumber":"CAT-NEW","equipmentName":"New ski","equipmentCategory":"SKI","skillLevel":"BEGINNER","description":"Fitting details","purchaseDate":"2026-01-01","purchasePrice":"2500.00","retailPrice":"4200.00","currentDailyRate":null}
                """;
        var result=mvc.perform(post("/api/equipment/catalog").with(user(principal(55005))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.currentDailyRate").value("350.00"))
                .andExpect(jsonPath("$.description").value("Fitting details"))
                .andExpect(jsonPath("$.retailPrice").value("4200.00"))
                .andExpect(jsonPath("$.revision").value("0")).andReturn();
        String id=json.readTree(result.getResponse().getContentAsString()).get("equipmentId").asText();
        jdbc.update("UPDATE equipment SET equipment_status='RENTED' WHERE equipment_id=?",id);
        String update="""
                {"storeId":"55001","equipmentName":"Updated ski","skillLevel":"BEGINNER","description":"Updated details","purchaseDate":"2026-01-01","purchasePrice":"2500.00","retailPrice":"4300.00","currentDailyRate":"360.00","expectedRevision":"0","status":"AVAILABLE","currentStoreId":"55002"}
                """;
        mvc.perform(post("/api/equipment/catalog/"+id+"/update").with(user(principal(55005))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentDailyRate").value("360.00"))
                .andExpect(jsonPath("$.status").value("RENTED"))
                .andExpect(jsonPath("$.currentStoreId").value("55001"))
                .andExpect(jsonPath("$.revision").value("1"));
        mvc.perform(post("/api/equipment/catalog/"+id+"/update").with(user(principal(55005))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isConflict());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE object_type='EQUIPMENT' AND object_id=?",Integer.class,Long.parseLong(id)));
        assertEquals("99.50",jdbc.queryForObject("SELECT current_daily_rate FROM equipment WHERE equipment_id=55008",java.math.BigDecimal.class).toPlainString());
    }

    @Test @DisplayName("AC-UC10 archive writes require manager and CSRF; invalid or duplicate money/inventory rejected")
    void validationAndWriteAuthorization() throws Exception {
        String body="""
                {"storeId":"55001","inventoryNumber":"CAT-FAULT","equipmentName":"New ski","equipmentCategory":"SKI","skillLevel":"BEGINNER","currentDailyRate":"350.00"}
                """;
        mvc.perform(post("/api/equipment/catalog").with(user(principal(55006))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/equipment/catalog").with(user(principal(55005))).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/equipment/catalog").with(user(principal(55005))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        mvc.perform(post("/api/equipment/catalog").with(user(principal(55005))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body.replace("350.00","-1.00"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/equipment/catalog").with(user(principal(55005))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body.replace("350.00","350.001"))).andExpect(status().isBadRequest());
    }

    @Test @DisplayName("AC-BR09 BSR tariff is authenticated NOK reference, not a rewrite of existing current prices")
    void tariff() throws Exception {
        mvc.perform(get("/api/pricing")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/pricing").with(user(principal(55007))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currency").value("NOK"))
                .andExpect(jsonPath("$.dailyRates.SKI").value("350.00"))
                .andExpect(jsonPath("$.dailyRates.SNOWBOARD").value("350.00"))
                .andExpect(jsonPath("$.dailyRates.BOOTS").value("180.00"))
                .andExpect(jsonPath("$.dailyRates.POLES").value("60.00"))
                .andExpect(jsonPath("$.dailyRates.GOGGLES").value("90.00"))
                .andExpect(jsonPath("$.dailyRates.HELMET").value("80.00"));
        assertEquals("99.50",jdbc.queryForObject("SELECT current_daily_rate FROM equipment WHERE equipment_id=55008",java.math.BigDecimal.class).toPlainString());
    }
}
