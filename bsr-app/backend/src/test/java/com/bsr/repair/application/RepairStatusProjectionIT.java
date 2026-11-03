package com.bsr.repair.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bsr.repair.api.RepairStatusProjection;
import com.bsr.schema.IsolatedTestDatabaseInitializer;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {"spring.profiles.active=test", "spring.jpa.hibernate.ddl-auto=validate"})
@ContextConfiguration(initializers = IsolatedTestDatabaseInitializer.class)
@Transactional
class RepairStatusProjectionIT {
    @Autowired RepairStatusProjection statuses;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("AC-UC16 repair status counts use task service store and include completed history")
    void repairTaskStatusCountsAreScoped() {
        jdbc.update("INSERT INTO store(store_id,store_number,store_name,business_status,opening_time,closing_time) VALUES "
                + "(59201,'S-OP-R1','Repair one','ACTIVE','08:00:00','22:00:00'),"
                + "(59202,'S-OP-R2','Repair two','ACTIVE','08:00:00','22:00:00')");
        jdbc.update("INSERT INTO employee(employee_id,employee_number,full_name,email,business_status) VALUES(59203,'E-OP-R','Repair employee','repair-op@test.invalid','ACTIVE')");
        jdbc.update("INSERT INTO equipment(equipment_id,inventory_number,equipment_name,equipment_category,skill_level,home_store_id,current_store_id,equipment_status,current_daily_rate) VALUES "
                + "(59211,'INV-OP-R1','Repair ski','SKI','INTERMEDIATE',59201,59201,'WAITING_REPAIR',50.00),"
                + "(59212,'INV-OP-R2','Repair ski','SKI','INTERMEDIATE',59202,59202,'AVAILABLE',50.00)");
        task(59221, 59211, 59201, "PENDING_CHARGE");
        task(59222, 59211, 59201, "COMPLETED");
        task(59223, 59212, 59202, "UNASSIGNED");

        assertEquals(Map.of("PENDING_CHARGE", 1L, "COMPLETED", 1L), statuses.countsAtStore(59201));
        assertEquals(Map.of("UNASSIGNED", 1L), statuses.countsAtStore(59202));
        assertEquals(Map.of(), statuses.countsAtStore(59204));
    }

    private void task(long id, long equipmentId, long storeId, String status) {
        jdbc.update("INSERT INTO repair_task(repair_task_id,repair_task_number,equipment_id,requested_by_employee_id,service_store_id,request_date,fault_description,task_status) VALUES(?,?,?,59203,?,'2026-10-14','Test fault',?)",
                id, "RT-OP-" + id, equipmentId, storeId, status);
    }
}
