-- UC10 equipment archive and UC15 contractor postal contact; existing prices remain unchanged.
ALTER TABLE equipment
    ADD COLUMN description VARCHAR(1000) NULL,
    ADD COLUMN retail_price DECIMAL(12, 2) NULL,
    ADD COLUMN catalog_version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_equipment_retail_price_nonnegative CHECK (retail_price IS NULL OR retail_price >= 0),
    ADD CONSTRAINT ck_equipment_catalog_version_nonnegative CHECK (catalog_version >= 0);

ALTER TABLE repair_contractor ADD COLUMN postal_address VARCHAR(255) NULL;
