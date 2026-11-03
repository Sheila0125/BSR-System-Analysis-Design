-- User-approved T04 supplement history. V001-V003 remain unchanged.
-- Every request/reply cycle is a separate row; earlier cycles are retained.
CREATE TABLE rental_request_supplement (
    supplement_id BIGINT NOT NULL AUTO_INCREMENT,
    request_id BIGINT NOT NULL,
    cycle_number INT NOT NULL,
    requested_by_employee_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    requested_at DATETIME NOT NULL,
    customer_response VARCHAR(500) NULL,
    responded_at DATETIME NULL,
    CONSTRAINT pk_rental_request_supplement PRIMARY KEY (supplement_id),
    CONSTRAINT uk_rental_request_supplement_request_cycle UNIQUE (request_id, cycle_number),
    INDEX idx_fk_rental_request_supplement_requested_by_employee_id (requested_by_employee_id),
    CONSTRAINT fk_rental_request_supplement_request_id
        FOREIGN KEY (request_id) REFERENCES rental_request (request_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_rental_request_supplement_requested_by_employee_id
        FOREIGN KEY (requested_by_employee_id) REFERENCES employee (employee_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_rental_request_supplement_cycle_positive CHECK (cycle_number > 0),
    CONSTRAINT ck_rental_request_supplement_reason_present CHECK (CHAR_LENGTH(TRIM(reason)) > 0),
    CONSTRAINT ck_rental_request_supplement_response_pair CHECK (
        (customer_response IS NULL AND responded_at IS NULL)
        OR (customer_response IS NOT NULL AND responded_at IS NOT NULL
            AND CHAR_LENGTH(TRIM(customer_response)) > 0)
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4;
