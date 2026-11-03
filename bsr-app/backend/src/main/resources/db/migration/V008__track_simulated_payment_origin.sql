-- 2026-10-27 approved customer virtual-payment demonstration. Historical external facts stay intact.
ALTER TABLE payment_transaction
    MODIFY recorded_by_employee_id BIGINT NULL,
    ADD COLUMN transaction_source VARCHAR(20) NOT NULL DEFAULT 'EXTERNAL',
    ADD COLUMN simulated_by_account_id BIGINT NULL,
    ADD CONSTRAINT fk_payment_simulated_account
        FOREIGN KEY (simulated_by_account_id) REFERENCES account (account_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT ck_payment_origin_recorder CHECK (
        (transaction_source = 'EXTERNAL' AND recorded_by_employee_id IS NOT NULL AND simulated_by_account_id IS NULL)
        OR (transaction_source = 'SIMULATED' AND recorded_by_employee_id IS NULL AND simulated_by_account_id IS NOT NULL)
    );
