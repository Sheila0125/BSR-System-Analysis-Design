-- T02 / V001: identity and organization foundations.
-- Final UNIQUE and CHECK constraints are added by V003 as allocated in schema.md.

CREATE TABLE customer (
    customer_id BIGINT NOT NULL AUTO_INCREMENT,
    customer_number VARCHAR(30) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(40) NOT NULL,
    address VARCHAR(255) NULL,
    birth_year SMALLINT NULL,
    height_cm DECIMAL(5, 2) NULL,
    weight_kg DECIMAL(5, 2) NULL,
    boot_size DECIMAL(4, 1) NULL,
    height_estimated BOOLEAN NOT NULL DEFAULT FALSE,
    weight_estimated BOOLEAN NOT NULL DEFAULT FALSE,
    boot_size_estimated BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_customer PRIMARY KEY (customer_id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4;

CREATE TABLE employee (
    employee_id BIGINT NOT NULL AUTO_INCREMENT,
    employee_number VARCHAR(30) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(40) NULL,
    business_status VARCHAR(20) NOT NULL,
    CONSTRAINT pk_employee PRIMARY KEY (employee_id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4;

CREATE TABLE repair_contractor (
    contractor_id BIGINT NOT NULL AUTO_INCREMENT,
    contractor_number VARCHAR(30) NOT NULL,
    contractor_name VARCHAR(120) NOT NULL,
    contact_name VARCHAR(100) NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(40) NULL,
    business_status VARCHAR(20) NOT NULL,
    CONSTRAINT pk_repair_contractor PRIMARY KEY (contractor_id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4;

CREATE TABLE store (
    store_id BIGINT NOT NULL AUTO_INCREMENT,
    store_number VARCHAR(30) NOT NULL,
    store_name VARCHAR(120) NOT NULL,
    address VARCHAR(255) NULL,
    business_status VARCHAR(20) NOT NULL,
    opening_time TIME NOT NULL,
    closing_time TIME NOT NULL,
    CONSTRAINT pk_store PRIMARY KEY (store_id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4;

CREATE TABLE account (
    account_id BIGINT NOT NULL AUTO_INCREMENT,
    login_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL,
    customer_id BIGINT NULL,
    employee_id BIGINT NULL,
    contractor_id BIGINT NULL,
    CONSTRAINT pk_account PRIMARY KEY (account_id),
    INDEX idx_fk_account_customer_id (customer_id),
    INDEX idx_fk_account_employee_id (employee_id),
    INDEX idx_fk_account_contractor_id (contractor_id),
    CONSTRAINT fk_account_customer_id
        FOREIGN KEY (customer_id) REFERENCES customer (customer_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_account_employee_id
        FOREIGN KEY (employee_id) REFERENCES employee (employee_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_account_contractor_id
        FOREIGN KEY (contractor_id) REFERENCES repair_contractor (contractor_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4;

CREATE TABLE role_grant (
    role_grant_id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    role_code VARCHAR(30) NOT NULL,
    store_id BIGINT NULL,
    valid_from DATETIME NOT NULL,
    valid_to DATETIME NULL,
    CONSTRAINT pk_role_grant PRIMARY KEY (role_grant_id),
    INDEX idx_fk_role_grant_account_id (account_id),
    INDEX idx_fk_role_grant_store_id (store_id),
    CONSTRAINT fk_role_grant_account_id
        FOREIGN KEY (account_id) REFERENCES account (account_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_role_grant_store_id
        FOREIGN KEY (store_id) REFERENCES store (store_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4;
