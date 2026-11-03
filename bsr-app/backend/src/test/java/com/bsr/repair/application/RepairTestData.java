package com.bsr.repair.application;

import com.bsr.identity.api.ActiveRoleGrant;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.IdentityType;
import com.bsr.identity.api.RoleCode;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.jdbc.core.ConnectionCallback;

public final class RepairTestData {

    public static final long STORE_ID = 36001L;
    public static final long EMPLOYEE_ID = 36002L;
    public static final long EMPLOYEE_ACCOUNT_ID = 36003L;
    public static final long EQUIPMENT_ID = 36004L;
    public static final long CONTRACTOR_ID = 36005L;
    public static final long CONTRACTOR2_ID = 36006L;
    public static final long CONTRACTOR_ACCOUNT_ID = 36007L;
    public static final long CONTRACTOR2_ACCOUNT_ID = 36008L;

    private RepairTestData() {
    }

    public static void insert(JdbcTemplate jdbc) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("fixtures/repair/t06-repair.sql"));
            return null;
        });
    }

    public static void cleanup(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM role_grant WHERE account_id IN (?,?,?)",
                EMPLOYEE_ACCOUNT_ID, CONTRACTOR_ACCOUNT_ID, CONTRACTOR2_ACCOUNT_ID);
        jdbc.update("DELETE FROM audit_event WHERE actor_account_id IN (?,?,?)",
                EMPLOYEE_ACCOUNT_ID, CONTRACTOR_ACCOUNT_ID, CONTRACTOR2_ACCOUNT_ID);
        jdbc.update("DELETE FROM equipment_inspection WHERE equipment_id=?", EQUIPMENT_ID);
        jdbc.update("""
                DELETE FROM repair_service_charge WHERE repair_task_id IN
                    (SELECT repair_task_id FROM repair_task WHERE equipment_id=?)
                """, EQUIPMENT_ID);
        jdbc.update("""
                DELETE FROM repair_labor WHERE repair_task_id IN
                    (SELECT repair_task_id FROM repair_task WHERE equipment_id=?)
                """, EQUIPMENT_ID);
        jdbc.update("DELETE FROM repair_task WHERE equipment_id=?", EQUIPMENT_ID);
        jdbc.update("DELETE FROM repair_rate WHERE contractor_id IN (?,?)", CONTRACTOR_ID, CONTRACTOR2_ID);
        jdbc.update("DELETE FROM equipment WHERE equipment_id=?", EQUIPMENT_ID);
        jdbc.update("DELETE FROM account WHERE account_id IN (?,?,?)",
                EMPLOYEE_ACCOUNT_ID, CONTRACTOR_ACCOUNT_ID, CONTRACTOR2_ACCOUNT_ID);
        jdbc.update("DELETE FROM repair_contractor WHERE contractor_id IN (?,?)", CONTRACTOR_ID, CONTRACTOR2_ID);
        jdbc.update("DELETE FROM employee WHERE employee_id=?", EMPLOYEE_ID);
        jdbc.update("DELETE FROM store WHERE store_id=?", STORE_ID);
    }

    public static ActorContext employee() {
        return new ActorContext(EMPLOYEE_ACCOUNT_ID, "repair-employee", IdentityType.EMPLOYEE,
                EMPLOYEE_ID, List.of(new ActiveRoleGrant(RoleCode.EMPLOYEE, STORE_ID)));
    }

    public static ActorContext managerOnly() {
        return new ActorContext(EMPLOYEE_ACCOUNT_ID, "repair-employee", IdentityType.EMPLOYEE,
                EMPLOYEE_ID, List.of(new ActiveRoleGrant(RoleCode.MANAGER, STORE_ID)));
    }

    public static ActorContext contractor(long contractorId) {
        long accountId = contractorId == CONTRACTOR_ID ? CONTRACTOR_ACCOUNT_ID : CONTRACTOR2_ACCOUNT_ID;
        return new ActorContext(accountId, "repair-contractor", IdentityType.CONTRACTOR,
                contractorId, List.of(new ActiveRoleGrant(RoleCode.CONTRACTOR, null)));
    }
}
