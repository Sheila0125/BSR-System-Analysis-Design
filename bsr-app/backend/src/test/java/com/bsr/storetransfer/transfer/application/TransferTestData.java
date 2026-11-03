package com.bsr.storetransfer.transfer.application;

import com.bsr.identity.api.ActiveRoleGrant;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.IdentityType;
import com.bsr.identity.api.RoleCode;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

public final class TransferTestData {
    static final long SOURCE = 37001L;
    static final long DESTINATION = 37002L;
    static final long MANAGER = 37003L;
    static final long MANAGER_ACCOUNT = 37004L;
    static final long SOURCE_EMPLOYEE = 37005L;
    static final long SOURCE_ACCOUNT = 37006L;
    static final long DESTINATION_EMPLOYEE = 37007L;
    static final long DESTINATION_ACCOUNT = 37008L;
    static final long EQUIPMENT_A = 37009L;
    static final long EQUIPMENT_B = 37010L;
    static final long EQUIPMENT_C = 37011L;

    private TransferTestData() {
    }

    public static void insert(JdbcTemplate jdbc) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("fixtures/storetransfer/t07-transfer.sql"));
            return null;
        });
    }

    public static void cleanup(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM audit_event WHERE actor_account_id IN (37004,37006,37008)");
        jdbc.update("DELETE FROM equipment_transfer_item WHERE transfer_id IN (SELECT transfer_id FROM equipment_transfer WHERE source_store_id=37001 AND destination_store_id=37002)");
        jdbc.update("DELETE FROM equipment_transfer WHERE source_store_id=37001 AND destination_store_id=37002");
        jdbc.update("DELETE FROM rental_item WHERE rental_item_id=37014");
        jdbc.update("DELETE FROM rental_contract WHERE contract_id=37013");
        jdbc.update("DELETE FROM role_grant WHERE account_id IN (37004,37006,37008)");
        jdbc.update("DELETE FROM equipment WHERE equipment_id IN (37009,37010,37011)");
        jdbc.update("DELETE FROM account WHERE account_id IN (37004,37006,37008)");
        jdbc.update("DELETE FROM employee WHERE employee_id IN (37003,37005,37007)");
        jdbc.update("DELETE FROM customer WHERE customer_id=37012");
        jdbc.update("DELETE FROM store WHERE store_number='S-T07-NEW'");
        jdbc.update("DELETE FROM store WHERE store_id IN (37001,37002)");
    }

    public static ActorContext manager() {
        return new ActorContext(MANAGER_ACCOUNT, "t07-manager", IdentityType.EMPLOYEE, MANAGER,
                List.of(new ActiveRoleGrant(RoleCode.MANAGER, SOURCE),
                        new ActiveRoleGrant(RoleCode.MANAGER, DESTINATION)));
    }

    public static ActorContext managerAtSourceOnly() {
        return new ActorContext(MANAGER_ACCOUNT, "t07-manager", IdentityType.EMPLOYEE, MANAGER,
                List.of(new ActiveRoleGrant(RoleCode.MANAGER, SOURCE)));
    }

    public static ActorContext sourceEmployee() {
        return new ActorContext(SOURCE_ACCOUNT, "t07-source", IdentityType.EMPLOYEE, SOURCE_EMPLOYEE,
                List.of(new ActiveRoleGrant(RoleCode.EMPLOYEE, SOURCE)));
    }

    static ActorContext destinationEmployee() {
        return new ActorContext(DESTINATION_ACCOUNT, "t07-destination", IdentityType.EMPLOYEE,
                DESTINATION_EMPLOYEE, List.of(new ActiveRoleGrant(RoleCode.EMPLOYEE, DESTINATION)));
    }
}
