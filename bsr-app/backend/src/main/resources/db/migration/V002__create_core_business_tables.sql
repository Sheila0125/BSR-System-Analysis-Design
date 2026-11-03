-- T02 / V002: the remaining 18 business tables.
-- Final UNIQUE and CHECK constraints, business indexes, and the deferred
-- equipment_inspection.repair_task_id foreign key are added by V003.

CREATE TABLE equipment (
    equipment_id BIGINT NOT NULL AUTO_INCREMENT,
    inventory_number VARCHAR(40) NOT NULL,
    equipment_name VARCHAR(120) NOT NULL,
    equipment_category VARCHAR(40) NOT NULL,
    skill_level VARCHAR(20) NOT NULL,
    home_store_id BIGINT NOT NULL,
    current_store_id BIGINT NOT NULL,
    equipment_status VARCHAR(20) NOT NULL,
    purchase_date DATE NULL,
    purchase_price DECIMAL(12, 2) NULL,
    current_daily_rate DECIMAL(12, 2) NOT NULL,
    CONSTRAINT pk_equipment PRIMARY KEY (equipment_id),
    INDEX idx_fk_equipment_home_store_id (home_store_id),
    INDEX idx_fk_equipment_current_store_id (current_store_id),
    CONSTRAINT fk_equipment_home_store_id
        FOREIGN KEY (home_store_id) REFERENCES store (store_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_equipment_current_store_id
        FOREIGN KEY (current_store_id) REFERENCES store (store_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4;

CREATE TABLE rental_request (
    request_id BIGINT NOT NULL AUTO_INCREMENT,
    request_number VARCHAR(30) NOT NULL,
    customer_id BIGINT NOT NULL,
    service_store_id BIGINT NOT NULL,
    accepted_by_employee_id BIGINT NULL,
    rental_start_date DATE NOT NULL,
    rental_days INT NOT NULL,
    skill_level VARCHAR(20) NOT NULL,
    submitted_at DATETIME NOT NULL,
    request_status VARCHAR(20) NOT NULL,
    CONSTRAINT pk_rental_request PRIMARY KEY (request_id),
    INDEX idx_fk_rental_request_customer_id (customer_id),
    INDEX idx_fk_rental_request_service_store_id (service_store_id),
    INDEX idx_fk_rental_request_accepted_by_employee_id (accepted_by_employee_id),
    CONSTRAINT fk_rental_request_customer_id
        FOREIGN KEY (customer_id) REFERENCES customer (customer_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_rental_request_service_store_id
        FOREIGN KEY (service_store_id) REFERENCES store (store_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_rental_request_accepted_by_employee_id
        FOREIGN KEY (accepted_by_employee_id) REFERENCES employee (employee_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4;

CREATE TABLE rental_request_item (
    request_item_id BIGINT NOT NULL AUTO_INCREMENT,
    request_id BIGINT NOT NULL,
    equipment_category VARCHAR(40) NOT NULL,
    quantity INT NOT NULL,
    fitting_note VARCHAR(255) NULL,
    CONSTRAINT pk_rental_request_item PRIMARY KEY (request_item_id),
    INDEX idx_fk_rental_request_item_request_id (request_id),
    CONSTRAINT fk_rental_request_item_request_id
        FOREIGN KEY (request_id) REFERENCES rental_request (request_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4;

CREATE TABLE rental_contract (
    contract_id BIGINT NOT NULL AUTO_INCREMENT,
    contract_number VARCHAR(30) NOT NULL,
    source_request_id BIGINT NULL,
    customer_id BIGINT NOT NULL,
    created_by_employee_id BIGINT NOT NULL,
    service_store_id BIGINT NOT NULL,
    contract_date DATE NOT NULL,
    rental_start_date DATE NOT NULL,
    rental_days INT NOT NULL,
    amount_due DECIMAL(12, 2) NOT NULL,
    contract_status VARCHAR(20) NOT NULL,
    CONSTRAINT pk_rental_contract PRIMARY KEY (contract_id),
    INDEX idx_fk_rental_contract_source_request_id (source_request_id),
    INDEX idx_fk_rental_contract_customer_id (customer_id),
    INDEX idx_fk_rental_contract_created_by_employee_id (created_by_employee_id),
    INDEX idx_fk_rental_contract_service_store_id (service_store_id),
    CONSTRAINT fk_rental_contract_source_request_id
        FOREIGN KEY (source_request_id) REFERENCES rental_request (request_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_rental_contract_customer_id
        FOREIGN KEY (customer_id) REFERENCES customer (customer_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_rental_contract_created_by_employee_id
        FOREIGN KEY (created_by_employee_id) REFERENCES employee (employee_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_rental_contract_service_store_id
        FOREIGN KEY (service_store_id) REFERENCES store (store_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4;

CREATE TABLE rental_item (
    rental_item_id BIGINT NOT NULL AUTO_INCREMENT,
    contract_id BIGINT NOT NULL,
    equipment_id BIGINT NOT NULL,
    daily_rate_snapshot DECIMAL(12, 2) NOT NULL,
    checked_out_at DATETIME NULL,
    returned_at DATETIME NULL,
    return_store_id BIGINT NULL,
    item_status VARCHAR(20) NOT NULL,
    CONSTRAINT pk_rental_item PRIMARY KEY (rental_item_id),
    INDEX idx_fk_rental_item_contract_id (contract_id),
    INDEX idx_fk_rental_item_equipment_id (equipment_id),
    INDEX idx_fk_rental_item_return_store_id (return_store_id),
    CONSTRAINT fk_rental_item_contract_id
        FOREIGN KEY (contract_id) REFERENCES rental_contract (contract_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_rental_item_equipment_id
        FOREIGN KEY (equipment_id) REFERENCES equipment (equipment_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_rental_item_return_store_id
        FOREIGN KEY (return_store_id) REFERENCES store (store_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4;

CREATE TABLE rental_change (
    rental_change_id BIGINT NOT NULL AUTO_INCREMENT,
    change_number VARCHAR(30) NOT NULL,
    contract_id BIGINT NOT NULL,
    change_type VARCHAR(20) NOT NULL,
    change_reason VARCHAR(255) NULL,
    effective_date DATE NULL,
    amount_delta DECIMAL(12, 2) NULL,
    handled_by_employee_id BIGINT NULL,
    changed_at DATETIME NULL,
    change_status VARCHAR(20) NOT NULL,
    requested_by_customer_id BIGINT NULL,
    requested_at DATETIME NULL,
    requested_store_id BIGINT NULL,
    request_note VARCHAR(500) NULL,
    CONSTRAINT pk_rental_change PRIMARY KEY (rental_change_id),
    INDEX idx_fk_rental_change_contract_id (contract_id),
    INDEX idx_fk_rental_change_handled_by_employee_id (handled_by_employee_id),
    INDEX idx_fk_rental_change_requested_by_customer_id (requested_by_customer_id),
    INDEX idx_fk_rental_change_requested_store_id (requested_store_id),
    CONSTRAINT fk_rental_change_contract_id
        FOREIGN KEY (contract_id) REFERENCES rental_contract (contract_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_rental_change_handled_by_employee_id
        FOREIGN KEY (handled_by_employee_id) REFERENCES employee (employee_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_rental_change_requested_by_customer_id
        FOREIGN KEY (requested_by_customer_id) REFERENCES customer (customer_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_rental_change_requested_store_id
        FOREIGN KEY (requested_store_id) REFERENCES store (store_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4;

CREATE TABLE rental_change_item (
    rental_change_item_id BIGINT NOT NULL AUTO_INCREMENT,
    rental_change_id BIGINT NOT NULL,
    rental_item_id BIGINT NOT NULL,
    effect_code VARCHAR(20) NOT NULL,
    CONSTRAINT pk_rental_change_item PRIMARY KEY (rental_change_item_id),
    INDEX idx_fk_rental_change_item_rental_change_id (rental_change_id),
    INDEX idx_fk_rental_change_item_rental_item_id (rental_item_id),
    CONSTRAINT fk_rental_change_item_rental_change_id
        FOREIGN KEY (rental_change_id) REFERENCES rental_change (rental_change_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_rental_change_item_rental_item_id
        FOREIGN KEY (rental_item_id) REFERENCES rental_item (rental_item_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4;

CREATE TABLE rental_charge (
    rental_charge_id BIGINT NOT NULL AUTO_INCREMENT,
    contract_id BIGINT NOT NULL,
    rental_change_id BIGINT NULL,
    charge_type VARCHAR(20) NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    charge_status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT pk_rental_charge PRIMARY KEY (rental_charge_id),
    INDEX idx_fk_rental_charge_contract_id (contract_id),
    INDEX idx_fk_rental_charge_rental_change_id (rental_change_id),
    CONSTRAINT fk_rental_charge_contract_id
        FOREIGN KEY (contract_id) REFERENCES rental_contract (contract_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_rental_charge_rental_change_id
        FOREIGN KEY (rental_change_id) REFERENCES rental_change (rental_change_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4;

CREATE TABLE payment_transaction (
    payment_transaction_id BIGINT NOT NULL AUTO_INCREMENT,
    rental_charge_id BIGINT NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    external_transaction_id VARCHAR(100) NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    transaction_status VARCHAR(20) NOT NULL,
    processed_at DATETIME NULL,
    recorded_by_employee_id BIGINT NOT NULL,
    recorded_at DATETIME NOT NULL,
    CONSTRAINT pk_payment_transaction PRIMARY KEY (payment_transaction_id),
    INDEX idx_fk_payment_transaction_rental_charge_id (rental_charge_id),
    INDEX idx_fk_payment_transaction_recorded_by_employee_id (recorded_by_employee_id),
    CONSTRAINT fk_payment_transaction_rental_charge_id
        FOREIGN KEY (rental_charge_id) REFERENCES rental_charge (rental_charge_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_payment_transaction_recorded_by_employee_id
        FOREIGN KEY (recorded_by_employee_id) REFERENCES employee (employee_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4;

CREATE TABLE repair_task (
    repair_task_id BIGINT NOT NULL AUTO_INCREMENT,
    repair_task_number VARCHAR(30) NOT NULL,
    equipment_id BIGINT NOT NULL,
    requested_by_employee_id BIGINT NOT NULL,
    service_store_id BIGINT NOT NULL,
    current_contractor_id BIGINT NULL,
    request_date DATE NOT NULL,
    fault_description VARCHAR(500) NOT NULL,
    handling_requirement VARCHAR(500) NULL,
    task_status VARCHAR(30) NOT NULL,
    acceptance_result VARCHAR(20) NULL,
    accepted_by_employee_id BIGINT NULL,
    accepted_at DATETIME NULL,
    CONSTRAINT pk_repair_task PRIMARY KEY (repair_task_id),
    INDEX idx_fk_repair_task_equipment_id (equipment_id),
    INDEX idx_fk_repair_task_requested_by_employee_id (requested_by_employee_id),
    INDEX idx_fk_repair_task_service_store_id (service_store_id),
    INDEX idx_fk_repair_task_current_contractor_id (current_contractor_id),
    INDEX idx_fk_repair_task_accepted_by_employee_id (accepted_by_employee_id),
    CONSTRAINT fk_repair_task_equipment_id
        FOREIGN KEY (equipment_id) REFERENCES equipment (equipment_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_repair_task_requested_by_employee_id
        FOREIGN KEY (requested_by_employee_id) REFERENCES employee (employee_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_repair_task_service_store_id
        FOREIGN KEY (service_store_id) REFERENCES store (store_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_repair_task_current_contractor_id
        FOREIGN KEY (current_contractor_id) REFERENCES repair_contractor (contractor_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_repair_task_accepted_by_employee_id
        FOREIGN KEY (accepted_by_employee_id) REFERENCES employee (employee_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4;

CREATE TABLE equipment_inspection (
    inspection_id BIGINT NOT NULL AUTO_INCREMENT,
    equipment_id BIGINT NOT NULL,
    inspected_by_employee_id BIGINT NOT NULL,
    rental_item_id BIGINT NULL,
    repair_task_id BIGINT NULL,
    inspection_type VARCHAR(20) NOT NULL,
    inspected_at DATETIME NOT NULL,
    inspection_result VARCHAR(20) NOT NULL,
    fault_description VARCHAR(500) NULL,
    CONSTRAINT pk_equipment_inspection PRIMARY KEY (inspection_id),
    INDEX idx_fk_equipment_inspection_equipment_id (equipment_id),
    INDEX idx_fk_equipment_inspection_inspected_by_employee_id (inspected_by_employee_id),
    INDEX idx_fk_equipment_inspection_rental_item_id (rental_item_id),
    INDEX idx_fk_equipment_inspection_repair_task_id (repair_task_id),
    CONSTRAINT fk_equipment_inspection_equipment_id
        FOREIGN KEY (equipment_id) REFERENCES equipment (equipment_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_equipment_inspection_inspected_by_employee_id
        FOREIGN KEY (inspected_by_employee_id) REFERENCES employee (employee_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_equipment_inspection_rental_item_id
        FOREIGN KEY (rental_item_id) REFERENCES rental_item (rental_item_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4;

CREATE TABLE repair_rate (
    repair_rate_id BIGINT NOT NULL AUTO_INCREMENT,
    contractor_id BIGINT NOT NULL,
    hourly_rate DECIMAL(12, 2) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT pk_repair_rate PRIMARY KEY (repair_rate_id),
    INDEX idx_fk_repair_rate_contractor_id (contractor_id),
    CONSTRAINT fk_repair_rate_contractor_id
        FOREIGN KEY (contractor_id) REFERENCES repair_contractor (contractor_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4;

CREATE TABLE repair_labor (
    repair_labor_id BIGINT NOT NULL AUTO_INCREMENT,
    repair_task_id BIGINT NOT NULL,
    repair_date DATE NOT NULL,
    duration_hours DECIMAL(8, 2) NOT NULL,
    handling_result VARCHAR(500) NOT NULL,
    note VARCHAR(500) NULL,
    repair_rate_id BIGINT NULL,
    hourly_rate_snapshot DECIMAL(12, 2) NULL,
    labor_amount DECIMAL(12, 2) NULL,
    submitted_at DATETIME NULL,
    contractor_id BIGINT NOT NULL,
    CONSTRAINT pk_repair_labor PRIMARY KEY (repair_labor_id),
    INDEX idx_fk_repair_labor_repair_task_id (repair_task_id),
    INDEX idx_fk_repair_labor_repair_rate_id (repair_rate_id),
    INDEX idx_fk_repair_labor_contractor_id (contractor_id),
    CONSTRAINT fk_repair_labor_repair_task_id
        FOREIGN KEY (repair_task_id) REFERENCES repair_task (repair_task_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_repair_labor_repair_rate_id
        FOREIGN KEY (repair_rate_id) REFERENCES repair_rate (repair_rate_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_repair_labor_contractor_id
        FOREIGN KEY (contractor_id) REFERENCES repair_contractor (contractor_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4;

CREATE TABLE repair_service_charge (
    repair_service_charge_id BIGINT NOT NULL AUTO_INCREMENT,
    repair_task_id BIGINT NOT NULL,
    total_hours DECIMAL(8, 2) NOT NULL,
    service_amount DECIMAL(12, 2) NOT NULL,
    charge_status VARCHAR(20) NOT NULL,
    confirmed_by_employee_id BIGINT NULL,
    confirmed_at DATETIME NULL,
    CONSTRAINT pk_repair_service_charge PRIMARY KEY (repair_service_charge_id),
    INDEX idx_fk_repair_service_charge_repair_task_id (repair_task_id),
    INDEX idx_fk_repair_service_charge_confirmed_by_employee_id (confirmed_by_employee_id),
    CONSTRAINT fk_repair_service_charge_repair_task_id
        FOREIGN KEY (repair_task_id) REFERENCES repair_task (repair_task_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_repair_service_charge_confirmed_by_employee_id
        FOREIGN KEY (confirmed_by_employee_id) REFERENCES employee (employee_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4;

CREATE TABLE equipment_transfer (
    transfer_id BIGINT NOT NULL AUTO_INCREMENT,
    transfer_number VARCHAR(30) NOT NULL,
    transfer_type VARCHAR(20) NOT NULL,
    source_store_id BIGINT NOT NULL,
    destination_store_id BIGINT NOT NULL,
    initiated_by_employee_id BIGINT NOT NULL,
    approved_by_employee_id BIGINT NULL,
    initiated_at DATETIME NOT NULL,
    approved_at DATETIME NULL,
    completed_at DATETIME NULL,
    transfer_status VARCHAR(20) NOT NULL,
    CONSTRAINT pk_equipment_transfer PRIMARY KEY (transfer_id),
    INDEX idx_fk_equipment_transfer_source_store_id (source_store_id),
    INDEX idx_fk_equipment_transfer_destination_store_id (destination_store_id),
    INDEX idx_fk_equipment_transfer_initiated_by_employee_id (initiated_by_employee_id),
    INDEX idx_fk_equipment_transfer_approved_by_employee_id (approved_by_employee_id),
    CONSTRAINT fk_equipment_transfer_source_store_id
        FOREIGN KEY (source_store_id) REFERENCES store (store_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_equipment_transfer_destination_store_id
        FOREIGN KEY (destination_store_id) REFERENCES store (store_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_equipment_transfer_initiated_by_employee_id
        FOREIGN KEY (initiated_by_employee_id) REFERENCES employee (employee_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_equipment_transfer_approved_by_employee_id
        FOREIGN KEY (approved_by_employee_id) REFERENCES employee (employee_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4;

CREATE TABLE equipment_transfer_item (
    transfer_item_id BIGINT NOT NULL AUTO_INCREMENT,
    transfer_id BIGINT NOT NULL,
    equipment_id BIGINT NOT NULL,
    dispatch_result VARCHAR(20) NOT NULL,
    dispatched_by_employee_id BIGINT NULL,
    dispatched_at DATETIME NULL,
    receipt_result VARCHAR(20) NOT NULL,
    received_by_employee_id BIGINT NULL,
    received_at DATETIME NULL,
    exception_note VARCHAR(500) NULL,
    CONSTRAINT pk_equipment_transfer_item PRIMARY KEY (transfer_item_id),
    INDEX idx_fk_equipment_transfer_item_transfer_id (transfer_id),
    INDEX idx_fk_equipment_transfer_item_equipment_id (equipment_id),
    INDEX idx_fk_equipment_transfer_item_dispatched_by_employee_id (dispatched_by_employee_id),
    INDEX idx_fk_equipment_transfer_item_received_by_employee_id (received_by_employee_id),
    CONSTRAINT fk_equipment_transfer_item_transfer_id
        FOREIGN KEY (transfer_id) REFERENCES equipment_transfer (transfer_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_equipment_transfer_item_equipment_id
        FOREIGN KEY (equipment_id) REFERENCES equipment (equipment_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_equipment_transfer_item_dispatched_by_employee_id
        FOREIGN KEY (dispatched_by_employee_id) REFERENCES employee (employee_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_equipment_transfer_item_received_by_employee_id
        FOREIGN KEY (received_by_employee_id) REFERENCES employee (employee_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4;

CREATE TABLE employee_shift (
    shift_id BIGINT NOT NULL AUTO_INCREMENT,
    shift_number VARCHAR(30) NOT NULL,
    shift_date DATE NOT NULL,
    store_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    shift_status VARCHAR(20) NOT NULL,
    changed_by_employee_id BIGINT NOT NULL,
    changed_at DATETIME NOT NULL,
    cancellation_reason VARCHAR(255) NULL,
    CONSTRAINT pk_employee_shift PRIMARY KEY (shift_id),
    INDEX idx_fk_employee_shift_store_id (store_id),
    INDEX idx_fk_employee_shift_employee_id (employee_id),
    INDEX idx_fk_employee_shift_changed_by_employee_id (changed_by_employee_id),
    CONSTRAINT fk_employee_shift_store_id
        FOREIGN KEY (store_id) REFERENCES store (store_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_employee_shift_employee_id
        FOREIGN KEY (employee_id) REFERENCES employee (employee_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_employee_shift_changed_by_employee_id
        FOREIGN KEY (changed_by_employee_id) REFERENCES employee (employee_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4;

CREATE TABLE audit_event (
    audit_event_id BIGINT NOT NULL AUTO_INCREMENT,
    actor_account_id BIGINT NOT NULL,
    object_type VARCHAR(40) NOT NULL,
    object_id BIGINT NOT NULL,
    action_code VARCHAR(40) NOT NULL,
    occurred_at DATETIME NOT NULL,
    result_code VARCHAR(20) NOT NULL,
    detail_summary VARCHAR(1000) NULL,
    CONSTRAINT pk_audit_event PRIMARY KEY (audit_event_id),
    INDEX idx_fk_audit_event_actor_account_id (actor_account_id),
    CONSTRAINT fk_audit_event_actor_account_id
        FOREIGN KEY (actor_account_id) REFERENCES account (account_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4;
