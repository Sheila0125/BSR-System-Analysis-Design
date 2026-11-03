package com.bsr.equipment.application;

import org.springframework.jdbc.core.JdbcTemplate;

final class EquipmentTestData {

    static final long SOURCE_STORE_ID = 28001L;
    static final long RETURN_STORE_ID = 28002L;
    static final long EMPLOYEE_ID = 28003L;
    static final long ACCOUNT_ID = 28004L;
    static final long CUSTOMER_ID = 28005L;
    static final long REQUEST_ID = 28006L;
    static final long CONTRACT_ID = 28007L;
    static final long RETURN_EQUIPMENT_ID = 28008L;
    static final long RENTAL_ITEM_ID = 28009L;
    static final long CONCURRENT_EQUIPMENT_ID = 28010L;
    static final long CONCURRENT_RENTAL_ITEM_ID = 28011L;
    static final long CUSTOMER_ACCOUNT_ID = 28012L;
    static final long REPAIR_TASK_ID = 28014L;

    private EquipmentTestData() {
    }

    static void insert(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update(
                "INSERT INTO store (store_id, store_number, store_name, address, business_status, opening_time, closing_time) VALUES (?, 'S-EQ-01', 'Equipment Source Store', NULL, 'ACTIVE', '08:00:00', '22:00:00')",
                SOURCE_STORE_ID);
        jdbcTemplate.update(
                "INSERT INTO store (store_id, store_number, store_name, address, business_status, opening_time, closing_time) VALUES (?, 'S-EQ-02', 'Equipment Return Store', NULL, 'ACTIVE', '08:00:00', '22:00:00')",
                RETURN_STORE_ID);
        jdbcTemplate.update(
                "INSERT INTO employee (employee_id, employee_number, full_name, email, phone, business_status) VALUES (?, 'E-EQ-01', 'Equipment Test Employee', 'equipment-employee@test.invalid', NULL, 'ACTIVE')",
                EMPLOYEE_ID);
        jdbcTemplate.update(
                "INSERT INTO account (account_id, login_name, password_hash, enabled, customer_id, employee_id, contractor_id) VALUES (?, 'equipment-test-employee', '{noop}unused', TRUE, NULL, ?, NULL)",
                ACCOUNT_ID,
                EMPLOYEE_ID);
        jdbcTemplate.update(
                "INSERT INTO customer (customer_id, customer_number, full_name, email, phone, height_estimated, weight_estimated, boot_size_estimated) VALUES (?, 'C-EQ-01', 'Equipment Test Customer', 'equipment-customer@test.invalid', '+47000028005', FALSE, FALSE, FALSE)",
                CUSTOMER_ID);
        jdbcTemplate.update("INSERT INTO account(account_id,login_name,password_hash,enabled,customer_id) VALUES(?,'equipment-test-customer','{noop}unused',TRUE,?)",CUSTOMER_ACCOUNT_ID,CUSTOMER_ID);
        jdbcTemplate.update(
                """
                INSERT INTO equipment (
                    equipment_id, inventory_number, equipment_name, equipment_category, skill_level,
                    home_store_id, current_store_id, equipment_status, purchase_date,
                    purchase_price, current_daily_rate
                ) VALUES
                    (?, 'INV-EQ-RETURN', 'Returned Test Ski', 'SKI', 'INTERMEDIATE',
                     ?, ?, 'RETURN_INSPECTION', '2025-01-01', 900.00, 90.00),
                    (?, 'INV-EQ-RACE', 'Concurrent Test Ski', 'SKI', 'ADVANCED',
                     ?, ?, 'AVAILABLE', '2025-01-02', 1000.00, 100.00)
                """,
                RETURN_EQUIPMENT_ID,
                SOURCE_STORE_ID,
                RETURN_STORE_ID,
                CONCURRENT_EQUIPMENT_ID,
                SOURCE_STORE_ID,
                SOURCE_STORE_ID);
        jdbcTemplate.update(
                """
                INSERT INTO rental_request (
                    request_id, request_number, customer_id, service_store_id,
                    accepted_by_employee_id, rental_start_date, rental_days,
                    skill_level, submitted_at, request_status
                ) VALUES (?, 'RR-EQ-01', ?, ?, ?, '2026-10-01', 2,
                    'INTERMEDIATE', '2026-09-30 09:00:00', 'CONVERTED')
                """,
                REQUEST_ID,
                CUSTOMER_ID,
                SOURCE_STORE_ID,
                EMPLOYEE_ID);
        jdbcTemplate.update(
                """
                INSERT INTO rental_contract (
                    contract_id, contract_number, source_request_id, customer_id,
                    created_by_employee_id, service_store_id, contract_date,
                    rental_start_date, rental_days, amount_due, contract_status
                ) VALUES (?, 'RC-EQ-01', ?, ?, ?, ?, '2026-09-30',
                    '2026-10-01', 2, 180.00, 'PENDING_SETTLEMENT')
                """,
                CONTRACT_ID,
                REQUEST_ID,
                CUSTOMER_ID,
                EMPLOYEE_ID,
                SOURCE_STORE_ID);
        jdbcTemplate.update(
                """
                INSERT INTO rental_item (
                    rental_item_id, contract_id, equipment_id, daily_rate_snapshot,
                    checked_out_at, returned_at, return_store_id, item_status
                ) VALUES (?, ?, ?, 90.00, '2026-10-01 09:00:00',
                    '2026-10-02 16:00:00', ?, 'RETURNED')
                """,
                RENTAL_ITEM_ID,
                CONTRACT_ID,
                RETURN_EQUIPMENT_ID,
                RETURN_STORE_ID);
        jdbcTemplate.update(
                """
                INSERT INTO rental_item (
                    rental_item_id, contract_id, equipment_id, daily_rate_snapshot,
                    checked_out_at, returned_at, return_store_id, item_status
                ) VALUES (?, ?, ?, 100.00, NULL, NULL, NULL, 'PENDING_ISSUE')
                """,
                CONCURRENT_RENTAL_ITEM_ID,
                CONTRACT_ID,
                CONCURRENT_EQUIPMENT_ID);
    }

    static void cleanup(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("DELETE FROM audit_event WHERE actor_account_id = ?", ACCOUNT_ID);
        jdbcTemplate.update("DELETE FROM audit_event WHERE actor_account_id = ?", CUSTOMER_ACCOUNT_ID);
        jdbcTemplate.update("DELETE FROM rental_change_item WHERE rental_change_id=28013");
        jdbcTemplate.update("DELETE FROM rental_change WHERE rental_change_id=28013");
        jdbcTemplate.update("DELETE FROM equipment_inspection WHERE equipment_id IN (?, ?)",
                RETURN_EQUIPMENT_ID, CONCURRENT_EQUIPMENT_ID);
        jdbcTemplate.update("DELETE FROM repair_task WHERE repair_task_id = ?", REPAIR_TASK_ID);
        jdbcTemplate.update("DELETE FROM rental_item WHERE rental_item_id IN (?, ?)",
                RENTAL_ITEM_ID, CONCURRENT_RENTAL_ITEM_ID);
        jdbcTemplate.update("DELETE FROM rental_contract WHERE contract_id = ?", CONTRACT_ID);
        jdbcTemplate.update("DELETE FROM rental_request WHERE request_id = ?", REQUEST_ID);
        jdbcTemplate.update("DELETE FROM equipment WHERE equipment_id IN (?, ?)",
                RETURN_EQUIPMENT_ID, CONCURRENT_EQUIPMENT_ID);
        jdbcTemplate.update("DELETE FROM account WHERE account_id = ?", ACCOUNT_ID);
        jdbcTemplate.update("DELETE FROM account WHERE account_id = ?", CUSTOMER_ACCOUNT_ID);
        jdbcTemplate.update("DELETE FROM customer WHERE customer_id = ?", CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM employee WHERE employee_id = ?", EMPLOYEE_ID);
        jdbcTemplate.update("DELETE FROM store WHERE store_id IN (?, ?)", SOURCE_STORE_ID, RETURN_STORE_ID);
    }
}
