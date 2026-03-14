ALTER TABLE fiscal_document
    ADD COLUMN IF NOT EXISTS operation_type_code VARCHAR(10) NULL;
