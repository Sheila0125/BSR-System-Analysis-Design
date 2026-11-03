package com.bsr.storetransfer.transfer.application;

import static com.bsr.storetransfer.transfer.application.TransferTestData.DESTINATION;
import static com.bsr.storetransfer.transfer.application.TransferTestData.EQUIPMENT_A;
import static com.bsr.storetransfer.transfer.application.TransferTestData.EQUIPMENT_B;
import static com.bsr.storetransfer.transfer.application.TransferTestData.MANAGER_ACCOUNT;
import static com.bsr.storetransfer.transfer.application.TransferTestData.SOURCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bsr.identity.api.ActorContext;
import com.bsr.platform.error.ApiException;
import com.bsr.schema.IsolatedTestDatabaseInitializer;
import com.bsr.storetransfer.transfer.domain.TransferStatus;
import com.bsr.storetransfer.transfer.domain.TransferType;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(properties = {
    "spring.profiles.active=test",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@ContextConfiguration(initializers = IsolatedTestDatabaseInitializer.class)
class TransferManagementIT {
    @Autowired private TransferService service;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        TransferTestData.cleanup(jdbc);
        TransferTestData.insert(jdbc);
    }

    @AfterEach
    void cleanup() {
        TransferTestData.cleanup(jdbc);
    }

    @Test
    @DisplayName("AC-UC11/AC-BR27/AC-BR28 same manager initiates and independently approves")
    void bothStoreManagerCanProposeAndIndependentlyApprove() {
        var started = service.initiate(TransferTestData.manager(), SOURCE, DESTINATION,
                TransferType.TEMPORARY, List.of(EQUIPMENT_A, EQUIPMENT_B));
        assertEquals(TransferStatus.INITIATED, started.aggregate().status());
        assertEquals(2, started.itemIds().size());
        assertEquals(1, auditCount("TRANSFER_INITIATED"));
        assertEquals(0, auditCount("TRANSFER_APPROVED"));

        var approved = service.approve(TransferTestData.manager(), started.id());
        assertEquals(TransferStatus.APPROVED, approved.aggregate().status());
        assertEquals(TransferTestData.MANAGER, approved.aggregate().approvedBy());
        assertEquals(1, auditCount("TRANSFER_APPROVED"));
        var firstApproval = approved.aggregate().approvedAt();
        var repeated = service.approve(TransferTestData.manager(), started.id());
        assertEquals(firstApproval, repeated.aggregate().approvedAt());
        assertEquals(1, auditCount("TRANSFER_APPROVED"));
    }

    @Test
    @DisplayName("AC-BR28 denies initiation and approval without both-store manager scope")
    void oneStoreGrantCannotProposeOrApproveTheOtherStore() {
        ApiException denied = assertThrows(ApiException.class, () -> service.initiate(
                TransferTestData.managerAtSourceOnly(), SOURCE, DESTINATION,
                TransferType.PERMANENT, List.of(EQUIPMENT_A)));
        assertEquals("ACTION_NOT_AUTHORIZED", denied.code());
        assertEquals(0, transferCount());

        var started = service.initiate(TransferTestData.manager(), SOURCE, DESTINATION,
                TransferType.PERMANENT, List.of(EQUIPMENT_A));
        assertThrows(ApiException.class,
                () -> service.approve(TransferTestData.managerAtSourceOnly(), started.id()));
        assertEquals("INITIATED", jdbc.queryForObject(
                "SELECT transfer_status FROM equipment_transfer WHERE transfer_id=?", String.class, started.id()));
        assertThrows(ApiException.class,
                () -> service.detail(TransferTestData.managerAtSourceOnly(), started.id()));
        assertEquals(0, service.list(TransferTestData.managerAtSourceOnly(), SOURCE).size());
        assertEquals(started.id(), service.detail(TransferTestData.sourceEmployee(), started.id()).id());
        assertEquals(started.id(), service.detail(TransferTestData.destinationEmployee(), started.id()).id());
    }

    @Test
    @DisplayName("AC-BR29 rejects the whole proposal and identifies unavailable equipment")
    void invalidCandidateRejectsWholeProposalAndIdentifiesId() {
        jdbc.update("UPDATE equipment SET equipment_status='RENTED' WHERE equipment_id=?", EQUIPMENT_B);
        ApiException conflict = assertThrows(ApiException.class, () -> service.initiate(
                TransferTestData.manager(), SOURCE, DESTINATION,
                TransferType.TEMPORARY, List.of(EQUIPMENT_A, EQUIPMENT_B)));
        assertEquals("TRANSFER_EQUIPMENT_UNAVAILABLE", conflict.code());
        assertEquals(Long.toString(EQUIPMENT_B), conflict.fieldErrors().get("invalidEquipmentIds"));
        assertEquals(0, transferCount());
        assertEquals(0, auditCount("TRANSFER_INITIATED"));
    }

    @Test
    @DisplayName("AC-BR27/AC-BR28 audit failure rolls back initiation and approval")
    void missingAuditAccountRollsBackProposalAndApproval() {
        ActorContext missingAccount = new ActorContext(37999L, "missing-account",
                TransferTestData.manager().identityType(), TransferTestData.MANAGER,
                TransferTestData.manager().activeGrants());
        assertThrows(RuntimeException.class, () -> service.initiate(missingAccount, SOURCE,
                DESTINATION, TransferType.TEMPORARY, List.of(EQUIPMENT_A)));
        assertEquals(0, transferCount());

        var started = service.initiate(TransferTestData.manager(), SOURCE, DESTINATION,
                TransferType.TEMPORARY, List.of(EQUIPMENT_A));
        assertThrows(RuntimeException.class, () -> service.approve(missingAccount, started.id()));
        assertEquals("INITIATED", jdbc.queryForObject(
                "SELECT transfer_status FROM equipment_transfer WHERE transfer_id=?", String.class, started.id()));
        assertEquals(0, auditCount("TRANSFER_APPROVED"));
    }

    private int transferCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM equipment_transfer WHERE source_store_id=37001 AND destination_store_id=37002", Integer.class);
    }

    private int auditCount(String action) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE actor_account_id=? AND action_code=?",
                Integer.class, MANAGER_ACCOUNT, action);
    }
}
