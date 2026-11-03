INSERT INTO store(store_id,store_number,store_name,business_status,opening_time,closing_time)
VALUES(48001,'S-T08-01','T08 Source','ACTIVE','08:00:00','22:00:00'),
      (48002,'S-T08-02','T08 Destination','ACTIVE','08:00:00','22:00:00');

INSERT INTO employee(employee_id,employee_number,full_name,email,business_status)
VALUES(48003,'E-T08-M','T08 Manager','t08-manager@test.invalid','ACTIVE'),
      (48005,'E-T08-A','T08 Worker A','t08-a@test.invalid','ACTIVE'),
      (48007,'E-T08-B','T08 Worker B','t08-b@test.invalid','ACTIVE');

INSERT INTO account(account_id,login_name,password_hash,enabled,employee_id)
VALUES(48004,'t08-manager','{noop}unused',TRUE,48003),
      (48006,'t08-a','{noop}unused',TRUE,48005),
      (48008,'t08-b','{noop}unused',TRUE,48007);

INSERT INTO role_grant(account_id,role_code,store_id,valid_from)
VALUES(48004,'MANAGER',48001,'2020-01-01 00:00:00'),
      (48004,'MANAGER',48002,'2020-01-01 00:00:00'),
      (48006,'EMPLOYEE',48001,'2020-01-01 00:00:00'),
      (48008,'EMPLOYEE',48001,'2020-01-01 00:00:00');
