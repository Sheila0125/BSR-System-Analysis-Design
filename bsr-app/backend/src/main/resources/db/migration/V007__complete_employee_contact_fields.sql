-- Optional employee archive fields already specified in design table 5-5.
ALTER TABLE employee
    ADD COLUMN address VARCHAR(255) NULL,
    ADD COLUMN note VARCHAR(500) NULL;
