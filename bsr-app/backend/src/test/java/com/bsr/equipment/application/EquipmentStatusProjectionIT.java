package com.bsr.equipment.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bsr.equipment.api.EquipmentStatusProjection;
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
class EquipmentStatusProjectionIT {
    @Autowired EquipmentStatusProjection statuses;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("AC-UC16 equipment status counts use current store, include unavailable items, and exclude other stores")
    void currentStoreStatusCounts() {
        jdbc.update("INSERT INTO store(store_id,store_number,store_name,business_status,opening_time,closing_time) VALUES "
                + "(59101,'S-OP-E1','Equipment one','ACTIVE','08:00:00','22:00:00'),"
                + "(59102,'S-OP-E2','Equipment two','ACTIVE','08:00:00','22:00:00')");
        equipment(59111, 59101, 59101, "AVAILABLE");
        equipment(59112, 59102, 59101, "WAITING_REPAIR");
        equipment(59113, 59101, 59101, "WAITING_REPAIR");
        equipment(59114, 59101, 59102, "RENTED");

        assertEquals(Map.of("AVAILABLE", 1L, "WAITING_REPAIR", 2L), statuses.countsAtStore(59101));
        assertEquals(Map.of("RENTED", 1L), statuses.countsAtStore(59102));
        assertEquals(Map.of(), statuses.countsAtStore(59103));
    }

    private void equipment(long id, long homeStoreId, long currentStoreId, String status) {
        jdbc.update("INSERT INTO equipment(equipment_id,inventory_number,equipment_name,equipment_category,skill_level,home_store_id,current_store_id,equipment_status,current_daily_rate) VALUES(?,?,'Test ski','SKI','INTERMEDIATE',?,?,?,50.00)",
                id, "INV-OP-" + id, homeStoreId, currentStoreId, status);
    }
}
