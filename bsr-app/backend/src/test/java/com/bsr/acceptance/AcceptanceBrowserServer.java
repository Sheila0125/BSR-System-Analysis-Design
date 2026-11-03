package com.bsr.acceptance;

import com.bsr.BsrApplication;
import com.bsr.schema.IsolatedTestDatabaseInitializer;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Test-classpath-only launcher. Never packaged into the product jar. */
public final class AcceptanceBrowserServer {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !java.util.Set.of("seed", "keep", "clean").contains(args[0]))
            throw new IllegalArgumentException("Choose seed, keep or clean for explicitly configured TEST_DB_* only");
        var app = new SpringApplication(BsrApplication.class);
        app.addInitializers(new IsolatedTestDatabaseInitializer());
        try (ConfigurableApplicationContext context = app.run("--spring.profiles.active=test")) {
            var jdbc = context.getBean(JdbcTemplate.class);
            if (!System.getenv("TEST_DB_NAME").equals(jdbc.queryForObject("SELECT DATABASE()", String.class)))
                throw new IllegalStateException("Connected database differs from explicitly configured TEST_DB_NAME");
            if (!args[0].equals("keep")) {
                var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
                transaction.executeWithoutResult(ignored -> {
                    for (int base : new int[]{42000, 43000}) {
                        Integer collisions = jdbc.queryForObject("SELECT COUNT(*) FROM store WHERE store_id IN(?,?) AND store_number NOT IN(?,?)",
                                Integer.class, base+1,base+9,"A10-S-"+base,"A10-D-"+base);
                        if (collisions != null && collisions != 0) throw new IllegalStateException("Reserved acceptance IDs contain unrelated stores");
                        clean(jdbc, base);
                    }
                    cleanSmoke(jdbc);
                    if (!args[0].equals("clean")) {
                        String hash = context.getBean(PasswordEncoder.class).encode("acceptance-local-only");
                        for (int base : new int[]{42000, 43000}) seed(jdbc, base, hash);
                        seedSmoke(jdbc, hash);
                    }
                });
                if (args[0].equals("clean")) { System.out.println("ACCEPTANCE_FIXTURES_CLEANED"); return; }
            }
            System.out.println("ACCEPTANCE_SERVER_READY mode=" + args[0]);
            var input = new BufferedReader(new InputStreamReader(System.in));
            String signal;
            while ((signal = input.readLine()) != null && !signal.equals("STOP")) {
                System.out.println("Send STOP to close the acceptance server");
            }
        }
    }

    private static void seed(JdbcTemplate jdbc, int b, String hash) {
        jdbc.update("INSERT INTO store(store_id,store_number,store_name,business_status,opening_time,closing_time) VALUES(?,?,?,'ACTIVE','08:00','22:00'),(?,?,?,'ACTIVE','08:00','22:00')", b+1,"A10-S-"+b,"Acceptance source",b+9,"A10-D-"+b,"Acceptance destination");
        jdbc.update("INSERT INTO employee(employee_id,employee_number,full_name,email,business_status) VALUES(?,?,?,?,'ACTIVE')",b+3,"A10-E-"+b,"Fictional employee","employee"+b+"@test.invalid");
        jdbc.update("INSERT INTO customer(customer_id,customer_number,full_name,email,phone) VALUES(?,?,?,?,?)",b+2,"A10-C-"+b,"Fictional customer","customer"+b+"@test.invalid","+470000"+b);
        jdbc.update("INSERT INTO repair_contractor(contractor_id,contractor_number,contractor_name,email,business_status) VALUES(?,?,?,?,'ACTIVE')",b+10,"A10-RC-"+b,"Fictional contractor","contractor"+b+"@test.invalid");
        jdbc.update("INSERT INTO account(account_id,login_name,password_hash,enabled,employee_id) VALUES(?,?,?,TRUE,?)",b+4,"a10-employee-"+b,hash,b+3);
        jdbc.update("INSERT INTO account(account_id,login_name,password_hash,enabled,customer_id) VALUES(?,?,?,TRUE,?)",b+5,"a10-customer-"+b,hash,b+2);
        jdbc.update("INSERT INTO account(account_id,login_name,password_hash,enabled,contractor_id) VALUES(?,?,?,TRUE,?)",b+11,"a10-contractor-"+b,hash,b+10);
        for (String role : new String[]{"EMPLOYEE","MANAGER"}) {
            jdbc.update("INSERT INTO role_grant(account_id,role_code,store_id,valid_from) VALUES(?,?,?,'2020-01-01'),(?,?,?,'2020-01-01')",b+4,role,b+1,b+4,role,b+9);
        }
        jdbc.update("INSERT INTO role_grant(account_id,role_code,valid_from) VALUES(?,'CUSTOMER','2020-01-01'),(?,'CONTRACTOR','2020-01-01')",b+5,b+11);
        for (int offset : new int[]{6,7,8}) {
            jdbc.update("""
                INSERT INTO equipment(equipment_id,inventory_number,equipment_name,equipment_category,skill_level,
                    home_store_id,current_store_id,equipment_status,purchase_date,current_daily_rate)
                VALUES(?,?,?,'SKI','INTERMEDIATE',?,?,?,'2025-01-01',99.50)
                """,b+offset,"A10-INV-"+(b+offset),"Acceptance ski",b+1,b+1,offset==8?"WAITING_REPAIR":"AVAILABLE");
        }
        jdbc.update("""
            INSERT INTO repair_task(repair_task_id,repair_task_number,equipment_id,requested_by_employee_id,
                service_store_id,current_contractor_id,request_date,fault_description,task_status)
            VALUES(?,?,?,?,?,?,CURRENT_DATE,'Fictional damaged edge','PENDING')
            """,b+12,"A10-REPAIR-"+b,b+8,b+3,b+1,b+10);
    }

    private static void clean(JdbcTemplate jdbc, int b) {
        jdbc.update("DELETE FROM audit_event WHERE actor_account_id IN(?,?,?)",b+4,b+5,b+11);
        jdbc.update("DELETE FROM payment_transaction WHERE rental_charge_id IN(SELECT rental_charge_id FROM rental_charge WHERE contract_id IN(SELECT contract_id FROM rental_contract WHERE customer_id=?))",b+2);
        jdbc.update("DELETE FROM rental_charge WHERE contract_id IN(SELECT contract_id FROM rental_contract WHERE customer_id=?)",b+2);
        jdbc.update("DELETE FROM rental_change_item WHERE rental_change_id IN(SELECT rental_change_id FROM rental_change WHERE contract_id IN(SELECT contract_id FROM rental_contract WHERE customer_id=?))",b+2);
        jdbc.update("DELETE FROM rental_change WHERE contract_id IN(SELECT contract_id FROM rental_contract WHERE customer_id=?)",b+2);
        jdbc.update("DELETE FROM equipment_inspection WHERE equipment_id IN(?,?,?)",b+6,b+7,b+8);
        jdbc.update("DELETE FROM rental_item WHERE contract_id IN(SELECT contract_id FROM rental_contract WHERE customer_id=?)",b+2);
        jdbc.update("DELETE FROM rental_contract WHERE customer_id=?",b+2);
        jdbc.update("DELETE FROM rental_request_supplement WHERE request_id IN(SELECT request_id FROM rental_request WHERE customer_id=?)",b+2);
        jdbc.update("DELETE FROM rental_request_item WHERE request_id IN(SELECT request_id FROM rental_request WHERE customer_id=?)",b+2);
        jdbc.update("DELETE FROM rental_request WHERE customer_id=?",b+2);
        jdbc.update("DELETE FROM repair_service_charge WHERE repair_task_id=?",b+12);
        jdbc.update("DELETE FROM repair_labor WHERE repair_task_id=?",b+12);
        jdbc.update("DELETE FROM repair_task WHERE repair_task_id=?",b+12);
        jdbc.update("DELETE FROM repair_rate WHERE contractor_id=?",b+10);
        jdbc.update("DELETE FROM equipment_transfer_item WHERE transfer_id IN(SELECT transfer_id FROM equipment_transfer WHERE source_store_id=?)",b+1);
        jdbc.update("DELETE FROM equipment_transfer WHERE source_store_id=?",b+1);
        jdbc.update("DELETE FROM employee_shift WHERE employee_id=?",b+3);
        jdbc.update("DELETE FROM equipment WHERE equipment_id IN(?,?,?)",b+6,b+7,b+8);
        jdbc.update("DELETE FROM role_grant WHERE account_id IN(?,?,?)",b+4,b+5,b+11);
        jdbc.update("DELETE FROM account WHERE account_id IN(?,?,?)",b+4,b+5,b+11);
        jdbc.update("DELETE FROM repair_contractor WHERE contractor_id=?",b+10);
        jdbc.update("DELETE FROM customer WHERE customer_id=?",b+2);
        jdbc.update("DELETE FROM employee WHERE employee_id=?",b+3);
        jdbc.update("DELETE FROM store WHERE store_id IN(?,?)",b+1,b+9);
    }
    private static void cleanSmoke(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM audit_event WHERE actor_account_id=39903");
        jdbc.update("DELETE FROM role_grant WHERE account_id=39903");
        jdbc.update("DELETE FROM account WHERE account_id=39903");
        jdbc.update("DELETE FROM employee WHERE employee_id=39902");
        jdbc.update("DELETE FROM store WHERE store_id=39901");
    }
    private static void seedSmoke(JdbcTemplate jdbc, String hash) {
        jdbc.update("INSERT INTO store(store_id,store_number,store_name,business_status,opening_time,closing_time) VALUES(39901,'A10-SMOKE','Smoke store','ACTIVE','08:00','22:00')");
        jdbc.update("INSERT INTO employee(employee_id,employee_number,full_name,email,business_status) VALUES(39902,'A10-SMOKE-E','Smoke employee','smoke@test.invalid','ACTIVE')");
        jdbc.update("INSERT INTO account(account_id,login_name,password_hash,enabled,employee_id) VALUES(39903,'browser-t09',?,TRUE,39902)",hash);
        // Existing T09 smoke has its own documented fictional password.
        jdbc.update("UPDATE account SET password_hash=? WHERE account_id=39903",org.springframework.security.crypto.factory.PasswordEncoderFactories.createDelegatingPasswordEncoder().encode("smoke-local-only"));
        jdbc.update("INSERT INTO role_grant(account_id,role_code,store_id,valid_from) VALUES(39903,'EMPLOYEE',39901,'2020-01-01'),(39903,'MANAGER',39901,'2020-01-01')");
    }
}
