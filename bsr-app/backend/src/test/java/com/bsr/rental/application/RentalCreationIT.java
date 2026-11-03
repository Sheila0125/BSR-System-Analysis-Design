package com.bsr.rental.application;

import static org.junit.jupiter.api.Assertions.*;

import com.bsr.identity.api.ActiveRoleGrant;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.IdentityType;
import com.bsr.identity.api.RoleCode;
import com.bsr.platform.error.ApiException;
import com.bsr.rental.domain.RentalContractStatus;
import com.bsr.schema.IsolatedTestDatabaseInitializer;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.time.LocalDateTime;
import com.bsr.rental.domain.ExternalTransactionResult;
import com.bsr.equipment.api.InspectionOutcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.MediaType;
import jakarta.servlet.http.Cookie;
import tools.jackson.databind.ObjectMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@SpringBootTest(properties = {"spring.profiles.active=test", "spring.jpa.hibernate.ddl-auto=validate"})
@ContextConfiguration(initializers = IsolatedTestDatabaseInitializer.class)
@AutoConfigureMockMvc
class RentalCreationIT {
    private static final long STORE = 31_001, CUSTOMER = 31_002, EMPLOYEE = 31_003,
            ACCOUNT = 31_004, CUSTOMER_ACCOUNT = 31_005, SKI = 31_006, BOOT = 31_007,
            REQUEST = 31_008, OTHER_REQUEST = 31_009;
    @Autowired RentalService service;
    @Autowired TransactionService transactions;
    @Autowired ChangeRequestService changes;
    @Autowired AdjustmentService adjustments;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper mapper;
    @MockitoSpyBean TransactionPersistence transactionPersistence;
    @MockitoSpyBean com.bsr.audit.api.AuditAppender auditAppender;

    @BeforeEach void setup() {
        cleanup();
        jdbc.update("INSERT INTO store(store_id,store_number,store_name,business_status,opening_time,closing_time) VALUES(?,'S-T05','Rental test store','ACTIVE','08:00:00','22:00:00')", STORE);
        jdbc.update("INSERT INTO customer(customer_id,customer_number,full_name,email,phone) VALUES(?,'C-T05','Rental test customer','t05@test.invalid','+47000031002')", CUSTOMER);
        jdbc.update("INSERT INTO employee(employee_id,employee_number,full_name,email,business_status) VALUES(?,'E-T05','Rental test employee','t05-employee@test.invalid','ACTIVE')", EMPLOYEE);
        jdbc.update("INSERT INTO account(account_id,login_name,password_hash,enabled,employee_id) VALUES(?,'t05-employee','{noop}unused',TRUE,?)", ACCOUNT, EMPLOYEE);
        jdbc.update("INSERT INTO account(account_id,login_name,password_hash,enabled,customer_id) VALUES(?,'t05-customer','{noop}unused',TRUE,?)", CUSTOMER_ACCOUNT, CUSTOMER);
        jdbc.update("INSERT INTO role_grant(account_id,role_code,store_id,valid_from) VALUES(?,'EMPLOYEE',?,'2020-01-01 00:00:00'),(?,'CUSTOMER',NULL,'2020-01-01 00:00:00')",ACCOUNT,STORE,CUSTOMER_ACCOUNT);
        jdbc.update("""
                INSERT INTO equipment(equipment_id,inventory_number,equipment_name,equipment_category,
                    skill_level,home_store_id,current_store_id,equipment_status,purchase_date,purchase_price,current_daily_rate)
                VALUES(?,'INV-T05-S','Test ski','SKI','INTERMEDIATE',?,?,'AVAILABLE','2025-01-01',900.00,99.50),
                      (?,'INV-T05-B','Test boot','BOOT','INTERMEDIATE',?,?,'AVAILABLE','2025-01-01',400.00,40.00)
                """, SKI, STORE, STORE, BOOT, STORE, STORE);
        for (long id : List.of(REQUEST, OTHER_REQUEST)) {
            jdbc.update("""
                    INSERT INTO rental_request(request_id,request_number,customer_id,service_store_id,
                        accepted_by_employee_id,rental_start_date,rental_days,skill_level,submitted_at,request_status)
                    VALUES(?,?,?,?,?,'2026-12-20',3,'INTERMEDIATE','2026-10-09 09:00:00','ACCEPTED')
                    """, id, "RR-T05-" + id, CUSTOMER, STORE, EMPLOYEE);
            jdbc.update("INSERT INTO rental_request_item(request_id,equipment_category,quantity) VALUES(?,'SKI',1),(?,'BOOT',1)", id, id);
        }
    }
    @AfterEach void teardown() { cleanup(); }

    @Test @DisplayName("AC-UC04/BR10 refused draft can replace a pending item and reconfirm without erasing history")
    void draftRevisionKeepsHistoryAndReconfirms() {
        var c=service.create(employee(),STORE,REQUEST,selection());
        service.submit(employee(),c.id()); service.decide(customer(),c.id(),false);
        insertAdditionalSki("120.00");
        var old=c.items().stream().filter(i -> i.equipmentId()==SKI).findFirst().orElseThrow();
        var revised=service.revise(employee(),c.id(),List.of(old.id()),List.of(new RentalService.Selection(31016,new BigDecimal("120.00"))));
        assertEquals(new BigDecimal("480.00"),revised.contract().amountDue());
        assertEquals(3,revised.items().size());
        var oldHistory=revised.items().stream().filter(i -> i.id()==old.id()).findFirst().orElseThrow();
        assertFalse(oldHistory.active()); assertEquals("PENDING_ISSUE",oldHistory.status());
        assertNull(oldHistory.checkedOutAt()); assertNull(oldHistory.returnedAt());
        assertEquals("AVAILABLE",jdbc.queryForObject("SELECT equipment_status FROM equipment WHERE equipment_id=?",String.class,SKI));
        assertEquals(new BigDecimal("61.50"),changes.forContract(customer(),c.id()).getFirst().amountDelta());
        service.submit(employee(),c.id()); service.decide(customer(),c.id(),true);
        assertEquals(new BigDecimal("480.00"),service.read(customer(),c.id()).charges().getFirst().amount());
        assertThrows(IllegalStateException.class,()->service.revise(employee(),c.id(),List.of(old.id()),List.of(new RentalService.Selection(SKI,new BigDecimal("99.50")))));
    }

    private void insertAdditionalSki(String rate) {
        jdbc.update("""
                INSERT INTO equipment(equipment_id,inventory_number,equipment_name,equipment_category,
                    skill_level,home_store_id,current_store_id,equipment_status,purchase_date,purchase_price,current_daily_rate)
                VALUES(31016,'INV-T05-NEW','New ski','SKI','INTERMEDIATE',31001,31001,'AVAILABLE','2025-01-01',900.00,?)
                """,new BigDecimal(rate));
    }

    @Test @DisplayName("AC-UC05/BR19 formal future-day refund fails without reverting committed return or inspection")
    void formalRefundFailureKeepsPhysicalFacts() {
        var c=activeToday();
        var ski=c.items().stream().filter(i -> i.equipmentId()==SKI).findFirst().orElseThrow();
        var intent=changes.submit(customer(),c.id(),STORE,com.bsr.rental.domain.RentalChangeRequest.Type.REMOVE,"Formal reduction");
        adjustments.receive(employee(),intent.id(),List.of(new RentalService.ReturnItem(ski.id(),InspectionOutcome.NORMAL,null)));
        var quoted=adjustments.quote(employee(),intent.id(),List.of());
        assertEquals(new BigDecimal("-199.00"),quoted.amountDelta());
        assertTrue(service.read(customer(),c.id()).charges().stream().noneMatch(ch -> ch.type().equals("REFUND")));
        adjustments.decide(customer(),intent.id(),true);
        var adjusted=service.read(customer(),c.id());
        assertEquals(new BigDecimal("219.50"),adjusted.contract().amountDue());
        var refund=adjusted.charges().stream().filter(ch -> ch.type().equals("REFUND")).findFirst().orElseThrow();
        var failure=new ExternalTransactionResult(refund.id(),ExternalTransactionResult.Type.REFUND,"EXT-T05-REFUND-FAIL",new BigDecimal("199.00"),ExternalTransactionResult.Status.FAILED,null);
        transactions.record(employee(),failure);
        assertEquals("RETURNED",service.read(customer(),c.id()).items().stream().filter(i -> i.id()==ski.id()).findFirst().orElseThrow().status());
        assertEquals("AVAILABLE",jdbc.queryForObject("SELECT equipment_status FROM equipment WHERE equipment_id=?",String.class,SKI));
        assertEquals("PENDING_REFUND",service.read(customer(),c.id()).charges().stream().filter(ch -> ch.id()==refund.id()).findFirst().orElseThrow().status());
        service.returnItems(employee(),c.id(),STORE,List.of(new RentalService.ReturnItem(c.items().stream().filter(i -> i.equipmentId()==BOOT).findFirst().orElseThrow().id(),InspectionOutcome.NORMAL,null)));
        assertEquals(RentalContractStatus.PENDING_SETTLEMENT,service.read(customer(),c.id()).contract().status());
        transactions.record(employee(),new ExternalTransactionResult(refund.id(),ExternalTransactionResult.Type.REFUND,"EXT-T05-REFUND-OK",new BigDecimal("199.00"),ExternalTransactionResult.Status.SUCCEEDED,LocalDateTime.of(2026,10,10,2,0)));
        assertEquals(RentalContractStatus.ENDED,service.read(customer(),c.id()).contract().status());
    }

    @Test @DisplayName("AC-UC05 failed replacement quote and supplement never undo return or issue incoming equipment")
    void replacementQuoteAndPaymentFailuresKeepReturn() {
        var c=activeToday(); insertAdditionalSki("120.00");
        var ski=c.items().stream().filter(i -> i.equipmentId()==SKI).findFirst().orElseThrow();
        var intent=changes.submit(customer(),c.id(),STORE,com.bsr.rental.domain.RentalChangeRequest.Type.REPLACE,"Replace ski");
        adjustments.receive(employee(),intent.id(),List.of(new RentalService.ReturnItem(ski.id(),InspectionOutcome.FAULT,"Edge cracked")));
        assertThrows(ApiException.class,()->adjustments.quote(employee(),intent.id(),List.of(new RentalService.Selection(31016,new BigDecimal("119.00")))));
        assertEquals("WAITING_REPAIR",jdbc.queryForObject("SELECT equipment_status FROM equipment WHERE equipment_id=?",String.class,SKI));
        var quote=adjustments.quote(employee(),intent.id(),List.of(new RentalService.Selection(31016,new BigDecimal("120.00"))));
        assertEquals(new BigDecimal("161.00"),quote.amountDelta());
        var incoming=service.read(customer(),c.id()).items().stream().filter(i -> i.equipmentId()==31016).findFirst().orElseThrow();
        assertFalse(incoming.active()); assertThrows(ApiException.class,()->service.issue(employee(),c.id(),List.of(incoming.id())));
        adjustments.decide(customer(),intent.id(),true);
        var supplement=service.read(customer(),c.id()).charges().stream().filter(ch -> ch.type().equals("SUPPLEMENT")).findFirst().orElseThrow();
        transactions.record(employee(),new ExternalTransactionResult(supplement.id(),ExternalTransactionResult.Type.SUPPLEMENT,"EXT-T05-SUP-FAIL",new BigDecimal("161.00"),ExternalTransactionResult.Status.FAILED,null));
        assertThrows(ApiException.class,()->service.issue(employee(),c.id(),List.of(incoming.id())));
        assertNull(service.read(customer(),c.id()).items().stream().filter(i -> i.id()==incoming.id()).findFirst().orElseThrow().checkedOutAt());
        transactions.record(employee(),new ExternalTransactionResult(supplement.id(),ExternalTransactionResult.Type.SUPPLEMENT,"EXT-T05-SUP-OK",new BigDecimal("161.00"),ExternalTransactionResult.Status.SUCCEEDED,LocalDateTime.of(2026,10,10,2,0)));
        service.issue(employee(),c.id(),List.of(incoming.id()));
        assertEquals("RENTED",service.read(customer(),c.id()).items().stream().filter(i -> i.id()==incoming.id()).findFirst().orElseThrow().status());
    }

    private RentalPersistence.StoredContract activeToday() {
        jdbc.update("UPDATE rental_request SET rental_start_date=? WHERE request_id=?",java.time.LocalDate.now(java.time.ZoneId.of("Europe/Oslo")),REQUEST);
        var c=confirmed();
        transactions.record(employee(),result(c.charges().getFirst().id(),"EXT-T05-ACTIVE","418.50",ExternalTransactionResult.Status.SUCCEEDED));
        return service.issue(employee(),c.id(),c.items().stream().map(RentalPersistence.StoredItem::id).toList());
    }

    @Test @DisplayName("AC-UC05 customer quote rejection frees only unissued additions and never reverses prior return")
    void customerQuoteRejectionPreservesReturnAndHistory() {
        var c=activeToday(); insertAdditionalSki("120.00");
        var old=c.items().stream().filter(i -> i.equipmentId()==SKI).findFirst().orElseThrow();
        var intent=changes.submit(customer(),c.id(),STORE,com.bsr.rental.domain.RentalChangeRequest.Type.REPLACE,"Do not undo return");
        adjustments.receive(employee(),intent.id(),List.of(new RentalService.ReturnItem(old.id(),InspectionOutcome.NORMAL,null)));
        adjustments.quote(employee(),intent.id(),List.of(new RentalService.Selection(31016,new BigDecimal("120.00"))));
        adjustments.decide(customer(),intent.id(),false);
        assertEquals("REJECTED",changes.forContract(customer(),c.id()).getFirst().status());
        assertEquals("AVAILABLE",jdbc.queryForObject("SELECT equipment_status FROM equipment WHERE equipment_id=31016",String.class));
        var history=service.read(customer(),c.id());
        var incoming=history.items().stream().filter(i -> i.equipmentId()==31016).findFirst().orElseThrow();
        assertFalse(incoming.active()); assertNull(incoming.checkedOutAt()); assertNull(incoming.returnedAt());
        assertEquals("RETURNED",history.items().stream().filter(i -> i.id()==old.id()).findFirst().orElseThrow().status());
        assertEquals(1,history.charges().size());
        assertThrows(ApiException.class,()->service.issue(employee(),c.id(),List.of(incoming.id())));
    }

    @Test @DisplayName("AC-UC05 ended contract reopens only for the customer's unrefunded returned item within rental dates")
    void endedContractAllowsOneLimitedFormalRefund() {
        var contract=activeToday(); insertAdditionalSki("120.00");
        var ski=contract.items().stream().filter(i -> i.equipmentId()==SKI).findFirst().orElseThrow();
        var boot=contract.items().stream().filter(i -> i.equipmentId()==BOOT).findFirst().orElseThrow();
        var replacement=changes.submit(customer(),contract.id(),STORE,
                com.bsr.rental.domain.RentalChangeRequest.Type.REPLACE,"Try a replacement first");
        adjustments.receive(employee(),replacement.id(),List.of(new RentalService.ReturnItem(ski.id(),InspectionOutcome.NORMAL,null)));
        adjustments.quote(employee(),replacement.id(),List.of(new RentalService.Selection(31016,new BigDecimal("120.00"))));
        service.returnItems(employee(),contract.id(),STORE,List.of(new RentalService.ReturnItem(boot.id(),InspectionOutcome.NORMAL,null)));
        adjustments.decide(customer(),replacement.id(),false);

        assertEquals(RentalContractStatus.ENDED,service.read(customer(),contract.id()).contract().status());
        assertThrows(IllegalStateException.class,()->changes.submit(customer(),contract.id(),STORE,
                com.bsr.rental.domain.RentalChangeRequest.Type.ADD,"Ended contracts cannot add equipment"));
        int inspections=count("SELECT COUNT(*) FROM equipment_inspection WHERE rental_item_id="+ski.id());

        var refundRequest=changes.submit(customer(),contract.id(),STORE,
                com.bsr.rental.domain.RentalChangeRequest.Type.REMOVE,"Refund the returned ski's future whole days");
        assertEquals(RentalContractStatus.PENDING_SETTLEMENT,service.read(customer(),contract.id()).contract().status());
        assertThrows(IllegalStateException.class,()->changes.submit(customer(),contract.id(),STORE,
                com.bsr.rental.domain.RentalChangeRequest.Type.PARTIAL_RETURN,"A second limited reopen is not allowed"));

        adjustments.receive(employee(),refundRequest.id(),List.of(new RentalService.ReturnItem(ski.id(),InspectionOutcome.NORMAL,null)));
        assertEquals(inspections,count("SELECT COUNT(*) FROM equipment_inspection WHERE rental_item_id="+ski.id()));
        var quote=adjustments.quote(employee(),refundRequest.id(),List.of());
        assertEquals(new BigDecimal("-199.00"),quote.amountDelta());
        adjustments.decide(customer(),refundRequest.id(),true);

        var adjusted=service.read(customer(),contract.id());
        assertEquals(new BigDecimal("219.50"),adjusted.contract().amountDue());
        assertEquals(RentalContractStatus.PENDING_SETTLEMENT,adjusted.contract().status());
        var refund=adjusted.charges().stream().filter(c -> c.changeId()!=null && c.changeId()==refundRequest.id()).findFirst().orElseThrow();
        assertEquals("REFUND",refund.type()); assertEquals(new BigDecimal("199.00"),refund.amount());
        transactions.record(employee(),new ExternalTransactionResult(refund.id(),ExternalTransactionResult.Type.REFUND,
                "EXT-T05-ENDED-REFUND",refund.amount(),ExternalTransactionResult.Status.SUCCEEDED,LocalDateTime.of(2026,10,10,2,0)));
        assertEquals(RentalContractStatus.ENDED,service.read(customer(),contract.id()).contract().status());
    }

    @Test @DisplayName("AC-UC05 same returned item on two connections can enter only one formal processing change")
    void sameItemCannotEnterTwoConcurrentAdjustments() throws Exception {
        var contract=activeToday();
        var item=contract.items().stream().filter(i -> i.equipmentId()==SKI).findFirst().orElseThrow();
        var first=changes.submit(customer(),contract.id(),STORE,com.bsr.rental.domain.RentalChangeRequest.Type.REMOVE,"First reduction");
        var second=changes.submit(customer(),contract.id(),STORE,com.bsr.rental.domain.RentalChangeRequest.Type.REMOVE,"Second reduction");
        var ready=new CountDownLatch(2); var release=new CountDownLatch(1);
        var connections=new ConcurrentHashMap<Long,Long>();
        try(var executor=Executors.newFixedThreadPool(2)) {
            var futures=List.of(first,second).stream().map(change -> executor.submit(() -> {
                long connection=jdbc.queryForObject("SELECT CONNECTION_ID()",Long.class);
                connections.put(change.id(),connection);
                ready.countDown(); await(release);
                return adjustments.receive(employee(),change.id(),List.of(new RentalService.ReturnItem(item.id(),InspectionOutcome.NORMAL,null)));
            })).toList();
            try { await(ready); } finally { release.countDown(); }
            int successes=0,conflicts=0;
            for(var future:futures) {
                try { assertNotNull(future.get(10,TimeUnit.SECONDS)); successes++; }
                catch(ExecutionException exception) {
                    assertInstanceOf(ApiException.class,exception.getCause());
                    assertEquals("RENTAL_CHANGE_CONFLICT",((ApiException)exception.getCause()).code()); conflicts++;
                }
            }
            assertEquals(1,successes); assertEquals(1,conflicts);
            assertEquals(2,connections.values().stream().distinct().count());
        } finally { release.countDown(); }
        assertEquals(1,count("SELECT COUNT(*) FROM rental_change_item WHERE rental_item_id="+item.id()));
        assertEquals(1,count("SELECT COUNT(*) FROM equipment_inspection WHERE rental_item_id="+item.id()));
        assertEquals("RETURNED",service.read(customer(),contract.id()).items().stream().filter(i -> i.id()==item.id()).findFirst().orElseThrow().status());
    }

    @Test @DisplayName("AC-UC04 approved zero draft revision removes only ineffective fee and preserves changes and old items")
    void zeroDraftRevisionKeepsAuditAndHasNoFakeFee() {
        var c=service.create(employee(),STORE,REQUEST,selection()); insertAdditionalSki("0.00");
        jdbc.update("""
                INSERT INTO equipment(equipment_id,inventory_number,equipment_name,equipment_category,
                    skill_level,home_store_id,current_store_id,equipment_status,purchase_date,purchase_price,current_daily_rate)
                VALUES(31017,'INV-T05-ZERO-BOOT','Zero boot','BOOT','INTERMEDIATE',31001,31001,'AVAILABLE','2025-01-01',400.00,0.00)
                """);
        var revised=service.revise(employee(),c.id(),c.items().stream().map(RentalPersistence.StoredItem::id).toList(),
                List.of(new RentalService.Selection(31016,new BigDecimal("0.00")),new RentalService.Selection(31017,new BigDecimal("0.00"))));
        assertEquals(new BigDecimal("0.00"),revised.contract().amountDue()); assertTrue(revised.charges().isEmpty());
        assertEquals(4,revised.items().size()); assertEquals(2,revised.items().stream().filter(RentalPersistence.StoredItem::active).count());
        assertEquals(new BigDecimal("-418.50"),changes.forContract(customer(),c.id()).getFirst().amountDelta());
        service.submit(employee(),c.id()); service.decide(customer(),c.id(),true);
        service.issue(employee(),c.id(),revised.items().stream().filter(RentalPersistence.StoredItem::active).map(RentalPersistence.StoredItem::id).toList());
        assertEquals(0,count("SELECT COUNT(*) FROM payment_transaction WHERE recorded_by_employee_id=31003"));
    }

    @Test @DisplayName("AC-UC06 receiving-store Session gets minimum information and preserves home store")
    void crossStoreReturnUsesReceivingScopeAndMinimalHttp() throws Exception {
        var c=activeToday();
        jdbc.update("INSERT INTO store(store_id,store_number,store_name,business_status,opening_time,closing_time) VALUES(31018,'S-T05-RETURN','Receiving store','ACTIVE','08:00:00','22:00:00')");
        jdbc.update("DELETE FROM role_grant WHERE account_id=? AND store_id=?",ACCOUNT,STORE);
        jdbc.update("INSERT INTO role_grant(account_id,role_code,store_id,valid_from) VALUES(?,'EMPLOYEE',31018,'2020-01-01 00:00:00')",ACCOUNT);
        var receiving=login("t05-employee",ACCOUNT);
        mvc.perform(get("/api/contracts/"+c.id()).session(receiving.session())).andExpect(status().isForbidden());
        mvc.perform(get("/api/contracts/"+c.id()+"/return-lookup?returnStoreId=31018").session(receiving.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amountDue").doesNotExist()).andExpect(jsonPath("$.customerId").doesNotExist())
                .andExpect(jsonPath("$.items[0].dailyRateSnapshot").doesNotExist());
        var item=c.items().getFirst();
        mvc.perform(post("/api/contracts/"+c.id()+"/returns").session(receiving.session()).cookie(receiving.cookie()).header("X-XSRF-TOKEN",receiving.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"returnStoreId\":\"31018\",\"items\":[{\"itemId\":\""+item.id()+"\",\"outcome\":\"NORMAL\"}]}"))
                .andExpect(status().isOk());
        assertEquals(STORE,jdbc.queryForObject("SELECT home_store_id FROM equipment WHERE equipment_id=?",Long.class,item.equipmentId()));
        assertEquals(31018L,jdbc.queryForObject("SELECT current_store_id FROM equipment WHERE equipment_id=?",Long.class,item.equipmentId()));
    }

    @Test @DisplayName("AC-UC05/06 formal refund uses actual receiving store for employee financial scope")
    void formalRefundBelongsToHandlingStoreRatherThanHomeStore() {
        var contract=activeToday();
        jdbc.update("INSERT INTO store(store_id,store_number,store_name,business_status,opening_time,closing_time) VALUES(31018,'S-T05-RETURN','Receiving store','ACTIVE','08:00:00','22:00:00')");
        var receivingActor=new ActorContext(ACCOUNT,"t05-employee",IdentityType.EMPLOYEE,EMPLOYEE,List.of(new ActiveRoleGrant(RoleCode.EMPLOYEE,31018L)));
        var ski=contract.items().stream().filter(i -> i.equipmentId()==SKI).findFirst().orElseThrow();
        var change=changes.submit(customer(),contract.id(),31018,com.bsr.rental.domain.RentalChangeRequest.Type.REMOVE,"Refund at receiving store");
        adjustments.receive(receivingActor,change.id(),List.of(new RentalService.ReturnItem(ski.id(),InspectionOutcome.NORMAL,null)));
        adjustments.quote(receivingActor,change.id(),List.of()); adjustments.decide(customer(),change.id(),true);
        var refund=service.read(customer(),contract.id()).charges().stream().filter(c -> c.type().equals("REFUND")).findFirst().orElseThrow();
        var result=new ExternalTransactionResult(refund.id(),ExternalTransactionResult.Type.REFUND,"EXT-T05-CROSS-REFUND",refund.amount(),ExternalTransactionResult.Status.SUCCEEDED,LocalDateTime.of(2026,10,10,2,0));
        assertThrows(ApiException.class,()->transactions.record(employee(),result));
        transactions.record(receivingActor,result);
        assertEquals("SETTLED",service.read(customer(),contract.id()).charges().stream().filter(c -> c.id()==refund.id()).findFirst().orElseThrow().status());
        assertEquals(STORE,jdbc.queryForObject("SELECT home_store_id FROM equipment WHERE equipment_id=?",Long.class,SKI));
        assertEquals(31018L,jdbc.queryForObject("SELECT current_store_id FROM equipment WHERE equipment_id=?",Long.class,SKI));
    }

    @Test @DisplayName("AC-WX03/UC05 employee physical return and quote require customer Session confirmation and CSRF")
    void adjustmentHttpUsesTwoTrustedSessionsAndCustomerFinalConsent() throws Exception {
        var contract=activeToday();
        var old=contract.items().stream().filter(i -> i.equipmentId()==SKI).findFirst().orElseThrow();
        var employeeSession=login("t05-employee",ACCOUNT);
        var customerSession=login("t05-customer",CUSTOMER_ACCOUNT);
        var submitted=mvc.perform(post("/api/contracts/"+contract.id()+"/change-requests")
                .session(customerSession.session()).cookie(customerSession.cookie()).header("X-XSRF-TOKEN",customerSession.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestedStoreId\":\"31001\",\"type\":\"REMOVE\",\"note\":\"Return ski\",\"requestedReturnItemIds\":[\""+old.id()+"\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requestedReturnItemIds[0]").value(Long.toString(old.id()))).andReturn();
        String changeId=mapper.readTree(submitted.getResponse().getContentAsString()).get("changeId").asText();
        assertEquals("RENTED",service.read(customer(),contract.id()).items().stream().filter(i -> i.id()==old.id()).findFirst().orElseThrow().status());
        assertEquals("RENTED",jdbc.queryForObject("SELECT equipment_status FROM equipment WHERE equipment_id=?",String.class,SKI));
        mvc.perform(get("/api/contracts/"+contract.id()+"/changes").session(customerSession.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].requestedReturnItemIds[0]").value(Long.toString(old.id())));
        mvc.perform(get("/api/rental-change-requests").param("storeId","31001").session(employeeSession.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].requestedReturnItemIds[0]").value(Long.toString(old.id())));
        mvc.perform(get("/api/rental-changes/"+changeId).session(employeeSession.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[?(@.itemId=='"+old.id()+"')].effect").value(org.hamcrest.Matchers.contains((Object)null)));
        String receive="{\"items\":[{\"itemId\":\""+old.id()+"\",\"outcome\":\"NORMAL\"}]}";
        mvc.perform(post("/api/rental-changes/"+changeId+"/receive")
                .session(customerSession.session()).cookie(customerSession.cookie()).header("X-XSRF-TOKEN",customerSession.token())
                .contentType(MediaType.APPLICATION_JSON).content(receive)).andExpect(status().isForbidden());
        mvc.perform(post("/api/rental-changes/"+changeId+"/receive")
                .session(employeeSession.session()).contentType(MediaType.APPLICATION_JSON).content(receive)).andExpect(status().isForbidden());
        mvc.perform(post("/api/rental-changes/"+changeId+"/receive")
                .session(employeeSession.session()).cookie(employeeSession.cookie()).header("X-XSRF-TOKEN",employeeSession.token())
                .contentType(MediaType.APPLICATION_JSON).content(receive)).andExpect(status().isOk());
        mvc.perform(post("/api/rental-changes/"+changeId+"/quote")
                .session(employeeSession.session()).cookie(employeeSession.cookie()).header("X-XSRF-TOKEN",employeeSession.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"selections\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.change.amountDelta").value("-199.00"));
        mvc.perform(get("/api/rental-changes/"+changeId).session(customerSession.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[?(@.effect=='RETURN')].dailyRateSnapshot").value("99.50"));
        mvc.perform(post("/api/rental-changes/"+changeId+"/confirm")
                .session(employeeSession.session()).cookie(employeeSession.cookie()).header("X-XSRF-TOKEN",employeeSession.token()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/rental-changes/"+changeId+"/confirm")
                .session(customerSession.session()).cookie(customerSession.cookie()).header("X-XSRF-TOKEN",customerSession.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.change.status").value("COMPLETED"));
        assertEquals("RETURNED",service.read(customer(),contract.id()).items().stream().filter(i -> i.id()==old.id()).findFirst().orElseThrow().status());
    }

    @Test void requestedReturnsRejectDuplicatesUnknownItemsAndAdditionIntent() throws Exception {
        var contract=activeToday();
        long item=contract.items().getFirst().id();
        insertAdditionalSki("120.00");
        jdbc.update("DELETE FROM rental_request_item WHERE request_id=? AND equipment_category='BOOT'",OTHER_REQUEST);
        var other=service.create(employee(),STORE,OTHER_REQUEST,List.of(new RentalService.Selection(31016,new BigDecimal("120.00"))));
        long foreignItem=other.items().getFirst().id();
        var session=login("t05-customer",CUSTOMER_ACCOUNT);
        int before=count("SELECT COUNT(*) FROM rental_change WHERE contract_id="+contract.id());
        for(String body:List.of(
                "{\"requestedStoreId\":\"31001\",\"type\":\"REMOVE\",\"requestedReturnItemIds\":[\""+item+"\",\""+item+"\"]}",
                "{\"requestedStoreId\":\"31001\",\"type\":\"REMOVE\",\"requestedReturnItemIds\":[\"999999999\"]}",
                "{\"requestedStoreId\":\"31001\",\"type\":\"REMOVE\",\"requestedReturnItemIds\":[\""+foreignItem+"\"]}",
                "{\"requestedStoreId\":\"31001\",\"type\":\"ADD\",\"requestedReturnItemIds\":[\""+item+"\"]}")) {
            mvc.perform(post("/api/contracts/"+contract.id()+"/change-requests").session(session.session())
                    .cookie(session.cookie()).header("X-XSRF-TOKEN",session.token()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(before,count("SELECT COUNT(*) FROM rental_change WHERE contract_id="+contract.id()));
        assertEquals("RENTED",service.read(customer(),contract.id()).items().getFirst().status());
    }

    @Test @DisplayName("R03 audit foreign-key failure rolls back return/inspection and external results independently")
    void auditFailureRollsBackOnlyCurrentStage() {
        var c=activeToday();
        var missingAccount=new ActorContext(999999,"missing-account",IdentityType.EMPLOYEE,EMPLOYEE,List.of(new ActiveRoleGrant(RoleCode.EMPLOYEE,STORE)));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->service.returnItems(missingAccount,c.id(),STORE,
                List.of(new RentalService.ReturnItem(c.items().getFirst().id(),InspectionOutcome.NORMAL,null))));
        assertEquals("RENTED",service.read(customer(),c.id()).items().getFirst().status());
        assertEquals(0,count("SELECT COUNT(*) FROM equipment_inspection WHERE equipment_id IN(31006,31007)"));
        var intent=changes.submit(customer(),c.id(),STORE,com.bsr.rental.domain.RentalChangeRequest.Type.REMOVE,"Audit boundary");
        adjustments.receive(employee(),intent.id(),List.of(new RentalService.ReturnItem(c.items().getFirst().id(),InspectionOutcome.NORMAL,null)));
        adjustments.quote(employee(),intent.id(),List.of()); adjustments.decide(customer(),intent.id(),true);
        var refund=service.read(customer(),c.id()).charges().stream().filter(ch -> ch.type().equals("REFUND")).findFirst().orElseThrow();
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->transactions.record(missingAccount,
                new ExternalTransactionResult(refund.id(),ExternalTransactionResult.Type.REFUND,"EXT-T05-AUDIT-FAIL",refund.amount(),ExternalTransactionResult.Status.SUCCEEDED,LocalDateTime.of(2026,10,10,2,0))));
        assertEquals(0,count("SELECT COUNT(*) FROM payment_transaction WHERE external_transaction_id='EXT-T05-AUDIT-FAIL'"));
        assertEquals("RETURNED",service.read(customer(),c.id()).items().getFirst().status());
    }

    @Test @DisplayName("AC-UC04/BR16 atomic contract reserves concrete equipment and freezes 418.50")
    void acUc04AtomicCreationAndSnapshots() {
        var created = service.create(employee(), STORE, REQUEST, selection());
        assertEquals(new BigDecimal("418.50"), created.contract().amountDue());
        assertEquals(RentalContractStatus.DRAFT, created.contract().status());
        assertEquals(2, created.items().size());
        assertEquals("INITIAL", created.charges().getFirst().type());
        assertEquals(new BigDecimal("418.50"), created.charges().getFirst().amount());
        assertEquals("CONVERTED", requestStatus(REQUEST));
        assertEquals(2, count("SELECT COUNT(*) FROM equipment WHERE equipment_id IN (31006,31007) AND equipment_status='PENDING_ISSUE'"));
        jdbc.update("UPDATE equipment SET current_daily_rate=125.00 WHERE equipment_id=?", SKI);
        assertEquals(new BigDecimal("418.50"), service.read(customer(), created.id()).contract().amountDue());
        assertEquals(new BigDecimal("99.50"), service.read(customer(), created.id()).items().getFirst().dailyRateSnapshot());
        assertEquals(1, count("SELECT COUNT(*) FROM audit_event WHERE actor_account_id=31004 AND action_code='RENTAL_CONTRACT_CREATED'"));
    }

    @Test @DisplayName("AC-UC04 reconfirms stale rates, validates quantities and forbids manager-only or wrong-store actors")
    void acUc04NegativesHaveNoSideEffects() {
        assertThrows(ApiException.class, () -> service.create(employee(), STORE, REQUEST,
                List.of(new RentalService.Selection(SKI, new BigDecimal("98.50")), selection().getLast())));
        assertThrows(ApiException.class, () -> service.create(employee(), STORE, REQUEST, List.of(selection().getFirst())));
        var managerOnly = new ActorContext(ACCOUNT,"t05-employee",IdentityType.EMPLOYEE,EMPLOYEE,
                List.of(new ActiveRoleGrant(RoleCode.MANAGER,STORE)));
        assertThrows(ApiException.class, () -> service.create(managerOnly, STORE, REQUEST, selection()));
        assertThrows(ApiException.class, () -> service.create(employee(), STORE + 100, REQUEST, selection()));
        assertEquals(0, contracts());
        assertEquals("ACCEPTED", requestStatus(REQUEST));
        assertEquals(2, available());
    }

    @Test @DisplayName("AC-UC04/BR40 audit failure rolls back source conversion, charges, details and reservations")
    void acUc04AuditFailureRollsBackWholeCreation() {
        var missingAccount = new ActorContext(999_31004,"missing",IdentityType.EMPLOYEE,EMPLOYEE,employee().activeGrants());
        assertThrows(RuntimeException.class, () -> service.create(missingAccount, STORE, REQUEST, selection()));
        assertEquals(0, contracts());
        assertEquals(2, available());
        assertEquals("ACCEPTED", requestStatus(REQUEST));
        assertEquals(0, count("SELECT COUNT(*) FROM audit_event WHERE actor_account_id=31004"));
    }

    @Test @DisplayName("AC-UC04 same request two connections and barrier permit exactly one committed contract")
    void acUc04SameRequestRace() throws Exception { compete(REQUEST); }

    @Test @DisplayName("AC-UC04 different requests competing for the same concrete equipment commit only once")
    void acUc04SameEquipmentRace() throws Exception { compete(OTHER_REQUEST); }

    @Test @DisplayName("AC-UC02/04 customer confirmation is owned, persistent and cancellation releases equipment")
    void acUc02ConfirmationAndCancellation() {
        var contract = service.create(employee(), STORE, REQUEST, selection());
        assertEquals(RentalContractStatus.PENDING_CONFIRMATION, service.submit(employee(), contract.id()).contract().status());
        var otherCustomer = new ActorContext(CUSTOMER_ACCOUNT,"other",IdentityType.CUSTOMER,CUSTOMER + 100,customer().activeGrants());
        assertThrows(ApiException.class, () -> service.decide(otherCustomer, contract.id(), true));
        assertEquals(RentalContractStatus.DRAFT, service.decide(customer(), contract.id(), false).contract().status());
        service.submit(employee(), contract.id());
        assertEquals(RentalContractStatus.PENDING_PAYMENT, service.decide(customer(), contract.id(), true).contract().status());
        assertThrows(RuntimeException.class, () -> service.issue(employee(), contract.id(), List.of(contract.items().getFirst().id())));
        assertEquals(RentalContractStatus.CANCELLED, service.cancel(employee(), contract.id()).contract().status());
        assertEquals(2, available());
        assertEquals(1, service.listCustomer(customer()).size());
    }

    private void compete(long competingRequest) throws Exception {
        var firstCreated = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var firstConnection = new long[1];
        var secondConnection = new long[1];
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> transaction().execute(status -> {
                firstConnection[0] = jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class);
                var result = service.create(employee(), STORE, REQUEST, selection());
                firstCreated.countDown();
                await(releaseFirst);
                return result;
            }));
            await(firstCreated);
            var second = executor.submit(() -> transaction().execute(status -> {
                secondConnection[0] = jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class);
                secondStarted.countDown();
                return service.create(employee(), STORE, competingRequest, selection());
            }));
            try {
                await(secondStarted);
                assertThrows(TimeoutException.class, () -> second.get(250, TimeUnit.MILLISECONDS));
            } finally { releaseFirst.countDown(); }
            first.get(10, TimeUnit.SECONDS);
            assertThrows(ExecutionException.class, () -> second.get(10, TimeUnit.SECONDS));
            assertNotEquals(firstConnection[0], secondConnection[0]);
        } finally { releaseFirst.countDown(); }
        assertEquals(1, contracts());
        assertEquals(2, count("SELECT COUNT(*) FROM rental_item WHERE equipment_id IN (31006,31007)"));
        assertEquals(1, count("SELECT COUNT(*) FROM rental_charge WHERE contract_id IN(SELECT contract_id FROM rental_contract WHERE customer_id=31002)"));
        assertEquals(1, count("SELECT COUNT(*) FROM rental_request WHERE request_id IN(31008,31009) AND request_status='CONVERTED'"));
        assertEquals(2, count("SELECT COUNT(*) FROM equipment WHERE equipment_id IN(31006,31007) AND equipment_status='PENDING_ISSUE'"));
        assertEquals(1, count("SELECT COUNT(*) FROM audit_event WHERE actor_account_id=31004 AND action_code='RENTAL_CONTRACT_CREATED'"));
    }

    @Test @DisplayName("AC-UC04/R03 failed or unknown results cannot issue; successful duplicate is idempotent; partial issue is factual")
    void acUc04ExternalResultsAndPartialIssue() {
        var contract=confirmed();
        long chargeId=contract.charges().getFirst().id();
        var failed=result(chargeId,"EXT-T05-FAIL","418.50",ExternalTransactionResult.Status.FAILED);
        var recorded=transactions.record(employee(),failed);
        assertEquals(recorded.id(),transactions.record(employee(),failed).id());
        assertEquals(RentalContractStatus.PENDING_PAYMENT,service.read(customer(),contract.id()).contract().status());
        assertThrows(RuntimeException.class,()->service.issue(employee(),contract.id(),List.of(contract.items().getFirst().id())));
        var unknown=result(chargeId,"EXT-T05-UNKNOWN","418.50",ExternalTransactionResult.Status.PENDING_CONFIRMATION);
        transactions.record(employee(),unknown);
        assertThrows(RuntimeException.class,()->service.issue(employee(),contract.id(),List.of(contract.items().getFirst().id())));
        var succeeded=result(chargeId,"EXT-T05-OK","418.50",ExternalTransactionResult.Status.SUCCEEDED);
        var success=transactions.record(employee(),succeeded);
        assertEquals(success.id(),transactions.record(employee(),succeeded).id());
        assertThrows(ApiException.class,()->transactions.record(employee(),result(chargeId,"EXT-T05-OK","400.00",ExternalTransactionResult.Status.SUCCEEDED)));
        assertEquals(RentalContractStatus.PENDING_ISSUE,service.read(customer(),contract.id()).contract().status());
        var issued=service.issue(employee(),contract.id(),List.of(contract.items().getFirst().id()));
        assertEquals(RentalContractStatus.ACTIVE,issued.contract().status());
        assertEquals("RENTED",issued.items().getFirst().status());
        assertNotNull(issued.items().getFirst().checkedOutAt());
        assertEquals("PENDING_ISSUE",issued.items().getLast().status());
        assertNull(issued.items().getLast().checkedOutAt());
        assertEquals(3,transactions.list(customer(),chargeId).size());
        assertThrows(ApiException.class,()->transactions.record(customer(),succeeded));
        assertThrows(IllegalStateException.class,()->service.cancel(employee(),contract.id()));
    }

    @Test @DisplayName("AC-UC06/BR16 ordinary early and duplicate return preserve physical facts without refund")
    void acUc06OrdinaryReturnAndDuplicate() {
        var contract=confirmed();
        transactions.record(employee(),result(contract.charges().getFirst().id(),"EXT-T05-RETURN","418.50",ExternalTransactionResult.Status.SUCCEEDED));
        service.issue(employee(),contract.id(),contract.items().stream().map(RentalPersistence.StoredItem::id).toList());
        var first=contract.items().getFirst();
        var returned=service.returnItems(employee(),contract.id(),STORE,List.of(new RentalService.ReturnItem(first.id(),InspectionOutcome.NORMAL,null)));
        assertEquals("ACTIVE",returned.status());
        var timestamp=returned.items().getFirst().returnedAt();
        var duplicate=service.returnItems(employee(),contract.id(),STORE,List.of(new RentalService.ReturnItem(first.id(),InspectionOutcome.FAULT,"changed attempt")));
        assertEquals(timestamp,duplicate.items().getFirst().returnedAt());
        assertEquals(1,count("SELECT COUNT(*) FROM equipment_inspection WHERE equipment_id=31006"));
        assertEquals(0,count("SELECT COUNT(*) FROM rental_charge WHERE contract_id="+contract.id()+" AND charge_type='REFUND'"));
        var last=service.returnItems(employee(),contract.id(),STORE,List.of(new RentalService.ReturnItem(contract.items().getLast().id(),InspectionOutcome.FAULT,"damaged boot")));
        assertEquals("ENDED",last.status());
        assertEquals("AVAILABLE",jdbc.queryForObject("SELECT equipment_status FROM equipment WHERE equipment_id=?",String.class,SKI));
        assertEquals("WAITING_REPAIR",jdbc.queryForObject("SELECT equipment_status FROM equipment WHERE equipment_id=?",String.class,BOOT));
        assertEquals(STORE,jdbc.queryForObject("SELECT home_store_id FROM equipment WHERE equipment_id=?",Long.class,BOOT));
    }

    private RentalPersistence.StoredContract confirmed() {
        var contract=service.create(employee(),STORE,REQUEST,selection());
        service.submit(employee(),contract.id());
        return service.decide(customer(),contract.id(),true);
    }

    @Test @DisplayName("AC-UC04 user-approved zero initial amount confirms directly to issue without fake charges/transactions")
    void acUc04ZeroAmountHasNoFakePayment() {
        jdbc.update("UPDATE equipment SET current_daily_rate=0.00 WHERE equipment_id IN(31006,31007)");
        var contract=service.create(employee(),STORE,REQUEST,List.of(new RentalService.Selection(SKI,new BigDecimal("0.00")),new RentalService.Selection(BOOT,new BigDecimal("0.00"))));
        service.submit(employee(),contract.id());
        var ready=service.decide(customer(),contract.id(),true);
        assertEquals(RentalContractStatus.PENDING_ISSUE,ready.contract().status());
        assertTrue(ready.charges().isEmpty());
        assertEquals(RentalContractStatus.ACTIVE,service.issue(employee(),contract.id(),List.of(contract.items().getFirst().id())).contract().status());
        assertEquals(0,count("SELECT COUNT(*) FROM rental_charge WHERE contract_id="+contract.id()));
    }
    @Test @DisplayName("AC-UC03 SUBMITTED survives reload with customer/store/time and no fabricated handling/equipment")
    void acUc03SubmittedIntentPersistsWithoutPhysicalEffects() {
        var contract=confirmed();
        transactions.record(employee(),result(contract.charges().getFirst().id(),"EXT-T05-CHANGE","418.50",ExternalTransactionResult.Status.SUCCEEDED));
        service.issue(employee(),contract.id(),contract.items().stream().map(RentalPersistence.StoredItem::id).toList());
        var request=changes.submit(customer(),contract.id(),STORE,com.bsr.rental.domain.RentalChangeRequest.Type.PARTIAL_RETURN,"Return one ski tomorrow");
        var loaded=changes.queue(employee(),STORE).getFirst();
        assertEquals(request.id(),loaded.id());
        assertEquals("SUBMITTED",loaded.status());
        assertEquals(CUSTOMER,loaded.customerId());
        assertNotNull(loaded.requestedAt());
        assertNull(loaded.handledByEmployeeId()); assertNull(loaded.effectiveDate());
        assertNull(loaded.amountDelta()); assertNull(loaded.changedAt());
        assertEquals(0,count("SELECT COUNT(*) FROM rental_change_item WHERE rental_change_id="+request.id()));
        assertEquals(2,count("SELECT COUNT(*) FROM equipment WHERE equipment_id IN(31006,31007) AND equipment_status='RENTED'"));
        var other=new ActorContext(CUSTOMER_ACCOUNT,"other",IdentityType.CUSTOMER,CUSTOMER+1,customer().activeGrants());
        assertThrows(ApiException.class,()->changes.submit(other,contract.id(),STORE,com.bsr.rental.domain.RentalChangeRequest.Type.ADD,null));
        assertThrows(ApiException.class,()->changes.queue(customer(),STORE));
        assertEquals(1,changes.forContract(customer(),contract.id()).size());
    }
    private ExternalTransactionResult result(long chargeId,String externalId,String amount,ExternalTransactionResult.Status status) {
        return new ExternalTransactionResult(chargeId,ExternalTransactionResult.Type.PAYMENT,externalId,
                new BigDecimal(amount),status,LocalDateTime.of(2026,10,9,10,0));
    }

    @Test @DisplayName("INV01 invoice uses saved amounts and enforces owner and store access")
    void invoiceUsesSavedAmountsAndAuthorizesEveryRead() throws Exception {
        var c=confirmed(); var customerSession=login("t05-customer",CUSTOMER_ACCOUNT);
        jdbc.update("UPDATE equipment SET current_daily_rate=999.00 WHERE equipment_id=?",SKI);
        mvc.perform(get("/api/contracts/"+c.id()+"/invoice").session(customerSession.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceNumber").value("INV-"+c.contract().contractNumber()))
                .andExpect(jsonPath("$.documentType").value("COURSE_DEMO_INVOICE"))
                .andExpect(jsonPath("$.currency").value("NOK"))
                .andExpect(jsonPath("$.amountDue").value("418.50"))
                .andExpect(jsonPath("$.totalPaid").value("0.00"))
                .andExpect(jsonPath("$.outstandingPayment").value("418.50"))
                .andExpect(jsonPath("$.outstandingRefund").value("0.00"))
                .andExpect(jsonPath("$.items[0].dailyRateSnapshot").value("99.50"))
                .andExpect(jsonPath("$.charges[0].canSimulatePayment").value(true));
        mvc.perform(get("/api/contracts/"+c.id()+"/invoice")).andExpect(status().isUnauthorized());
        jdbc.update("UPDATE rental_contract SET customer_id=10001 WHERE contract_id=?",c.id());
        try {
            mvc.perform(get("/api/contracts/"+c.id()+"/invoice").session(customerSession.session())).andExpect(status().isForbidden());
        } finally { jdbc.update("UPDATE rental_contract SET customer_id=? WHERE contract_id=?",CUSTOMER,c.id()); }
        var employeeSession=login("t05-employee",ACCOUNT);
        mvc.perform(get("/api/contracts/"+c.id()+"/invoice").session(employeeSession.session())).andExpect(status().isOk());
        jdbc.update("INSERT INTO store(store_id,store_number,store_name,business_status,opening_time,closing_time) VALUES(31018,'S-INV-OTHER','Other invoice store','ACTIVE','08:00:00','22:00:00')");
        jdbc.update("UPDATE role_grant SET store_id=31018 WHERE account_id=?",ACCOUNT);
        var otherStoreSession=login("t05-employee",ACCOUNT);
        mvc.perform(get("/api/contracts/"+c.id()+"/invoice").session(otherStoreSession.session())).andExpect(status().isForbidden());
        jdbc.update("DELETE FROM role_grant WHERE account_id=?",ACCOUNT);
        mvc.perform(get("/api/contracts/"+c.id()+"/invoice").session(otherStoreSession.session())).andExpect(status().isUnauthorized());
    }

    @Test @DisplayName("PAY01 failed simulation retries; success settles exact server amount and replays without a second audit")
    void simulationFailureRetryAndIdempotencyAreDurable() throws Exception {
        var c=confirmed(); long charge=c.charges().getFirst().id(); var customerSession=login("t05-customer",CUSTOMER_ACCOUNT);
        String failureKey=java.util.UUID.randomUUID().toString(), successKey=java.util.UUID.randomUUID().toString();
        simulate(customerSession,charge,failureKey,"FAILED").andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("SIMULATED"))
                .andExpect(jsonPath("$.simulatedByAccountId").value(Long.toString(CUSTOMER_ACCOUNT)))
                .andExpect(jsonPath("$.recordedByEmployeeId").doesNotExist());
        assertEquals(RentalContractStatus.PENDING_PAYMENT,service.read(customer(),c.id()).contract().status());
        mvc.perform(get("/api/contracts/"+c.id()+"/invoice").session(customerSession.session()))
                .andExpect(jsonPath("$.totalPaid").value("0.00")).andExpect(jsonPath("$.outstandingPayment").value("418.50"));
        String first=simulate(customerSession,charge,successKey,"SUCCEEDED").andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value("418.50")).andReturn().getResponse().getContentAsString();
        String repeat=simulate(customerSession,charge,successKey,"SUCCEEDED").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(mapper.readTree(first),mapper.readTree(repeat));
        assertEquals(mapper.readTree(first).get("processedAt").asText(),jdbc.queryForObject("SELECT DATE_FORMAT(processed_at,'%Y-%m-%dT%H:%i:%sZ') FROM payment_transaction WHERE external_transaction_id=?",String.class,"SIM-"+successKey));
        assertEquals(mapper.readTree(first).get("recordedAt").asText(),jdbc.queryForObject("SELECT DATE_FORMAT(recorded_at,'%Y-%m-%dT%H:%i:%sZ') FROM payment_transaction WHERE external_transaction_id=?",String.class,"SIM-"+successKey));
        simulate(customerSession,charge,successKey,"FAILED").andExpect(status().isConflict());
        simulate(customerSession,charge,java.util.UUID.randomUUID().toString(),"SUCCEEDED").andExpect(status().isConflict());
        assertEquals(RentalContractStatus.PENDING_ISSUE,service.read(customer(),c.id()).contract().status());
        assertEquals(2,count("SELECT COUNT(*) FROM payment_transaction WHERE rental_charge_id="+charge));
        assertEquals(2,count("SELECT COUNT(*) FROM audit_event WHERE actor_account_id=31005 AND action_code='SIMULATED_PAYMENT_RECORDED'"));
        assertEquals(0,count("SELECT COUNT(*) FROM payment_transaction WHERE rental_charge_id="+charge+" AND recorded_by_employee_id IS NOT NULL"));
        mvc.perform(get("/api/contracts/"+c.id()+"/invoice").session(customerSession.session()))
                .andExpect(jsonPath("$.totalPaid").value("418.50"))
                .andExpect(jsonPath("$.outstandingPayment").value("0.00"))
                .andExpect(jsonPath("$.charges[0].canSimulatePayment").value(false));
        service.issue(employee(),c.id(),c.items().stream().map(RentalPersistence.StoredItem::id).toList());
        assertEquals(RentalContractStatus.ACTIVE,service.read(customer(),c.id()).contract().status());
    }

    @Test @DisplayName("PAY02 simulation checks CSRF, customer identity, ownership, confirmation, and outcome")
    void simulationRejectsWrongIdentityStageAndMalformedInput() throws Exception {
        var c=service.create(employee(),STORE,REQUEST,selection()); long charge=c.charges().getFirst().id();
        var customerSession=login("t05-customer",CUSTOMER_ACCOUNT); String key=java.util.UUID.randomUUID().toString();
        simulate(customerSession,charge,key,"SUCCEEDED").andExpect(status().isConflict());
        service.submit(employee(),c.id()); service.decide(customer(),c.id(),true);
        mvc.perform(post("/api/rental-charges/"+charge+"/simulated-payments").session(customerSession.session())
                .contentType(MediaType.APPLICATION_JSON).content(simulationBody(key,"SUCCEEDED"))).andExpect(status().isForbidden());
        simulate(customerSession,charge,"bad-key","SUCCEEDED").andExpect(status().isBadRequest());
        simulate(customerSession,charge,key,"PENDING_CONFIRMATION").andExpect(status().isBadRequest());
        var employeeSession=login("t05-employee",ACCOUNT);
        simulate(employeeSession,charge,key,"SUCCEEDED").andExpect(status().isForbidden());
        jdbc.update("UPDATE rental_contract SET customer_id=10001 WHERE contract_id=?",c.id());
        try { simulate(customerSession,charge,key,"SUCCEEDED").andExpect(status().isForbidden()); }
        finally { jdbc.update("UPDATE rental_contract SET customer_id=? WHERE contract_id=?",CUSTOMER,c.id()); }
        assertEquals(0,count("SELECT COUNT(*) FROM payment_transaction WHERE rental_charge_id="+charge));
    }

    @Test @DisplayName("PAY03 simulation settles only the remaining amount after a real partial payment")
    void simulationComputesRemainingAmountAndDoesNotTrustClientMoney() throws Exception {
        var c=confirmed(); long charge=c.charges().getFirst().id();
        transactions.record(employee(),result(charge,"EXT-PARTIAL-INVOICE","100.00",ExternalTransactionResult.Status.SUCCEEDED));
        var customerSession=login("t05-customer",CUSTOMER_ACCOUNT);
        mvc.perform(post("/api/rental-charges/"+charge+"/simulated-payments").session(customerSession.session())
                .cookie(customerSession.cookie()).header("X-XSRF-TOKEN",customerSession.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idempotencyKey\":\""+java.util.UUID.randomUUID()+"\",\"outcome\":\"SUCCEEDED\",\"amount\":\"0.01\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value("318.50"));
        mvc.perform(get("/api/contracts/"+c.id()+"/invoice").session(customerSession.session()))
                .andExpect(jsonPath("$.totalPaid").value("418.50")).andExpect(jsonPath("$.outstandingPayment").value("0.00"));
        assertEquals("EXTERNAL",jdbc.queryForObject("SELECT transaction_source FROM payment_transaction WHERE external_transaction_id='EXT-PARTIAL-INVOICE'",String.class));
    }

    @Test @DisplayName("INV02 invoice keeps refund balance distinct and customers cannot simulate refunds")
    void invoiceSeparatesRefundsAndSimulationCannotRefund() throws Exception {
        var c=activeToday(); var ski=c.items().stream().filter(i->i.equipmentId()==SKI).findFirst().orElseThrow();
        var change=changes.submit(customer(),c.id(),STORE,com.bsr.rental.domain.RentalChangeRequest.Type.REMOVE,"Invoice refund");
        adjustments.receive(employee(),change.id(),List.of(new RentalService.ReturnItem(ski.id(),InspectionOutcome.NORMAL,null)));
        adjustments.quote(employee(),change.id(),List.of()); adjustments.decide(customer(),change.id(),true);
        var refund=service.read(customer(),c.id()).charges().stream().filter(ch->ch.type().equals("REFUND")).findFirst().orElseThrow();
        var customerSession=login("t05-customer",CUSTOMER_ACCOUNT);
        simulate(customerSession,refund.id(),java.util.UUID.randomUUID().toString(),"SUCCEEDED").andExpect(status().isConflict());
        mvc.perform(get("/api/contracts/"+c.id()+"/invoice").session(customerSession.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amountDue").value("219.50"))
                .andExpect(jsonPath("$.totalPaid").value("418.50")).andExpect(jsonPath("$.totalRefunded").value("0.00"))
                .andExpect(jsonPath("$.outstandingPayment").value("0.00")).andExpect(jsonPath("$.outstandingRefund").value("199.00"));
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    @DisplayName("PAY04 two real MySQL connections settle once for same or different request keys")
    void simulationConcurrentKeysSettleOnlyOnce(boolean sameKey) throws Exception {
        var c=confirmed(); long charge=c.charges().getFirst().id(); var customerSession=login("t05-customer",CUSTOMER_ACCOUNT);
        mvc.perform(get("/api/contracts/"+c.id()+"/invoice").session(customerSession.session())).andExpect(status().isOk());
        String firstKey=java.util.UUID.randomUUID().toString(),secondKey=sameKey ? firstKey : java.util.UUID.randomUUID().toString();
        var arrived=new CountDownLatch(2); var release=new CountDownLatch(1);
        var connectionIds=ConcurrentHashMap.<Long>newKeySet();
        doAnswer(invocation->{ connectionIds.add(jdbc.queryForObject("SELECT CONNECTION_ID()",Long.class)); arrived.countDown(); await(release); return invocation.callRealMethod(); })
                .when(transactionPersistence).charge(charge);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var a=executor.submit(()->simulate(customerSession,charge,firstKey,"SUCCEEDED").andReturn().getResponse().getStatus());
            var b=executor.submit(()->simulate(customerSession,charge,secondKey,"SUCCEEDED").andReturn().getResponse().getStatus());
            try { await(arrived); } finally { release.countDown(); }
            var statuses=List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));
            if(sameKey) assertEquals(List.of(200,200),statuses);
            else { assertTrue(statuses.contains(200)); assertTrue(statuses.contains(409)); }
            assertEquals(2,connectionIds.size());
        } finally { release.countDown(); reset(transactionPersistence); }
        assertEquals(1,count("SELECT COUNT(*) FROM payment_transaction WHERE rental_charge_id="+charge));
        assertEquals(new BigDecimal("418.50"),jdbc.queryForObject("SELECT SUM(amount) FROM payment_transaction WHERE rental_charge_id="+charge,BigDecimal.class));
    }

    @Test @DisplayName("PAY05 virtual payment and settlement roll back together when audit is unavailable")
    void simulationAuditFailureRollsBackPaymentAndContract() throws Exception {
        var c=confirmed(); long charge=c.charges().getFirst().id(); var session=login("t05-customer",CUSTOMER_ACCOUNT);
        com.bsr.audit.api.AuditAppender auditSpy=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(auditAppender);
        doThrow(new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"AUDIT_UNAVAILABLE","Test audit failure"))
                .when(auditSpy).appendSuccess(any(),eq("RENTAL_CONTRACT"),eq(c.id()),eq("SIMULATED_PAYMENT_RECORDED"),anyString());
        try { simulate(session,charge,java.util.UUID.randomUUID().toString(),"SUCCEEDED").andExpect(status().isServiceUnavailable()); }
        finally { reset(auditSpy); }
        assertEquals(0,count("SELECT COUNT(*) FROM payment_transaction WHERE rental_charge_id="+charge));
        assertEquals("PENDING",service.read(customer(),c.id()).charges().getFirst().status());
        assertEquals(RentalContractStatus.PENDING_PAYMENT,service.read(customer(),c.id()).contract().status());
    }

    @Test @DisplayName("PAY06 virtual payment of an approved supplement permits issue and preserves the returned item")
    void simulationPaysApprovedSupplementWithoutReversingReturn() throws Exception {
        var c=activeToday(); insertAdditionalSki("120.00");
        var returned=c.items().stream().filter(i->i.equipmentId()==SKI).findFirst().orElseThrow();
        var change=changes.submit(customer(),c.id(),STORE,com.bsr.rental.domain.RentalChangeRequest.Type.REPLACE,"Virtual supplement");
        adjustments.receive(employee(),change.id(),List.of(new RentalService.ReturnItem(returned.id(),InspectionOutcome.NORMAL,null)));
        adjustments.quote(employee(),change.id(),List.of(new RentalService.Selection(31016,new BigDecimal("120.00"))));
        adjustments.decide(customer(),change.id(),true);
        var updated=service.read(customer(),c.id()); var charge=updated.charges().stream().filter(ch->ch.type().equals("SUPPLEMENT")).findFirst().orElseThrow();
        var session=login("t05-customer",CUSTOMER_ACCOUNT);
        simulate(session,charge.id(),java.util.UUID.randomUUID().toString(),"SUCCEEDED").andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUPPLEMENT")).andExpect(jsonPath("$.amount").value("161.00"));
        var incoming=updated.items().stream().filter(i->i.equipmentId()==31016).findFirst().orElseThrow();
        service.issue(employee(),c.id(),List.of(incoming.id()));
        assertEquals("RETURNED",service.read(customer(),c.id()).items().stream().filter(i->i.id()==returned.id()).findFirst().orElseThrow().status());
        mvc.perform(get("/api/contracts/"+c.id()+"/invoice").session(session.session())).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPaid").value("579.50")).andExpect(jsonPath("$.outstandingPayment").value("0.00"));
    }

    @Test @DisplayName("INV03 a zero-price confirmed contract has an invoice without fake payment rows")
    void invoiceZeroAmountHasNoFakePayment() throws Exception {
        jdbc.update("UPDATE equipment SET current_daily_rate=0.00 WHERE equipment_id IN(31006,31007)");
        var c=service.create(employee(),STORE,REQUEST,List.of(new RentalService.Selection(SKI,new BigDecimal("0.00")),new RentalService.Selection(BOOT,new BigDecimal("0.00"))));
        service.submit(employee(),c.id()); service.decide(customer(),c.id(),true);
        var session=login("t05-customer",CUSTOMER_ACCOUNT);
        mvc.perform(get("/api/contracts/"+c.id()+"/invoice").session(session.session())).andExpect(status().isOk())
                .andExpect(jsonPath("$.amountDue").value("0.00")).andExpect(jsonPath("$.outstandingPayment").value("0.00"))
                .andExpect(jsonPath("$.charges").isEmpty());
    }

    private org.springframework.test.web.servlet.ResultActions simulate(HttpSession session,long charge,String key,String outcome) throws Exception {
        return mvc.perform(post("/api/rental-charges/"+charge+"/simulated-payments").session(session.session())
                .cookie(session.cookie()).header("X-XSRF-TOKEN",session.token()).contentType(MediaType.APPLICATION_JSON).content(simulationBody(key,outcome)));
    }
    private String simulationBody(String key,String outcome) {
        return "{\"idempotencyKey\":\""+key+"\",\"outcome\":\""+outcome+"\"}";
    }

    @Test @DisplayName("AC-WX02/UC02/04 real Session and CSRF cross-device contract confirmation and employee-only external results")
    void acWx02HttpCrossDeviceAndScope() throws Exception {
        mvc.perform(get("/api/contracts/customer")).andExpect(status().isUnauthorized());
        var employee=login("t05-employee",ACCOUNT);
        String body="""
                {"storeId":"31001","sourceRequestId":"31008","selections":[
                  {"equipmentId":"31006","expectedDailyRate":"99.50"},
                  {"equipmentId":"31007","expectedDailyRate":"40.00"}]}
                """;
        mvc.perform(post("/api/contracts").session(employee.session()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        var created=mvc.perform(post("/api/contracts").session(employee.session()).cookie(employee.cookie())
                .header("X-XSRF-TOKEN",employee.token()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amountDue").value("418.50"))
                .andExpect(jsonPath("$.items[0].equipmentId").value("31006")).andReturn();
        String id=mapper.readTree(created.getResponse().getContentAsString()).get("contractId").asText();
        mvc.perform(post("/api/contracts/"+id+"/submit").session(employee.session()).cookie(employee.cookie()).header("X-XSRF-TOKEN",employee.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_CONFIRMATION"));
        var customer=login("t05-customer",CUSTOMER_ACCOUNT);
        mvc.perform(get("/api/contracts/customer").session(customer.session())).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].contractId").value(id)).andExpect(jsonPath("$[0].status").value("PENDING_CONFIRMATION"));
        mvc.perform(post("/api/contracts/"+id+"/confirm").session(customer.session()).cookie(customer.cookie()).header("X-XSRF-TOKEN",customer.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        String charge=mapper.readTree(created.getResponse().getContentAsString()).get("charges").get(0).get("chargeId").asText();
        String result="""
                {"type":"PAYMENT","externalId":"EXT-T05-HTTP","amount":"418.50","status":"SUCCEEDED","processedAt":"2026-10-10T01:00:00Z"}
                """;
        mvc.perform(post("/api/rental-charges/"+charge+"/transactions").session(customer.session()).cookie(customer.cookie())
                .header("X-XSRF-TOKEN",customer.token()).contentType(MediaType.APPLICATION_JSON).content(result))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACTION_NOT_AUTHORIZED"));
        mvc.perform(post("/api/rental-charges/"+charge+"/transactions").session(employee.session()).cookie(employee.cookie())
                .header("X-XSRF-TOKEN",employee.token()).contentType(MediaType.APPLICATION_JSON).content(result))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value("418.50"));
        mvc.perform(get("/api/contracts/"+id).session(customer.session())).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_ISSUE"));
        mvc.perform(post("/api/contracts/"+id+"/confirm").session(customer.session()).cookie(customer.cookie()).header("X-XSRF-TOKEN",customer.token()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RENTAL_STATE_CONFLICT"));
    }
    private record HttpSession(MockHttpSession session,Cookie cookie,String token) { }

    @Test @DisplayName("AC-UC04/R03 same external ID unique-key race across different contract locks exits rollback before comparison")
    void acR03ExternalIdentifierRaceAcrossTwoRoots() throws Exception {
        var first=confirmed();
        jdbc.update("""
                INSERT INTO equipment(equipment_id,inventory_number,equipment_name,equipment_category,
                    skill_level,home_store_id,current_store_id,equipment_status,purchase_date,purchase_price,current_daily_rate)
                VALUES(31016,'INV-T05-S2','Second ski','SKI','INTERMEDIATE',31001,31001,'AVAILABLE','2025-01-01',900.00,99.50),
                      (31017,'INV-T05-B2','Second boot','BOOT','INTERMEDIATE',31001,31001,'AVAILABLE','2025-01-01',400.00,40.00)
                """);
        var second=service.create(employee(),STORE,OTHER_REQUEST,List.of(new RentalService.Selection(31016,new BigDecimal("99.50")),new RentalService.Selection(31017,new BigDecimal("40.00"))));
        service.submit(employee(),second.id()); service.decide(customer(),second.id(),true);
        var readyToInsert=new CountDownLatch(2);
        var releaseInserts=new CountDownLatch(1);
        var connectionIds=new ConcurrentHashMap<Long,Long>();
        doAnswer(invocation -> {
            ExternalTransactionResult external=invocation.getArgument(0);
            connectionIds.put(external.chargeId(),jdbc.queryForObject("SELECT CONNECTION_ID()",Long.class));
            readyToInsert.countDown(); await(releaseInserts);
            return invocation.callRealMethod();
        }).when(transactionPersistence).insert(any(),anyLong(),any());
        try(var executor=Executors.newFixedThreadPool(2)) {
            var a=executor.submit(()->transactions.record(employee(),result(first.charges().getFirst().id(),"EXT-T05-RACE","418.50",ExternalTransactionResult.Status.SUCCEEDED)));
            var b=executor.submit(()->transactions.record(employee(),result(second.charges().getFirst().id(),"EXT-T05-RACE","418.50",ExternalTransactionResult.Status.SUCCEEDED)));
            try { await(readyToInsert); } finally { releaseInserts.countDown(); }
            int successes=0,conflicts=0;
            for(var future:List.of(a,b)) {
                try { assertNotNull(future.get(10,TimeUnit.SECONDS)); successes++; }
                catch(ExecutionException exception) {
                    assertInstanceOf(ApiException.class,exception.getCause());
                    assertEquals("EXTERNAL_TRANSACTION_CONFLICT",((ApiException)exception.getCause()).code()); conflicts++;
                }
            }
            assertEquals(1,successes); assertEquals(1,conflicts);
            assertEquals(2,connectionIds.values().stream().distinct().count());
        } finally { releaseInserts.countDown(); reset(transactionPersistence); }
        assertEquals(1,count("SELECT COUNT(*) FROM payment_transaction WHERE external_transaction_id='EXT-T05-RACE'"));
        assertEquals(1,count("SELECT COUNT(*) FROM rental_charge WHERE contract_id IN(SELECT contract_id FROM rental_contract WHERE customer_id=31002) AND charge_status='SETTLED'"));
        assertEquals(1,count("SELECT COUNT(*) FROM rental_contract WHERE customer_id=31002 AND contract_status='PENDING_ISSUE'"));
        assertEquals(1,count("SELECT COUNT(*) FROM audit_event WHERE actor_account_id=31004 AND action_code='EXTERNAL_RESULT_RECORDED'"));
    }
    private HttpSession login(String loginName,long accountId) throws Exception {
        jdbc.update("UPDATE account SET password_hash=? WHERE account_id=?",passwordEncoder.encode("T05 test password"),accountId);
        var session=new MockHttpSession();
        var csrf=mvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk()).andReturn();
        var cookie=csrf.getResponse().getCookie("XSRF-TOKEN");
        var token=mapper.readTree(csrf.getResponse().getContentAsString()).get("token").asText();
        var logged=mvc.perform(post("/api/auth/login").session(session).cookie(cookie).header("X-XSRF-TOKEN",token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"loginName\":\""+loginName+"\",\"password\":\"T05 test password\"}"))
                .andExpect(status().isOk()).andReturn();
        var authenticated=(MockHttpSession)logged.getRequest().getSession(false);
        var renewed=mvc.perform(get("/api/auth/csrf").session(authenticated)).andExpect(status().isOk()).andReturn();
        return new HttpSession(authenticated,renewed.getResponse().getCookie("XSRF-TOKEN"),mapper.readTree(renewed.getResponse().getContentAsString()).get("token").asText());
    }
    private TransactionTemplate transaction() {
        var template = new TransactionTemplate(manager);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return template;
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Rental barrier timed out"); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
    }
    private ActorContext employee() {
        return new ActorContext(ACCOUNT,"t05-employee",IdentityType.EMPLOYEE,EMPLOYEE,List.of(new ActiveRoleGrant(RoleCode.EMPLOYEE,STORE)));
    }
    private ActorContext customer() {
        return new ActorContext(CUSTOMER_ACCOUNT,"t05-customer",IdentityType.CUSTOMER,CUSTOMER,List.of(new ActiveRoleGrant(RoleCode.CUSTOMER,null)));
    }
    private List<RentalService.Selection> selection() {
        return List.of(new RentalService.Selection(SKI,new BigDecimal("99.50")),new RentalService.Selection(BOOT,new BigDecimal("40.00")));
    }
    private int count(String sql) { return jdbc.queryForObject(sql,Integer.class); }
    private int contracts() { return count("SELECT COUNT(*) FROM rental_contract WHERE customer_id=31002"); }
    private int available() { return count("SELECT COUNT(*) FROM equipment WHERE equipment_id IN(31006,31007) AND equipment_status='AVAILABLE'"); }
    private String requestStatus(long id) { return jdbc.queryForObject("SELECT request_status FROM rental_request WHERE request_id=?",String.class,id); }
    private void cleanup() {
        jdbc.update("DELETE FROM audit_event WHERE actor_account_id IN(31004,31005)");
        jdbc.update("DELETE FROM payment_transaction WHERE rental_charge_id IN(SELECT rental_charge_id FROM rental_charge WHERE contract_id IN(SELECT contract_id FROM rental_contract WHERE customer_id=31002))");
        jdbc.update("DELETE FROM rental_charge WHERE contract_id IN(SELECT contract_id FROM rental_contract WHERE customer_id=31002)");
        jdbc.update("DELETE FROM rental_change_item WHERE rental_change_id IN(SELECT rental_change_id FROM rental_change WHERE contract_id IN(SELECT contract_id FROM rental_contract WHERE customer_id=31002))");
        jdbc.update("DELETE FROM rental_change WHERE contract_id IN(SELECT contract_id FROM rental_contract WHERE customer_id=31002)");
        jdbc.update("DELETE FROM equipment_inspection WHERE equipment_id IN(31006,31007,31016,31017)");
        jdbc.update("DELETE FROM rental_item WHERE contract_id IN(SELECT contract_id FROM rental_contract WHERE customer_id=31002)");
        jdbc.update("DELETE FROM rental_contract WHERE customer_id=31002");
        jdbc.update("DELETE FROM rental_request_item WHERE request_id IN(31008,31009)");
        jdbc.update("DELETE FROM rental_request WHERE request_id IN(31008,31009)");
        jdbc.update("DELETE FROM equipment WHERE equipment_id IN(31006,31007,31016,31017)");
        jdbc.update("DELETE FROM role_grant WHERE account_id IN(31004,31005)");
        jdbc.update("DELETE FROM account WHERE account_id IN(31004,31005)");
        jdbc.update("DELETE FROM customer WHERE customer_id=31002");
        jdbc.update("DELETE FROM employee WHERE employee_id=31003");
        jdbc.update("DELETE FROM store WHERE store_id IN(31001,31018)");
    }
}
