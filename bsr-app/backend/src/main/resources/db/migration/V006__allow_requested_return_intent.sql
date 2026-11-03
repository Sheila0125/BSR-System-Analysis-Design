-- Customer intent is retained separately from actual RETURN / REPLACE_OUT facts.
-- No existing links, rental items, equipment states or charge snapshots are changed.
ALTER TABLE rental_change_item
    DROP CHECK ck_rental_change_item_effect_code,
    ADD CONSTRAINT ck_rental_change_item_effect_code
        CHECK (effect_code IN ('ADD', 'RETURN', 'REPLACE_OUT', 'REPLACE_IN', 'REQUESTED_RETURN'));
