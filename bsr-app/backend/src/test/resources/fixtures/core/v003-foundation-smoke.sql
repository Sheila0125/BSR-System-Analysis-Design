INSERT INTO customer (
    customer_id, customer_number, full_name, email, phone, address,
    birth_year, height_cm, weight_kg, boot_size,
    height_estimated, weight_estimated, boot_size_estimated
) VALUES (
    10001, 'C-FIX-10001', 'Fixture Customer', 'customer@fixture.invalid', '+47000010001', NULL,
    1990, 175.00, 70.00, 42.0, FALSE, FALSE, FALSE
);

INSERT INTO employee (
    employee_id, employee_number, full_name, email, phone, business_status
) VALUES (
    10002, 'E-FIX-10002', 'Fixture Employee', 'employee@fixture.invalid', NULL, 'ACTIVE'
);

INSERT INTO repair_contractor (
    contractor_id, contractor_number, contractor_name, contact_name,
    email, phone, business_status
) VALUES (
    10003, 'K-FIX-10003', 'Fixture Repair Contractor', 'Fixture Contact',
    'contractor@fixture.invalid', NULL, 'ACTIVE'
);

INSERT INTO store (
    store_id, store_number, store_name, address,
    business_status, opening_time, closing_time
) VALUES
    (10004, 'S-FIX-10004', 'Fixture Source Store', NULL, 'ACTIVE', '08:00:00', '22:00:00'),
    (10022, 'S-FIX-10022', 'Fixture Destination Store', NULL, 'ACTIVE', '08:00:00', '22:00:00');

INSERT INTO account (
    account_id, login_name, password_hash, enabled,
    customer_id, employee_id, contractor_id
) VALUES (
    10005, 'fixture-employee', 'not-a-real-password-hash', TRUE,
    NULL, 10002, NULL
);

INSERT INTO role_grant (
    role_grant_id, account_id, role_code, store_id, valid_from, valid_to
) VALUES (
    10006, 10005, 'EMPLOYEE', 10004, '2026-01-01 00:00:00', NULL
);

INSERT INTO equipment (
    equipment_id, inventory_number, equipment_name, equipment_category,
    skill_level, home_store_id, current_store_id, equipment_status,
    purchase_date, purchase_price, current_daily_rate
) VALUES
    (10007, 'EQ-FIX-10007', 'Fixture Rental Equipment', 'SKI', 'BEGINNER',
     10004, 10004, 'AVAILABLE', '2025-01-01', 500.00, 100.00),
    (10026, 'EQ-FIX-10026', 'Fixture Transfer Equipment', 'SKI', 'INTERMEDIATE',
     10004, 10004, 'IN_TRANSFER', '2025-01-02', 600.00, 120.00),
    (10027, 'EQ-FIX-10027', 'Fixture Repair Equipment', 'BOOT', 'INTERMEDIATE',
     10004, 10004, 'IN_REPAIR', '2025-01-03', 300.00, 60.00);

INSERT INTO rental_request (
    request_id, request_number, customer_id, service_store_id,
    accepted_by_employee_id, rental_start_date, rental_days,
    skill_level, submitted_at, request_status
) VALUES (
    10008, 'RR-FIX-10008', 10001, 10004, 10002,
    '2026-02-01', 2, 'BEGINNER', '2026-01-20 10:00:00', 'CONVERTED'
);

INSERT INTO rental_request_item (
    request_item_id, request_id, equipment_category, quantity, fitting_note
) VALUES (
    10009, 10008, 'SKI', 1, 'Fixture fitting note'
);

INSERT INTO rental_contract (
    contract_id, contract_number, source_request_id, customer_id,
    created_by_employee_id, service_store_id, contract_date,
    rental_start_date, rental_days, amount_due, contract_status
) VALUES (
    10010, 'RC-FIX-10010', 10008, 10001, 10002, 10004,
    '2026-01-21', '2026-02-01', 2, 200.00, 'PENDING_SETTLEMENT'
);

INSERT INTO rental_item (
    rental_item_id, contract_id, equipment_id, daily_rate_snapshot,
    checked_out_at, returned_at, return_store_id, item_status
) VALUES (
    10011, 10010, 10007, 100.00,
    '2026-02-01 09:00:00', '2026-02-02 16:00:00', 10004, 'RETURNED'
);

INSERT INTO rental_change (
    rental_change_id, change_number, contract_id, change_type, change_reason,
    effective_date, amount_delta, handled_by_employee_id, changed_at,
    change_status, requested_by_customer_id, requested_at,
    requested_store_id, request_note
) VALUES (
    10012, 'CH-FIX-10012', 10010, 'REMOVE', 'Fixture completed return',
    '2026-02-02', 0.00, 10002, '2026-02-02 16:05:00',
    'COMPLETED', NULL, NULL, NULL, NULL
);

INSERT INTO rental_change_item (
    rental_change_item_id, rental_change_id, rental_item_id, effect_code
) VALUES (
    10013, 10012, 10011, 'RETURN'
);

INSERT INTO rental_charge (
    rental_charge_id, contract_id, rental_change_id,
    charge_type, amount, charge_status, created_at
) VALUES (
    10014, 10010, NULL, 'INITIAL', 200.00, 'SETTLED', '2026-01-21 12:00:00'
);

INSERT INTO payment_transaction (
    payment_transaction_id, rental_charge_id, transaction_type,
    external_transaction_id, amount, transaction_status,
    processed_at, recorded_by_employee_id, recorded_at
) VALUES (
    10015, 10014, 'PAYMENT', 'EXT-FIX-10015', 200.00, 'SUCCEEDED',
    '2026-01-21 12:30:00', 10002, '2026-01-21 12:31:00'
);

INSERT INTO repair_task (
    repair_task_id, repair_task_number, equipment_id,
    requested_by_employee_id, service_store_id, current_contractor_id,
    request_date, fault_description, handling_requirement, task_status,
    acceptance_result, accepted_by_employee_id, accepted_at
) VALUES (
    10016, 'RT-FIX-10016', 10027, 10002, 10004, 10003,
    '2026-02-03', 'Fixture binding fault', 'Inspect and repair', 'IN_PROGRESS',
    NULL, NULL, NULL
);

INSERT INTO equipment_inspection (
    inspection_id, equipment_id, inspected_by_employee_id,
    rental_item_id, repair_task_id, inspection_type,
    inspected_at, inspection_result, fault_description
) VALUES (
    10017, 10007, 10002, 10011, NULL, 'RETURN',
    '2026-02-02 16:10:00', 'NORMAL', NULL
);

INSERT INTO repair_rate (
    repair_rate_id, contractor_id, hourly_rate,
    effective_from, effective_to, created_at
) VALUES (
    10018, 10003, 100.00, '2026-01-01', NULL, '2026-01-01 09:00:00'
);

INSERT INTO repair_labor (
    repair_labor_id, repair_task_id, repair_date, duration_hours,
    handling_result, note, repair_rate_id, hourly_rate_snapshot,
    labor_amount, submitted_at, contractor_id
) VALUES (
    10019, 10016, '2026-02-04', 0.25,
    'Fixture inspection and adjustment', NULL, 10018, 100.00,
    25.00, '2026-02-04 10:00:00', 10003
);

INSERT INTO repair_service_charge (
    repair_service_charge_id, repair_task_id, total_hours,
    service_amount, charge_status, confirmed_by_employee_id, confirmed_at
) VALUES (
    10020, 10016, 0.25, 25.00, 'PENDING_CONFIRMATION', NULL, NULL
);

INSERT INTO equipment_transfer (
    transfer_id, transfer_number, transfer_type,
    source_store_id, destination_store_id,
    initiated_by_employee_id, approved_by_employee_id,
    initiated_at, approved_at, completed_at, transfer_status
) VALUES (
    10021, 'TR-FIX-10021', 'TEMPORARY', 10004, 10022,
    10002, NULL, '2026-02-05 09:00:00', NULL, NULL, 'INITIATED'
);

INSERT INTO equipment_transfer_item (
    transfer_item_id, transfer_id, equipment_id,
    dispatch_result, dispatched_by_employee_id, dispatched_at,
    receipt_result, received_by_employee_id, received_at, exception_note
) VALUES (
    10023, 10021, 10026,
    'PENDING', NULL, NULL, 'PENDING', NULL, NULL, NULL
);

INSERT INTO employee_shift (
    shift_id, shift_number, shift_date, store_id, employee_id,
    start_time, end_time, shift_status,
    changed_by_employee_id, changed_at, cancellation_reason
) VALUES (
    10024, 'SH-FIX-10024', '2026-02-06', 10004, 10002,
    '09:00:00', '17:00:00', 'ACTIVE', 10002, '2026-02-01 09:00:00', NULL
);

INSERT INTO audit_event (
    audit_event_id, actor_account_id, object_type, object_id,
    action_code, occurred_at, result_code, detail_summary
) VALUES (
    10025, 10005, 'RENTAL_CONTRACT', 10010,
    'FIXTURE_CREATED', '2026-02-01 09:00:00', 'SUCCESS', 'Fictional integration fixture'
);
