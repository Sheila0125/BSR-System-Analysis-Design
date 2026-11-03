-- Deterministic, isolated T06 integration fixture. Loaded only by repair tests.
INSERT INTO store(store_id,store_number,store_name,business_status,opening_time,closing_time)
VALUES(36001,'S-REPAIR-01','Repair Test Store','ACTIVE','08:00:00','22:00:00');

INSERT INTO employee(employee_id,employee_number,full_name,email,business_status)
VALUES(36002,'E-REPAIR-01','Repair Test Employee','repair-employee@test.invalid','ACTIVE');

INSERT INTO repair_contractor(contractor_id,contractor_number,contractor_name,email,business_status)
VALUES(36005,'RC-REPAIR-01','Repair Contractor A','repair-a@test.invalid','ACTIVE'),
      (36006,'RC-REPAIR-02','Repair Contractor B','repair-b@test.invalid','ACTIVE');

INSERT INTO account(account_id,login_name,password_hash,enabled,employee_id)
VALUES(36003,'repair-employee','{noop}unused',TRUE,36002);

INSERT INTO account(account_id,login_name,password_hash,enabled,contractor_id)
VALUES(36007,'repair-contractor-a','{noop}unused',TRUE,36005),
      (36008,'repair-contractor-b','{noop}unused',TRUE,36006);

INSERT INTO equipment(equipment_id,inventory_number,equipment_name,equipment_category,
    skill_level,home_store_id,current_store_id,equipment_status,purchase_date,current_daily_rate)
VALUES(36004,'INV-REPAIR-01','Repair Test Ski','SKI','INTERMEDIATE',36001,36001,
       'WAITING_REPAIR','2025-01-01',100.00);
