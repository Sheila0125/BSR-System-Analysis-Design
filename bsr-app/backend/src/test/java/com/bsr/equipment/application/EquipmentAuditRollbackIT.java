package com.bsr.equipment.application;

import static com.bsr.equipment.application.EquipmentTestData.ACCOUNT_ID;
import static com.bsr.equipment.application.EquipmentTestData.EMPLOYEE_ID;
import static com.bsr.equipment.application.EquipmentTestData.RENTAL_ITEM_ID;
import static com.bsr.equipment.application.EquipmentTestData.RETURN_EQUIPMENT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

import com.bsr.audit.api.AuditAppender;
import com.bsr.equipment.api.InspectionOperations;
import com.bsr.equipment.domain.InspectionResult;
import com.bsr.identity.api.ActiveRoleGrant;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.IdentityType;
import com.bsr.identity.api.RoleCode;
import com.bsr.schema.IsolatedTestDatabaseInitializer;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
    "spring.profiles.active=test",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@ContextConfiguration(initializers = IsolatedTestDatabaseInitializer.class)
class EquipmentAuditRollbackIT {

    @Autowired
    private InspectionOperations inspectionOperations;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private AuditAppender auditAppender;

    @BeforeEach
    void insertEquipmentFixture() {
        EquipmentTestData.cleanup(jdbcTemplate);
        EquipmentTestData.insert(jdbcTemplate);
    }

    @AfterEach
    void removeEquipmentFixture() {
        EquipmentTestData.cleanup(jdbcTemplate);
    }

    @Test
    @DisplayName("AC-BR40 inspection and equipment transition roll back when success audit fails")
    void AC_BR40_inspectionAuditFailureRollsBackFactAndState() {
        doThrow(new IllegalStateException("simulated audit persistence failure"))
                .when(auditAppender)
                .appendSuccess(any(), anyString(), anyLong(), anyString(), anyString());

        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> inspectionOperations.inspectReturn(
                        employee(),
                        RENTAL_ITEM_ID,
                        RETURN_EQUIPMENT_ID,
                        InspectionResult.FAULT,
                        "binding fault")));

        assertEquals("RETURN_INSPECTION", jdbcTemplate.queryForObject(
                "SELECT equipment_status FROM equipment WHERE equipment_id = ?",
                String.class,
                RETURN_EQUIPMENT_ID));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM equipment_inspection WHERE equipment_id = ?",
                Integer.class,
                RETURN_EQUIPMENT_ID));
    }

    private ActorContext employee() {
        return new ActorContext(
                ACCOUNT_ID,
                "equipment-test-employee",
                IdentityType.EMPLOYEE,
                EMPLOYEE_ID,
                List.of(new ActiveRoleGrant(RoleCode.EMPLOYEE, EquipmentTestData.RETURN_STORE_ID)));
    }
}
