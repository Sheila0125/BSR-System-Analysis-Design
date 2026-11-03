package com.bsr.customerrequest.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bsr.bootstrap.security.BsrAccountPrincipal;
import com.bsr.schema.IsolatedTestDatabaseInitializer;
import org.junit.jupiter.api.BeforeEach;
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
class ContactDetailsHttpIT {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    private RequestPostProcessor actor(long id) {
        return user(new BsrAccountPrincipal(id,"contact-test","unused",true));
    }

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
        jdbc.update("INSERT INTO store(store_id,store_number,store_name,business_status,opening_time,closing_time) VALUES(56001,'CONTACT-S','Contact store','ACTIVE','08:00','22:00')");
        jdbc.update("INSERT INTO employee(employee_id,employee_number,full_name,email,business_status) VALUES(56002,'CONTACT-E','Contact manager','contact-manager@test.invalid','ACTIVE')");
        jdbc.update("INSERT INTO customer(customer_id,customer_number,full_name,email,phone,address,birth_year,height_cm,height_estimated) VALUES(56003,'CONTACT-C','Contact customer','contact-c@test.invalid','123','Old address',1992,170.25,TRUE),(56004,'CONTACT-C2','Other customer','contact-other@test.invalid','456','Other address',1995,180,FALSE)");
        jdbc.update("INSERT INTO account(account_id,login_name,password_hash,enabled,employee_id,customer_id) VALUES(56005,'contact-manager','unused',TRUE,56002,NULL),(56006,'contact-customer','unused',TRUE,NULL,56003),(56007,'contact-other','unused',TRUE,NULL,56004)");
        jdbc.update("INSERT INTO role_grant(account_id,role_code,store_id,valid_from) VALUES(56005,'MANAGER',56001,'2020-01-01'),(56006,'CUSTOMER',NULL,'2020-01-01'),(56007,'CUSTOMER',NULL,'2020-01-01')");
    }

    @Test void customerContactUpdateIsOwnedAuditedAndPreservesMeasurements() throws Exception {
        String body="""
                {"customerId":"56004","fullName":" New name ","email":"new-contact@test.invalid","phone":"+47 555","address":" New address ","birthYear":"1993"}
                """;
        mvc.perform(post("/api/customer/profile/contact").with(actor(56006)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/customer/profile/contact").with(actor(56005)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/customer/profile/contact").with(actor(56006)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.customerId").value("56003"))
                .andExpect(jsonPath("$.fullName").value("New name"))
                .andExpect(jsonPath("$.address").value("New address"))
                .andExpect(jsonPath("$.birthYear").value(1993))
                .andExpect(jsonPath("$.heightCm").value("170.25"))
                .andExpect(jsonPath("$.heightEstimated").value(true));
        mvc.perform(get("/api/customer/profile").with(actor(56007)))
                .andExpect(jsonPath("$.address").value("Other address"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE object_type='CUSTOMER' AND object_id=56003 AND action_code='CUSTOMER_CONTACT_UPDATED'",Integer.class));
        String audit=jdbc.queryForObject("SELECT detail_summary FROM audit_event WHERE object_type='CUSTOMER' AND object_id=56003 AND action_code='CUSTOMER_CONTACT_UPDATED'",String.class);
        org.junit.jupiter.api.Assertions.assertFalse(audit.contains("new-contact@test.invalid"));
    }

    @Test void omittedOptionalContactFieldsSurviveAndExplicitBlanksClear() throws Exception {
        String body="""
                {"fullName":"Name","email":"contact-c@test.invalid","phone":"555"}
                """;
        mvc.perform(post("/api/customer/profile/contact").with(actor(56006)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.address").value("Old address"))
                .andExpect(jsonPath("$.birthYear").value(1992));
        mvc.perform(post("/api/customer/profile/contact").with(actor(56006)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("\"555\"","\"555\",\"address\":\"\",\"birthYear\":\"\"")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.address").isEmpty()).andExpect(jsonPath("$.birthYear").isEmpty());
        mvc.perform(post("/api/customer/profile/measurements").with(actor(56006)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"heightCm":"172.50","heightEstimated":false,"weightKg":null,"weightEstimated":false,"bootSize":null,"bootSizeEstimated":false}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Name"));
    }

    @Test void invalidContactsFailWithoutChanges() throws Exception {
        for(String invalid:new String[]{
                "{\"fullName\":\" \",\"email\":\"a@test.invalid\",\"phone\":\"123\"}",
                "{\"fullName\":\"Name\",\"email\":\"invalid\",\"phone\":\"123\"}",
                "{\"fullName\":\"Name\",\"email\":\"a@test.invalid\",\"phone\":\"123\",\"birthYear\":\"9999\"}"}) {
            mvc.perform(post("/api/customer/profile/contact").with(actor(56006)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
        }
        assertEquals("Old address",jdbc.queryForObject("SELECT address FROM customer WHERE customer_id=56003",String.class));
    }

    @Test void contractorPostalAddressRoundTripsAndOldClientDoesNotEraseIt() throws Exception {
        String body="""
                {"storeId":"56001","name":"Course workshop","contactName":"Contact","email":"course-workshop@test.invalid","phone":"555","postalAddress":" Postal road 8 "}
                """;
        mvc.perform(post("/api/repair-contractors").with(actor(56006)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        var response=mvc.perform(post("/api/repair-contractors").with(actor(56005)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.postalAddress").value("Postal road 8")).andReturn();
        String id=json.readTree(response.getResponse().getContentAsByteArray()).get("contractorId").asText();
        String oldBody=body.replace(",\"postalAddress\":\" Postal road 8 \"","");
        mvc.perform(post("/api/repair-contractors/{id}/update",id).with(actor(56005)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(oldBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.postalAddress").value("Postal road 8"));
        mvc.perform(post("/api/repair-contractors/{id}/update",id).with(actor(56005)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body.replace(" Postal road 8 ","x".repeat(256))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/repair-contractors").param("storeId","999999").with(actor(56005))).andExpect(status().isForbidden());
        mvc.perform(post("/api/repair-contractors/{id}/update",id).with(actor(56005)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body.replace(" Postal road 8 ","")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.postalAddress").isEmpty());
    }

    @Test void managerMaintainsOnlyScopedEmployeeSupplementAndPreservesOtherFields() throws Exception {
        mvc.perform(get("/api/employees/56002/contact").param("storeId","56001").with(actor(56005)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.employeeId").value("56002"));
        String body="""
                {"storeId":"56001","address":" Employee road ","note":" Course note ","businessStatus":"INACTIVE","email":"overwrite@test.invalid"}
                """;
        mvc.perform(post("/api/employees/56002/contact").with(actor(56005)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/employees/56002/contact").with(actor(56005)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.address").value("Employee road"))
                .andExpect(jsonPath("$.note").value("Course note"))
                .andExpect(jsonPath("$.email").value("contact-manager@test.invalid"));
        mvc.perform(post("/api/employees/56002/contact").with(actor(56005)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":\"56001\",\"address\":\"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.address").isEmpty()).andExpect(jsonPath("$.note").value("Course note"));
        assertEquals("ACTIVE",jdbc.queryForObject("SELECT business_status FROM employee WHERE employee_id=56002",String.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE object_type='EMPLOYEE' AND object_id=56002 AND action_code='EMPLOYEE_CONTACT_UPDATED'",Integer.class));
    }

    @Test void employeeCandidatesAreScopedAndContainNoContactSupplement() throws Exception {
        mvc.perform(get("/api/employees").param("storeId","56001").with(actor(56005)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].employeeId").value("56002"))
                .andExpect(jsonPath("$[0].address").doesNotExist()).andExpect(jsonPath("$[0].note").doesNotExist());
        mvc.perform(get("/api/employees").param("storeId","56001").with(actor(56006))).andExpect(status().isForbidden());
        mvc.perform(get("/api/employees").param("storeId","99999").with(actor(56005))).andExpect(status().isForbidden());
    }

    @Test void employeeContactRejectsWrongRoleForeignStoreAndOverlongInput() throws Exception {
        jdbc.update("INSERT INTO employee(employee_id,employee_number,full_name,email,business_status) VALUES(56008,'CONTACT-E2','Other employee','contact-e2@test.invalid','ACTIVE')");
        String body="{\"storeId\":\"56001\",\"address\":\"New\",\"note\":\"New\"}";
        mvc.perform(get("/api/employees/56002/contact").param("storeId","56001").with(actor(56006))).andExpect(status().isForbidden());
        mvc.perform(get("/api/employees/56002/contact").param("storeId","99999").with(actor(56005))).andExpect(status().isForbidden());
        mvc.perform(get("/api/employees/56008/contact").param("storeId","56001").with(actor(56005))).andExpect(status().isNotFound());
        mvc.perform(post("/api/employees/56008/contact").with(actor(56005)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
        mvc.perform(post("/api/employees/56002/contact").with(actor(56005)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body.replace("\"New\"","\""+"x".repeat(501)+"\""))).andExpect(status().isBadRequest());
    }
}
