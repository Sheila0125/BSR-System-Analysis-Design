package com.bsr.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bsr.bootstrap.security.BsrAccountPrincipal;
import com.bsr.schema.IsolatedTestDatabaseInitializer;
import com.bsr.storetransfer.transfer.application.TransferTestData;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
class OperationsApiIT {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void fixture() { TransferTestData.insert(jdbc); }

    @Test
    @DisplayName("AC-UC16 manager sees five existing state summaries without personal or transaction details")
    void managerSeesScopedMinimalStatusFacts() throws Exception {
        jdbc.update("INSERT INTO customer(customer_id,customer_number,full_name,email,phone) VALUES(57012,'C-T09-OP','Operations customer','operations@test.invalid','+47000057012')");
        jdbc.update("INSERT INTO rental_contract(contract_id,contract_number,customer_id,created_by_employee_id,service_store_id,contract_date,rental_start_date,rental_days,amount_due,contract_status) VALUES(57033,'RC-T09-OP',57012,37005,37001,'2026-10-14','2026-10-20',2,200.00,'DRAFT')");
        jdbc.update("INSERT INTO rental_item(rental_item_id,contract_id,equipment_id,daily_rate_snapshot,item_status) VALUES(57034,57033,37011,100.00,'PENDING_ISSUE')");
        jdbc.update("UPDATE equipment SET equipment_status='PENDING_ISSUE' WHERE equipment_id=37011");
        jdbc.update("INSERT INTO repair_task(repair_task_id,repair_task_number,equipment_id,requested_by_employee_id,service_store_id,request_date,fault_description,task_status) VALUES(57035,'RT-T09-OP',37010,37005,37001,'2026-10-14','Binding failed','UNASSIGNED')");
        jdbc.update("UPDATE equipment SET equipment_status='WAITING_REPAIR' WHERE equipment_id=37010");
        jdbc.update("INSERT INTO equipment_transfer(transfer_id,transfer_number,transfer_type,source_store_id,destination_store_id,initiated_by_employee_id,initiated_at,transfer_status) VALUES(57036,'TR-T09-OP','TEMPORARY',37001,37002,37003,'2026-10-14 09:00:00','INITIATED')");
        jdbc.update("INSERT INTO employee_shift(shift_id,shift_number,shift_date,store_id,employee_id,start_time,end_time,shift_status,changed_by_employee_id,changed_at) VALUES(57037,'SH-T09-OP',?,37001,37005,'08:00:00','16:00:00','ACTIVE',37003,'2026-10-14 09:00:00')",
                LocalDate.now(ZoneId.of("Europe/Oslo")).plusDays(1));

        String body = mvc.perform(get("/api/operations").param("storeId", "37001").with(manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storeId").value("37001"))
                .andExpect(jsonPath("$.rentalStatuses.DRAFT").value(1))
                .andExpect(jsonPath("$.equipmentStatuses.AVAILABLE").value(1))
                .andExpect(jsonPath("$.equipmentStatuses.PENDING_ISSUE").value(1))
                .andExpect(jsonPath("$.equipmentStatuses.WAITING_REPAIR").value(1))
                .andExpect(jsonPath("$.repairStatuses.UNASSIGNED").value(1))
                .andExpect(jsonPath("$.transferStatuses.INITIATED").value(1))
                .andExpect(jsonPath("$.shiftStatuses.ACTIVE").value(1))
                .andReturn().getResponse().getContentAsString();
        for (String forbidden : new String[]{"customerId", "employeeId", "contractorId",
                "equipmentId", "amountDue", "transactionId", "fullName", "email"}) {
            assertFalse(body.contains(forbidden), forbidden + " must not leave the operations endpoint");
        }
    }

    @Test
    @DisplayName("AC-UC16 anonymous employee cross-store and revoked manager cannot enumerate operations")
    void explicitManagerStoreGrantIsRecheckedEachRequest() throws Exception {
        mvc.perform(get("/api/operations").param("storeId", "37001"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/operations").param("storeId", "37001").with(employee()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/operations").param("storeId", "invalid").with(manager()))
                .andExpect(status().isBadRequest());
        jdbc.update("DELETE FROM role_grant WHERE account_id=37004 AND store_id=37002");
        mvc.perform(get("/api/operations").param("storeId", "37002").with(manager()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/operations").param("storeId", "37001").with(manager()))
                .andExpect(status().isOk());
        jdbc.update("DELETE FROM role_grant WHERE account_id=37004 AND store_id=37001");
        mvc.perform(get("/api/operations").param("storeId", "37001").with(manager()))
                .andExpect(status().isUnauthorized());
    }

    private RequestPostProcessor manager() { return actor(37004L, "t07-manager"); }
    private RequestPostProcessor employee() { return actor(37006L, "t07-source"); }
    private RequestPostProcessor actor(long id, String login) {
        var principal = new BsrAccountPrincipal(id, login, "{noop}unused", true);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                principal.getAuthorities()));
    }
}
