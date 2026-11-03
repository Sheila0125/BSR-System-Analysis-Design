-- Isolated T07 fixture; real integration tests only.
INSERT INTO store(store_id,store_number,store_name,business_status,opening_time,closing_time)
VALUES(37001,'S-T07-01','T07 Source','ACTIVE','08:00:00','22:00:00'),
      (37002,'S-T07-02','T07 Destination','ACTIVE','08:00:00','22:00:00');

INSERT INTO employee(employee_id,employee_number,full_name,email,business_status)
VALUES(37003,'E-T07-M','T07 Manager','t07-manager@test.invalid','ACTIVE'),
      (37005,'E-T07-S','T07 Source Worker','t07-source@test.invalid','ACTIVE'),
      (37007,'E-T07-D','T07 Destination Worker','t07-destination@test.invalid','ACTIVE');

INSERT INTO account(account_id,login_name,password_hash,enabled,employee_id)
VALUES(37004,'t07-manager','{noop}unused',TRUE,37003),
      (37006,'t07-source','{noop}unused',TRUE,37005),
      (37008,'t07-destination','{noop}unused',TRUE,37007);

INSERT INTO role_grant(account_id,role_code,store_id,valid_from)
VALUES(37004,'MANAGER',37001,'2020-01-01 00:00:00'),
      (37004,'MANAGER',37002,'2020-01-01 00:00:00'),
      (37006,'EMPLOYEE',37001,'2020-01-01 00:00:00'),
      (37008,'EMPLOYEE',37002,'2020-01-01 00:00:00');

INSERT INTO equipment(equipment_id,inventory_number,equipment_name,equipment_category,
    skill_level,home_store_id,current_store_id,equipment_status,purchase_date,current_daily_rate)
VALUES(37009,'INV-T07-01','T07 Ski A','SKI','INTERMEDIATE',37001,37001,'AVAILABLE','2025-01-01',100.00),
      (37010,'INV-T07-02','T07 Ski B','SKI','INTERMEDIATE',37001,37001,'AVAILABLE','2025-01-01',110.00),
      (37011,'INV-T07-03','T07 Ski C','SKI','INTERMEDIATE',37001,37001,'AVAILABLE','2025-01-01',120.00);
