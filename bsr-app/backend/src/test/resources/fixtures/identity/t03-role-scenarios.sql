INSERT INTO store (
    store_id, store_number, store_name, address,
    business_status, opening_time, closing_time
) VALUES
    (25001, 'S-ID-25001', 'Identity Fixture North', NULL, 'ACTIVE', '08:00:00', '22:00:00'),
    (25002, 'S-ID-25002', 'Identity Fixture South', NULL, 'ACTIVE', '08:00:00', '22:00:00');

INSERT INTO customer (
    customer_id, customer_number, full_name, email, phone,
    height_estimated, weight_estimated, boot_size_estimated
) VALUES (
    25003, 'C-ID-25003', 'Fixture Customer', 'identity-customer@test.invalid', '+47000025003',
    FALSE, FALSE, FALSE
);

INSERT INTO employee (
    employee_id, employee_number, full_name, email, phone, business_status
) VALUES
    (25004, 'E-ID-25004', 'Fixture Employee', 'identity-employee@test.invalid', NULL, 'ACTIVE'),
    (25005, 'E-ID-25005', 'Fixture Manager', 'identity-manager@test.invalid', NULL, 'ACTIVE');

INSERT INTO repair_contractor (
    contractor_id, contractor_number, contractor_name, contact_name,
    email, phone, business_status
) VALUES (
    25006, 'K-ID-25006', 'Fixture Contractor', 'Fixture Contact',
    'identity-contractor@test.invalid', NULL, 'ACTIVE'
);

INSERT INTO account (
    account_id, login_name, password_hash, enabled,
    customer_id, employee_id, contractor_id
) VALUES
    (25010, 'fixture-customer-role', '{bcrypt}$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', TRUE, 25003, NULL, NULL),
    (25011, 'fixture-employee-role', '{bcrypt}$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', TRUE, NULL, 25004, NULL),
    (25012, 'fixture-manager-role', '{bcrypt}$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', TRUE, NULL, 25005, NULL),
    (25013, 'fixture-contractor-role', '{bcrypt}$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', TRUE, NULL, NULL, 25006);

INSERT INTO role_grant (
    role_grant_id, account_id, role_code, store_id, valid_from, valid_to
) VALUES
    (25020, 25010, 'CUSTOMER', NULL, '2020-01-01 00:00:00', NULL),
    (25021, 25011, 'EMPLOYEE', 25001, '2020-01-01 00:00:00', NULL),
    (25022, 25012, 'MANAGER', 25001, '2020-01-01 00:00:00', NULL),
    (25023, 25012, 'EMPLOYEE', 25002, '2020-01-01 00:00:00', NULL),
    (25024, 25012, 'EMPLOYEE', 25001, '2020-01-01 00:00:00', '2021-01-01 00:00:00'),
    (25025, 25013, 'CONTRACTOR', NULL, '2020-01-01 00:00:00', NULL);
