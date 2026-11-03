package com.bsr.rental.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bsr.rental.api.RentalBlockingProjection;
import com.bsr.rental.api.RentalStatusProjection;
import com.bsr.schema.IsolatedTestDatabaseInitializer;
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
class RentalBlockingProjectionIT {
    @Autowired RentalBlockingProjection blockers;
    @Autowired RentalStatusProjection statuses;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("AC-BR38 unfinished contracts block only their service store; ENDED and CANCELLED do not")
    void unfinishedContractsAreScopedAndTerminalStatesExcluded() {
        fixture();
        contract(59011, 59001, "DRAFT");
        contract(59012, 59001, "PENDING_CONFIRMATION");
        contract(59013, 59001, "PENDING_PAYMENT");
        contract(59014, 59001, "PENDING_ISSUE");
        contract(59015, 59001, "ACTIVE");
        contract(59016, 59001, "PENDING_SETTLEMENT");
        contract(59017, 59001, "ENDED");
        contract(59018, 59001, "CANCELLED");
        contract(59019, 59002, "ACTIVE");

        assertEquals(6, blockers.unfinishedAtStore(59001).size());
        assertEquals(java.util.List.of(59011L, 59012L, 59013L, 59014L, 59015L, 59016L),
                blockers.unfinishedAtStore(59001).stream().map(b -> b.contractId()).toList());
        assertEquals(1, blockers.unfinishedAtStore(59002).size());
        assertEquals(0, blockers.unfinishedAtStore(59003).size());
    }

    @Test
    @DisplayName("AC-UC16 rental status counts include terminal states only for the requested service store")
    void rentalStatusCountsAreScopedToServiceStore() {
        fixture();
        contract(59011, 59001, "DRAFT");
        contract(59012, 59001, "ACTIVE");
        contract(59013, 59001, "ENDED");
        contract(59014, 59001, "ENDED");
        contract(59015, 59002, "ACTIVE");

        assertEquals(java.util.Map.of("DRAFT", 1L, "ACTIVE", 1L, "ENDED", 2L),
                statuses.countsAtStore(59001));
        assertEquals(java.util.Map.of("ACTIVE", 1L), statuses.countsAtStore(59002));
        assertEquals(java.util.Map.of(), statuses.countsAtStore(59003));
    }

    private void fixture() {
        jdbc.update("INSERT INTO store(store_id,store_number,store_name,business_status,opening_time,closing_time) VALUES(59001,'S-T09-R1','Rental blocker one','ACTIVE','08:00:00','22:00:00'),(59002,'S-T09-R2','Rental blocker two','ACTIVE','08:00:00','22:00:00')");
        jdbc.update("INSERT INTO customer(customer_id,customer_number,full_name,email,phone) VALUES(59004,'C-T09-R','Blocker customer','blocker-c@test.invalid','+47000059004')");
        jdbc.update("INSERT INTO employee(employee_id,employee_number,full_name,email,business_status) VALUES(59005,'E-T09-R','Blocker employee','blocker-e@test.invalid','ACTIVE')");
    }

    private void contract(long id, long storeId, String status) {
        jdbc.update("INSERT INTO rental_contract(contract_id,contract_number,customer_id,created_by_employee_id,service_store_id,contract_date,rental_start_date,rental_days,amount_due,contract_status) VALUES(?,?,?,?,?,'2026-10-14','2026-10-20',3,0.00,?)",
                id, "RC-T09-" + id, 59004, 59005, storeId, status);
    }
}
