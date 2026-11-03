package com.bsr.customerrequest.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bsr.schema.IsolatedTestDatabaseInitializer;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
    "spring.profiles.active=test",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = IsolatedTestDatabaseInitializer.class)
@Transactional
class CustomerRequestHttpIT {

    private static final long STORE_ID = 26201L;
    private static final long CUSTOMER_ID = 26202L;
    private static final long ACCOUNT_ID = 26203L;
    private static final long GRANT_ID = 26204L;
    private static final long OTHER_CUSTOMER_ID = 26205L;
    private static final long OTHER_ACCOUNT_ID = 26206L;
    private static final long OTHER_GRANT_ID = 26207L;
    private static final long EQUIPMENT_ID = 26208L;
    private static final long EMPLOYEE_ID = 26209L;
    private static final long EMPLOYEE_ACCOUNT_ID = 26210L;
    private static final long EMPLOYEE_GRANT_ID = 26211L;
    private static final long INACTIVE_STORE_ID = 26212L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void insertCustomers() {
        jdbcTemplate.update(
                "INSERT INTO store (store_id, store_number, store_name, address, business_status, opening_time, closing_time) VALUES (?, 'S-CR-HTTP', 'HTTP Store', NULL, 'ACTIVE', '08:00:00', '22:00:00')",
                STORE_ID);
        jdbcTemplate.update(
                "INSERT INTO store (store_id, store_number, store_name, address, business_status, opening_time, closing_time) VALUES (?, 'S-CR-OFF', 'Inactive HTTP Store', NULL, 'INACTIVE', '08:00:00', '22:00:00')",
                INACTIVE_STORE_ID);
        insertCustomer(CUSTOMER_ID, "C-CR-HTTP", "http-customer@test.invalid");
        insertCustomer(OTHER_CUSTOMER_ID, "C-CR-OTHER", "http-other@test.invalid");
        insertAccount(ACCOUNT_ID, "http-customer", CUSTOMER_ID, GRANT_ID);
        insertAccount(OTHER_ACCOUNT_ID, "http-other", OTHER_CUSTOMER_ID, OTHER_GRANT_ID);
        jdbcTemplate.update(
                "INSERT INTO employee (employee_id, employee_number, full_name, email, phone, business_status) VALUES (?, 'E-CR-HTTP', 'HTTP Employee', 'http-employee@test.invalid', '+47000026209', 'ACTIVE')",
                EMPLOYEE_ID);
        jdbcTemplate.update(
                "INSERT INTO account (account_id, login_name, password_hash, enabled, customer_id, employee_id, contractor_id) VALUES (?, 'http-employee', ?, TRUE, NULL, ?, NULL)",
                EMPLOYEE_ACCOUNT_ID,
                passwordEncoder.encode("customer test password"),
                EMPLOYEE_ID);
        jdbcTemplate.update(
                "INSERT INTO role_grant (role_grant_id, account_id, role_code, store_id, valid_from, valid_to) VALUES (?, ?, 'EMPLOYEE', ?, '2020-01-01 00:00:00', NULL)",
                EMPLOYEE_GRANT_ID,
                EMPLOYEE_ACCOUNT_ID,
                STORE_ID);
        jdbcTemplate.update(
                """
                INSERT INTO equipment (
                    equipment_id, inventory_number, equipment_name, equipment_category, skill_level,
                    home_store_id, current_store_id, equipment_status, purchase_date,
                    purchase_price, current_daily_rate
                ) VALUES (?, 'INV-CR-HTTP', 'HTTP Unreserved Ski', 'SKI', 'INTERMEDIATE',
                    ?, ?, 'AVAILABLE', '2025-01-01', 1000.00, 99.50)
                """,
                EQUIPMENT_ID,
                STORE_ID,
                STORE_ID);
    }

    @Test
    @DisplayName("AC-WX01 customer Session/CSRF API preserves profile values and owned request scope")
    void AC_WX01_customerSessionApiProfileRequestsDuplicateAndOwnership() throws Exception {
        mockMvc.perform(get("/api/customer/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        AuthSession auth = login("http-customer");
        mockMvc.perform(get("/api/customer/profile").session(auth.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(Long.toString(CUSTOMER_ID)))
                .andExpect(jsonPath("$.heightCm").value("170.00"));

        String measurementJson = """
                {
                  "heightCm":"171.25","heightEstimated":true,
                  "weightKg":"69.50","weightEstimated":false,
                  "bootSize":"42.5","bootSizeEstimated":true
                }
                """;
        mockMvc.perform(post("/api/customer/profile/measurements")
                        .session(auth.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(measurementJson))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        AuthSession measurementCsrf = csrf(auth.session());
        mockMvc.perform(post("/api/customer/profile/measurements")
                        .session(auth.session())
                        .cookie(measurementCsrf.cookie())
                        .header("X-XSRF-TOKEN", measurementCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(measurementJson.replace("171.25", "171.123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/customer/profile/measurements")
                        .session(auth.session())
                        .cookie(measurementCsrf.cookie())
                        .header("X-XSRF-TOKEN", measurementCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(measurementJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heightCm").value("171.25"))
                .andExpect(jsonPath("$.heightEstimated").value(true))
                .andExpect(jsonPath("$.bootSizeEstimated").value(true));

        MvcResult submitted = submit(auth.session(), false, status().isOk());
        String requestId = objectMapper.readTree(submitted.getResponse().getContentAsByteArray())
                .get("requestId")
                .asText();

        submit(auth.session(), false, status().isConflict())
                .getResponse();
        submit(auth.session(), true, status().isOk());

        mockMvc.perform(get("/api/customer/requests").session(auth.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].requestId").isString())
                .andExpect(jsonPath("$[0].customerId").value(Long.toString(CUSTOMER_ID)))
                .andExpect(jsonPath("$[0].items[0].equipmentCategory").value("SKI"));

        mockMvc.perform(get("/api/customer/requests/{requestId}", requestId).session(auth.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.status").value("PENDING"));

        AuthSession other = login("http-other");
        mockMvc.perform(get("/api/customer/requests/{requestId}", requestId).session(other.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RENTAL_REQUEST_NOT_FOUND"));

        assertEquals("AVAILABLE", jdbcTemplate.queryForObject(
                "SELECT equipment_status FROM equipment WHERE equipment_id = ?",
                String.class,
                EQUIPMENT_ID));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE object_type = 'RENTAL_REQUEST' AND action_code = 'RENTAL_REQUEST_SUBMITTED' AND result_code = 'SUCCESS'",
                Integer.class));
    }

    @Test
    @DisplayName("AC-UC01 inactive stores reject new requests without persistence or success audit")
    void AC_UC01_inactiveStoreCannotReceiveNewRequest() throws Exception {
        AuthSession auth = login("http-customer");

        submit(auth.session(), INACTIVE_STORE_ID, false, status().isConflict())
                .getResponse();

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rental_request WHERE service_store_id = ?",
                Integer.class,
                INACTIVE_STORE_ID));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE object_type = 'RENTAL_REQUEST' AND action_code = 'RENTAL_REQUEST_SUBMITTED'",
                Integer.class));
    }

    @Test
    @DisplayName("AC-UC04/AC-BR02 supplement loop requires Session, CSRF, ownership and explicit store authorization")
    void AC_UC04_BR02_supplementLoopSessionCsrfOwnershipAndStoreScope() throws Exception {
        AuthSession customer = login("http-customer");
        MvcResult submitted = submit(customer.session(), false, status().isOk());
        String requestId = objectMapper.readTree(submitted.getResponse().getContentAsByteArray())
                .get("requestId")
                .asText();

        mockMvc.perform(get("/api/employee/rental-requests").param("storeId", Long.toString(STORE_ID)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        AuthSession employee = login("http-employee");
        mockMvc.perform(get("/api/employee/rental-requests")
                        .session(employee.session())
                        .param("storeId", Long.toString(STORE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requestId").value(requestId))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].customerId").value(Long.toString(CUSTOMER_ID)));

        String requestMoreInformationPath =
                "/api/employee/rental-requests/{requestId}/request-more-information";
        String reasonJson = """
                {"reason":"Confirm boot size"}
                """;
        mockMvc.perform(post(requestMoreInformationPath, requestId)
                        .session(employee.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reasonJson))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        AuthSession actionCsrf = csrf(employee.session());
        mockMvc.perform(post(requestMoreInformationPath, requestId)
                        .session(employee.session())
                        .cookie(actionCsrf.cookie())
                        .header("X-XSRF-TOKEN", actionCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reasonJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEEDS_INFO"))
                .andExpect(jsonPath("$.supplements[0].cycleNumber").value(1))
                .andExpect(jsonPath("$.supplements[0].reason").value("Confirm boot size"))
                .andExpect(jsonPath("$.supplements[0].customerResponse").isEmpty());

        String resubmitPath = "/api/customer/requests/{requestId}/resubmit-supplement";
        String responseJson = """
                {"response":"Measurements updated"}
                """;
        mockMvc.perform(post(resubmitPath, requestId)
                        .session(customer.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(responseJson))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        AuthSession other = login("http-other");
        AuthSession otherCsrf = csrf(other.session());
        mockMvc.perform(post(resubmitPath, requestId)
                        .session(other.session())
                        .cookie(otherCsrf.cookie())
                        .header("X-XSRF-TOKEN", otherCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(responseJson))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RENTAL_REQUEST_NOT_FOUND"));

        AuthSession customerCsrf = csrf(customer.session());
        mockMvc.perform(post(resubmitPath, requestId)
                        .session(customer.session())
                        .cookie(customerCsrf.cookie())
                        .header("X-XSRF-TOKEN", customerCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(responseJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.supplements[0].reason").value("Confirm boot size"))
                .andExpect(jsonPath("$.supplements[0].customerResponse").value("Measurements updated"))
                .andExpect(jsonPath("$.supplements[0].respondedAt").isString());

        mockMvc.perform(post("/api/employee/rental-requests/{requestId}/accept", requestId)
                        .session(employee.session())
                        .cookie(actionCsrf.cookie())
                        .header("X-XSRF-TOKEN", actionCsrf.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.acceptedByEmployeeId").value(Long.toString(EMPLOYEE_ID)));

        assertEquals("ACCEPTED", jdbcTemplate.queryForObject(
                "SELECT request_status FROM rental_request WHERE request_id = ?",
                String.class,
                requestId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE object_type = 'RENTAL_REQUEST' AND object_id = ? AND action_code = 'RENTAL_REQUEST_ACCEPTED' AND result_code = 'SUCCESS'",
                Integer.class,
                requestId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rental_request_supplement WHERE request_id = ? AND customer_response = 'Measurements updated'",
                Integer.class,
                requestId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE object_type = 'RENTAL_REQUEST' AND object_id = ? AND action_code = 'RENTAL_REQUEST_INFO_REQUESTED' AND result_code = 'SUCCESS'",
                Integer.class,
                requestId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE object_type = 'RENTAL_REQUEST' AND object_id = ? AND action_code = 'RENTAL_REQUEST_INFO_RESUBMITTED' AND result_code = 'SUCCESS'",
                Integer.class,
                requestId));
    }

    @Test
    @DisplayName("AC-BR06 exact number or email lookup distinguishes customers with the same name")
    void AC_BR06_employeeVerifiesByUniqueIdentifiersNotName() throws Exception {
        mockMvc.perform(get("/api/employee/customers/verify")
                        .param("storeId", Long.toString(STORE_ID))
                        .param("customerNumber", "C-CR-HTTP"))
                .andExpect(status().isUnauthorized());

        AuthSession employee = login("http-employee");
        mockMvc.perform(get("/api/employee/customers/verify")
                        .session(employee.session())
                        .param("storeId", Long.toString(STORE_ID))
                        .param("customerNumber", "C-CR-HTTP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(Long.toString(CUSTOMER_ID)))
                .andExpect(jsonPath("$.customerNumber").value("C-CR-HTTP"))
                .andExpect(jsonPath("$.fullName").value("Same Name"));

        mockMvc.perform(get("/api/employee/customers/verify")
                        .session(employee.session())
                        .param("storeId", Long.toString(STORE_ID))
                        .param("email", "http-other@test.invalid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(Long.toString(OTHER_CUSTOMER_ID)))
                .andExpect(jsonPath("$.fullName").value("Same Name"));

        mockMvc.perform(get("/api/employee/customers/verify")
                        .session(employee.session())
                        .param("storeId", Long.toString(STORE_ID))
                        .param("customerNumber", "Same Name"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/employee/customers/verify")
                        .session(employee.session())
                        .param("storeId", Long.toString(STORE_ID))
                        .param("customerNumber", "C-CR-HTTP")
                        .param("email", "http-customer@test.invalid"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/employee/customers/verify")
                        .session(employee.session())
                        .param("storeId", "999999")
                        .param("customerNumber", "C-CR-HTTP"))
                .andExpect(status().isForbidden());

        AuthSession customer = login("http-customer");
        mockMvc.perform(get("/api/employee/customers/verify")
                        .session(customer.session())
                        .param("storeId", Long.toString(STORE_ID))
                        .param("customerNumber", "C-CR-OTHER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-UC04/AC-BR13 equipment selection query is store-scoped and never reserves inventory")
    void AC_UC04_BR13_availableEquipmentQueryIsScopedAndReadOnly() throws Exception {
        String path = "/api/equipment/available";
        mockMvc.perform(get(path).param("storeId", Long.toString(STORE_ID)))
                .andExpect(status().isUnauthorized());

        AuthSession employee = login("http-employee");
        mockMvc.perform(get(path).session(employee.session())
                        .param("storeId", Long.toString(STORE_ID))
                        .param("category", "SKI")
                        .param("skillLevel", "INTERMEDIATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].equipmentId").value(Long.toString(EQUIPMENT_ID)))
                .andExpect(jsonPath("$[0].currentStoreId").value(Long.toString(STORE_ID)))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$[0].currentDailyRate").value("99.50"))
                .andExpect(jsonPath("$[0].purchasePrice").doesNotExist());
        mockMvc.perform(get(path).session(employee.session())
                        .param("storeId", Long.toString(STORE_ID))
                        .param("category", "SNOWBOARD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get(path).session(employee.session()).param("storeId", "999999"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(path).session(employee.session()).param("storeId", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(path).session(employee.session())
                        .param("storeId", Long.toString(STORE_ID))
                        .param("category", "X".repeat(41)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        AuthSession customer = login("http-customer");
        mockMvc.perform(get(path).session(customer.session()).param("storeId", Long.toString(STORE_ID)))
                .andExpect(status().isForbidden());

        jdbcTemplate.update("UPDATE role_grant SET role_code = 'MANAGER' WHERE role_grant_id = ?",
                EMPLOYEE_GRANT_ID);
        mockMvc.perform(get(path).session(employee.session()).param("storeId", Long.toString(STORE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].inventoryNumber").value("INV-CR-HTTP"));
        assertEquals("AVAILABLE", jdbcTemplate.queryForObject(
                "SELECT equipment_status FROM equipment WHERE equipment_id = ?", String.class, EQUIPMENT_ID));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rental_item WHERE equipment_id = ?", Integer.class, EQUIPMENT_ID));
    }

    @Test
    @DisplayName("AC-UC01 employee measurement review requires explicit store scope, CSRF and current values")
    void AC_UC01_employeeReviewClearsVerifiedEstimatesWithAudit() throws Exception {
        jdbcTemplate.update("UPDATE customer SET height_estimated = TRUE WHERE customer_id = ?", CUSTOMER_ID);
        String path = "/api/employee/customers/{customerId}/review-measurements";
        String stale = """
                {"customerNumber":"C-CR-HTTP",
                 "expected":{"heightCm":"171.00","heightEstimated":true,
                   "weightKg":"70.00","weightEstimated":false,"bootSize":"42.0","bootSizeEstimated":false},
                 "updated":{"heightCm":"171.25","heightEstimated":false,
                   "weightKg":"70.00","weightEstimated":false,"bootSize":"42.0","bootSizeEstimated":false}}
                """;
        String current = stale.replace("\"heightCm\":\"171.00\"", "\"heightCm\":\"170.00\"");
        AuthSession employee = login("http-employee");
        mockMvc.perform(post(path, CUSTOMER_ID).session(employee.session())
                        .param("storeId", Long.toString(STORE_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(current))
                .andExpect(status().isForbidden());

        AuthSession csrf = csrf(employee.session());
        mockMvc.perform(post(path, CUSTOMER_ID).session(employee.session())
                        .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .param("storeId", "999999")
                        .contentType(MediaType.APPLICATION_JSON).content(current))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(path, CUSTOMER_ID).session(employee.session())
                        .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .param("storeId", Long.toString(STORE_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(stale))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CUSTOMER_PROFILE_CHANGED"));

        AuthSession customer = login("http-customer");
        AuthSession customerCsrf = csrf(customer.session());
        mockMvc.perform(post(path, CUSTOMER_ID).session(customer.session())
                        .cookie(customerCsrf.cookie()).header("X-XSRF-TOKEN", customerCsrf.token())
                        .param("storeId", Long.toString(STORE_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(current))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(path, CUSTOMER_ID).session(employee.session())
                        .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .param("storeId", Long.toString(STORE_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(current))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heightCm").value("171.25"))
                .andExpect(jsonPath("$.heightEstimated").value(false));
        assertEquals(false, jdbcTemplate.queryForObject(
                "SELECT height_estimated FROM customer WHERE customer_id = ?", Boolean.class, CUSTOMER_ID));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE object_type = 'CUSTOMER' AND object_id = ? AND action_code = 'CUSTOMER_MEASUREMENTS_REVIEWED'",
                Integer.class, CUSTOMER_ID));
    }

    private MvcResult submit(
            MockHttpSession session,
            boolean confirmDuplicate,
            org.springframework.test.web.servlet.ResultMatcher expectedStatus) throws Exception {
        return submit(session, STORE_ID, confirmDuplicate, expectedStatus);
    }

    private MvcResult submit(
            MockHttpSession session,
            long storeId,
            boolean confirmDuplicate,
            org.springframework.test.web.servlet.ResultMatcher expectedStatus) throws Exception {
        AuthSession csrf = csrf(session);
        return mockMvc.perform(post("/api/customer/requests")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceStoreId":"%d",
                                  "rentalStartDate":"2026-12-20",
                                  "rentalDays":3,
                                  "skillLevel":"INTERMEDIATE",
                                  "items":[{"equipmentCategory":"SKI","quantity":2,"fittingNote":"Keep input"}],
                                  "confirmDuplicate":%s
                                }
                                """.formatted(storeId, confirmDuplicate)))
                .andExpect(expectedStatus)
                .andReturn();
    }

    private AuthSession login(String loginName) throws Exception {
        MockHttpSession session = new MockHttpSession();
        AuthSession csrf = csrf(session);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginName":"%s","password":"customer test password"}
                                """.formatted(loginName)))
                .andExpect(status().isOk())
                .andReturn();
        return new AuthSession((MockHttpSession) result.getRequest().getSession(false), csrf.cookie(), csrf.token());
    }

    private AuthSession csrf(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return new AuthSession(session, result.getResponse().getCookie("XSRF-TOKEN"), body.get("token").asText());
    }

    private void insertCustomer(long id, String number, String email) {
        jdbcTemplate.update(
                """
                INSERT INTO customer (
                    customer_id, customer_number, full_name, email, phone, address, birth_year,
                    height_cm, weight_kg, boot_size,
                    height_estimated, weight_estimated, boot_size_estimated
                ) VALUES (?, ?, ?, ?, '+47000026202', 'Test Address', 1992,
                    170.00, 70.00, 42.0, FALSE, FALSE, FALSE)
                """,
                id,
                number,
                "Same Name",
                email);
    }

    private void insertAccount(long id, String loginName, long customerId, long grantId) {
        jdbcTemplate.update(
                "INSERT INTO account (account_id, login_name, password_hash, enabled, customer_id, employee_id, contractor_id) VALUES (?, ?, ?, TRUE, ?, NULL, NULL)",
                id,
                loginName,
                passwordEncoder.encode("customer test password"),
                customerId);
        jdbcTemplate.update(
                "INSERT INTO role_grant (role_grant_id, account_id, role_code, store_id, valid_from, valid_to) VALUES (?, ?, 'CUSTOMER', NULL, '2020-01-01 00:00:00', NULL)",
                grantId,
                id);
    }

    private record AuthSession(MockHttpSession session, Cookie cookie, String token) {
    }
}
