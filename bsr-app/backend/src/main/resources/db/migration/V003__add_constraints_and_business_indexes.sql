-- T02 / V003: final uniqueness, validation, deferred relationship, and query indexes.
-- Cross-row and cross-table rules remain protected by READ_COMMITTED business
-- transactions and parent-row locking as specified in schema.md.

ALTER TABLE customer
    ADD CONSTRAINT uk_customer_number UNIQUE (customer_number),
    ADD CONSTRAINT uk_customer_email UNIQUE (email),
    ADD CONSTRAINT ck_customer_birth_year
        CHECK (birth_year IS NULL OR birth_year > 0),
    ADD CONSTRAINT ck_customer_height_positive
        CHECK (height_cm IS NULL OR height_cm > 0),
    ADD CONSTRAINT ck_customer_weight_positive
        CHECK (weight_kg IS NULL OR weight_kg > 0),
    ADD CONSTRAINT ck_customer_boot_size_positive
        CHECK (boot_size IS NULL OR boot_size > 0);

ALTER TABLE employee
    ADD CONSTRAINT uk_employee_number UNIQUE (employee_number),
    ADD CONSTRAINT uk_employee_email UNIQUE (email),
    ADD CONSTRAINT ck_employee_business_status
        CHECK (business_status IN ('ACTIVE', 'INACTIVE'));

ALTER TABLE repair_contractor
    ADD CONSTRAINT uk_repair_contractor_number UNIQUE (contractor_number),
    ADD CONSTRAINT uk_repair_contractor_email UNIQUE (email),
    ADD CONSTRAINT ck_repair_contractor_business_status
        CHECK (business_status IN ('ACTIVE', 'INACTIVE'));

ALTER TABLE store
    ADD CONSTRAINT uk_store_number UNIQUE (store_number),
    ADD CONSTRAINT ck_store_business_status
        CHECK (business_status IN ('ACTIVE', 'INACTIVE')),
    ADD CONSTRAINT ck_store_opening_before_closing
        CHECK (opening_time < closing_time);

ALTER TABLE account
    ADD CONSTRAINT uk_account_login_name UNIQUE (login_name),
    ADD CONSTRAINT uk_account_customer_id UNIQUE (customer_id),
    ADD CONSTRAINT uk_account_employee_id UNIQUE (employee_id),
    ADD CONSTRAINT uk_account_contractor_id UNIQUE (contractor_id),
    ADD CONSTRAINT ck_account_exactly_one_identity
        CHECK (
            (CASE WHEN customer_id IS NOT NULL THEN 1 ELSE 0 END) +
            (CASE WHEN employee_id IS NOT NULL THEN 1 ELSE 0 END) +
            (CASE WHEN contractor_id IS NOT NULL THEN 1 ELSE 0 END) = 1
        );

ALTER TABLE role_grant
    ADD CONSTRAINT ck_role_grant_role_code
        CHECK (role_code IN ('CUSTOMER', 'EMPLOYEE', 'MANAGER', 'CONTRACTOR')),
    ADD CONSTRAINT ck_role_grant_valid_range
        CHECK (valid_to IS NULL OR valid_to > valid_from);

ALTER TABLE rental_request
    ADD CONSTRAINT uk_rental_request_number UNIQUE (request_number),
    ADD CONSTRAINT ck_rental_request_status
        CHECK (request_status IN ('PENDING', 'NEEDS_INFO', 'ACCEPTED', 'UNAVAILABLE', 'CONVERTED')),
    ADD CONSTRAINT ck_rental_request_days_positive
        CHECK (rental_days > 0);

ALTER TABLE rental_request_item
    ADD CONSTRAINT ck_rental_request_item_quantity_positive
        CHECK (quantity > 0);

ALTER TABLE rental_contract
    ADD CONSTRAINT uk_contract_number UNIQUE (contract_number),
    ADD CONSTRAINT uk_rental_contract_source_request_id UNIQUE (source_request_id),
    ADD CONSTRAINT ck_rental_contract_status
        CHECK (
            contract_status IN (
                'DRAFT',
                'PENDING_CONFIRMATION',
                'PENDING_PAYMENT',
                'PENDING_ISSUE',
                'ACTIVE',
                'PENDING_SETTLEMENT',
                'ENDED',
                'CANCELLED'
            )
        ),
    ADD CONSTRAINT ck_rental_contract_days_positive
        CHECK (rental_days > 0),
    ADD CONSTRAINT ck_rental_contract_amount_due_nonnegative
        CHECK (amount_due >= 0),
    ADD INDEX idx_contract_customer_status (customer_id, contract_status);

ALTER TABLE rental_item
    ADD CONSTRAINT ck_rental_item_status
        CHECK (item_status IN ('PENDING_ISSUE', 'RENTED', 'RETURNED')),
    ADD CONSTRAINT ck_rental_item_daily_rate_nonnegative
        CHECK (daily_rate_snapshot >= 0),
    ADD CONSTRAINT ck_rental_item_return_order
        CHECK (returned_at IS NULL OR checked_out_at IS NULL OR returned_at >= checked_out_at),
    ADD INDEX idx_rental_item_equipment_time (equipment_id, checked_out_at, returned_at);

ALTER TABLE rental_change
    ADD CONSTRAINT uk_rental_change_number UNIQUE (change_number),
    ADD CONSTRAINT ck_rental_change_type
        CHECK (change_type IN ('ADD', 'REMOVE', 'REPLACE', 'PARTIAL_RETURN')),
    ADD CONSTRAINT ck_rental_change_status
        CHECK (change_status IN ('SUBMITTED', 'PROCESSING', 'COMPLETED', 'REJECTED')),
    ADD CONSTRAINT ck_rental_change_submitted_fields
        CHECK (
            change_status <> 'SUBMITTED' OR (
                requested_by_customer_id IS NOT NULL AND
                requested_at IS NOT NULL AND
                requested_store_id IS NOT NULL AND
                handled_by_employee_id IS NULL AND
                effective_date IS NULL AND
                amount_delta IS NULL AND
                changed_at IS NULL
            )
        ),
    ADD CONSTRAINT ck_rental_change_completed_fields
        CHECK (
            change_status <> 'COMPLETED' OR (
                handled_by_employee_id IS NOT NULL AND
                effective_date IS NOT NULL AND
                amount_delta IS NOT NULL AND
                changed_at IS NOT NULL
            )
        );

ALTER TABLE rental_change_item
    ADD CONSTRAINT uk_rental_change_item_effect
        UNIQUE (rental_change_id, rental_item_id, effect_code),
    ADD CONSTRAINT ck_rental_change_item_effect_code
        CHECK (effect_code IN ('ADD', 'RETURN', 'REPLACE_OUT', 'REPLACE_IN'));

ALTER TABLE rental_charge
    ADD CONSTRAINT ck_rental_charge_type
        CHECK (charge_type IN ('INITIAL', 'SUPPLEMENT', 'REFUND')),
    ADD CONSTRAINT ck_rental_charge_status
        CHECK (charge_status IN ('PENDING', 'PROCESSING', 'SETTLED', 'PENDING_REFUND')),
    ADD CONSTRAINT ck_rental_charge_amount_positive
        CHECK (amount > 0);

ALTER TABLE payment_transaction
    ADD CONSTRAINT uk_payment_external_id UNIQUE (external_transaction_id),
    ADD CONSTRAINT ck_payment_transaction_type
        CHECK (transaction_type IN ('PAYMENT', 'SUPPLEMENT', 'REFUND')),
    ADD CONSTRAINT ck_payment_transaction_status
        CHECK (transaction_status IN ('PENDING_CONFIRMATION', 'SUCCEEDED', 'FAILED')),
    ADD CONSTRAINT ck_payment_transaction_amount_positive
        CHECK (amount > 0);

ALTER TABLE equipment
    ADD CONSTRAINT uk_equipment_inventory_number UNIQUE (inventory_number),
    ADD CONSTRAINT ck_equipment_status
        CHECK (
            equipment_status IN (
                'AVAILABLE',
                'PENDING_ISSUE',
                'RENTED',
                'RETURN_INSPECTION',
                'WAITING_REPAIR',
                'IN_REPAIR',
                'PENDING_ACCEPTANCE',
                'IN_TRANSFER',
                'PENDING_REVIEW'
            )
        ),
    ADD CONSTRAINT ck_equipment_purchase_price_nonnegative
        CHECK (purchase_price IS NULL OR purchase_price >= 0),
    ADD CONSTRAINT ck_equipment_daily_rate_nonnegative
        CHECK (current_daily_rate >= 0),
    ADD INDEX idx_equipment_location_status (current_store_id, equipment_status, equipment_category);

ALTER TABLE repair_task
    ADD CONSTRAINT uk_repair_task_number UNIQUE (repair_task_number),
    ADD CONSTRAINT ck_repair_task_status
        CHECK (
            task_status IN (
                'UNASSIGNED',
                'PENDING',
                'IN_PROGRESS',
                'PENDING_ACCEPTANCE',
                'RETURNED',
                'PENDING_CHARGE',
                'COMPLETED'
            )
        ),
    ADD CONSTRAINT ck_repair_task_acceptance_result
        CHECK (acceptance_result IS NULL OR acceptance_result IN ('PASSED', 'FAILED', 'NEEDS_INFO')),
    ADD CONSTRAINT ck_repair_task_acceptance_fields
        CHECK (
            (
                acceptance_result IS NULL AND
                accepted_by_employee_id IS NULL AND
                accepted_at IS NULL
            ) OR (
                acceptance_result IS NOT NULL AND
                accepted_by_employee_id IS NOT NULL AND
                accepted_at IS NOT NULL
            )
        ),
    ADD INDEX idx_repair_task_contractor_status (current_contractor_id, task_status),
    ADD INDEX idx_repair_task_equipment_status (equipment_id, task_status);

ALTER TABLE equipment_inspection
    ADD CONSTRAINT fk_equipment_inspection_repair_task_id
        FOREIGN KEY (repair_task_id) REFERENCES repair_task (repair_task_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT ck_equipment_inspection_type
        CHECK (inspection_type IN ('RETURN', 'REPAIR_ACCEPTANCE')),
    ADD CONSTRAINT ck_equipment_inspection_result
        CHECK (inspection_result IN ('NORMAL', 'FAULT', 'PENDING_REVIEW'));

ALTER TABLE repair_rate
    ADD CONSTRAINT ck_repair_rate_positive
        CHECK (hourly_rate > 0),
    ADD CONSTRAINT ck_repair_rate_date_range
        CHECK (effective_to IS NULL OR effective_to >= effective_from),
    ADD INDEX idx_repair_rate_contractor_dates (contractor_id, effective_from, effective_to);

ALTER TABLE repair_labor
    ADD CONSTRAINT ck_repair_labor_quarter_hour
        CHECK (duration_hours > 0 AND MOD(duration_hours * 100, 25) = 0),
    ADD CONSTRAINT ck_repair_labor_rate_positive
        CHECK (hourly_rate_snapshot IS NULL OR hourly_rate_snapshot > 0),
    ADD CONSTRAINT ck_repair_labor_amount_nonnegative
        CHECK (labor_amount IS NULL OR labor_amount >= 0);

ALTER TABLE repair_service_charge
    ADD CONSTRAINT uk_repair_service_charge_task UNIQUE (repair_task_id),
    ADD CONSTRAINT ck_repair_service_charge_status
        CHECK (charge_status IN ('PENDING_CONFIRMATION', 'CONFIRMED')),
    ADD CONSTRAINT ck_repair_service_hours_nonnegative
        CHECK (total_hours >= 0),
    ADD CONSTRAINT ck_repair_service_amount_nonnegative
        CHECK (service_amount >= 0),
    ADD CONSTRAINT ck_repair_service_confirmation_fields
        CHECK (
            (
                charge_status = 'PENDING_CONFIRMATION' AND
                confirmed_by_employee_id IS NULL AND
                confirmed_at IS NULL
            ) OR (
                charge_status = 'CONFIRMED' AND
                confirmed_by_employee_id IS NOT NULL AND
                confirmed_at IS NOT NULL
            )
        );

ALTER TABLE equipment_transfer
    ADD CONSTRAINT uk_equipment_transfer_number UNIQUE (transfer_number),
    ADD CONSTRAINT ck_equipment_transfer_type
        CHECK (transfer_type IN ('TEMPORARY', 'PERMANENT')),
    ADD CONSTRAINT ck_equipment_transfer_status
        CHECK (
            transfer_status IN (
                'INITIATED',
                'APPROVED',
                'IN_TRANSIT',
                'PARTIALLY_RECEIVED',
                'COMPLETED'
            )
        ),
    ADD CONSTRAINT ck_transfer_distinct_stores
        CHECK (source_store_id <> destination_store_id),
    ADD CONSTRAINT ck_transfer_approval_fields
        CHECK (
            (approved_by_employee_id IS NULL AND approved_at IS NULL) OR
            (approved_by_employee_id IS NOT NULL AND approved_at IS NOT NULL)
        ),
    ADD CONSTRAINT ck_transfer_completion_time
        CHECK (transfer_status <> 'COMPLETED' OR completed_at IS NOT NULL),
    ADD INDEX idx_transfer_status_time (transfer_status, initiated_at);

ALTER TABLE equipment_transfer_item
    ADD CONSTRAINT uk_equipment_transfer_item_equipment
        UNIQUE (transfer_id, equipment_id),
    ADD CONSTRAINT ck_equipment_transfer_item_dispatch_result
        CHECK (dispatch_result IN ('PENDING', 'DISPATCHED', 'EXCEPTION')),
    ADD CONSTRAINT ck_equipment_transfer_item_receipt_result
        CHECK (receipt_result IN ('PENDING', 'RECEIVED', 'EXCEPTION')),
    ADD CONSTRAINT ck_transfer_item_dispatch_fields
        CHECK (
            (
                dispatch_result = 'PENDING' AND
                dispatched_by_employee_id IS NULL AND
                dispatched_at IS NULL
            ) OR (
                dispatch_result <> 'PENDING' AND
                dispatched_by_employee_id IS NOT NULL AND
                dispatched_at IS NOT NULL
            )
        ),
    ADD CONSTRAINT ck_transfer_item_receipt_fields
        CHECK (
            (
                receipt_result = 'PENDING' AND
                received_by_employee_id IS NULL AND
                received_at IS NULL
            ) OR (
                receipt_result <> 'PENDING' AND
                received_by_employee_id IS NOT NULL AND
                received_at IS NOT NULL
            )
        ),
    ADD INDEX idx_transfer_item_equipment (equipment_id, transfer_id);

ALTER TABLE employee_shift
    ADD CONSTRAINT uk_employee_shift_number UNIQUE (shift_number),
    ADD CONSTRAINT ck_employee_shift_status
        CHECK (shift_status IN ('ACTIVE', 'CANCELLED')),
    ADD CONSTRAINT ck_shift_time_order
        CHECK (start_time < end_time),
    ADD CONSTRAINT ck_shift_business_hours
        CHECK (start_time >= '08:00:00' AND end_time <= '22:00:00'),
    ADD INDEX idx_shift_employee_date_status (employee_id, shift_date, shift_status),
    ADD INDEX idx_shift_store_date (store_id, shift_date, shift_status);

ALTER TABLE audit_event
    ADD CONSTRAINT ck_audit_event_result_code
        CHECK (result_code IN ('SUCCESS', 'FAILURE', 'CONFLICT')),
    ADD INDEX idx_audit_object_time (object_type, object_id, occurred_at);

-- Add replacement UNIQUE/composite indexes above before removing the now
-- redundant single-column FK indexes. This ordering keeps every FK indexed
-- throughout the migration on MySQL.
ALTER TABLE account
    DROP INDEX idx_fk_account_customer_id,
    DROP INDEX idx_fk_account_employee_id,
    DROP INDEX idx_fk_account_contractor_id;

ALTER TABLE rental_contract
    DROP INDEX idx_fk_rental_contract_source_request_id,
    DROP INDEX idx_fk_rental_contract_customer_id;

ALTER TABLE rental_item
    DROP INDEX idx_fk_rental_item_equipment_id;

ALTER TABLE rental_change_item
    DROP INDEX idx_fk_rental_change_item_rental_change_id;

ALTER TABLE equipment
    DROP INDEX idx_fk_equipment_current_store_id;

ALTER TABLE repair_task
    DROP INDEX idx_fk_repair_task_equipment_id,
    DROP INDEX idx_fk_repair_task_current_contractor_id;

ALTER TABLE repair_rate
    DROP INDEX idx_fk_repair_rate_contractor_id;

ALTER TABLE repair_service_charge
    DROP INDEX idx_fk_repair_service_charge_repair_task_id;

ALTER TABLE equipment_transfer_item
    DROP INDEX idx_fk_equipment_transfer_item_transfer_id,
    DROP INDEX idx_fk_equipment_transfer_item_equipment_id;

ALTER TABLE employee_shift
    DROP INDEX idx_fk_employee_shift_store_id,
    DROP INDEX idx_fk_employee_shift_employee_id;
