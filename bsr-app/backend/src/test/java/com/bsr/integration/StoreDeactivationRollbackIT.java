package com.bsr.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import com.bsr.audit.api.AuditAppender;
import com.bsr.schema.IsolatedTestDatabaseInitializer;
import com.bsr.storetransfer.coordination.api.GuardedStoreDeactivation;
import com.bsr.storetransfer.transfer.application.TransferTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {"spring.profiles.active=test", "spring.jpa.hibernate.ddl-auto=validate"})
@ContextConfiguration(initializers = IsolatedTestDatabaseInitializer.class)
class StoreDeactivationRollbackIT {
    @Autowired GuardedStoreDeactivation deactivation;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean AuditAppender audit;

    @BeforeEach void fixture() {
        TransferTestData.cleanup(jdbc);
        TransferTestData.insert(jdbc);
    }

    @AfterEach void cleanup() { TransferTestData.cleanup(jdbc); }

    @Test
    @DisplayName("AC-BR40 audit insertion failure rolls back store status in real MySQL")
    void auditFailureRollsBackDeactivation() {
        doThrow(new IllegalStateException("audit unavailable"))
                .when(audit).appendSuccess(any(), eq("STORE"), anyLong(),
                        eq("STORE_DEACTIVATED"), anyString());
        assertThrows(IllegalStateException.class,
                () -> deactivation.deactivate(TransferTestData.manager(), 37001));
        assertEquals("ACTIVE", jdbc.queryForObject(
                "SELECT business_status FROM store WHERE store_id=37001", String.class));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE object_type='STORE' AND object_id=37001 AND action_code='STORE_DEACTIVATED'",
                Integer.class));
    }
}
