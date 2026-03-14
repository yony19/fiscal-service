ALTER TABLE fiscal_document
    ADD COLUMN IF NOT EXISTS response_path TEXT NULL;

ALTER TABLE fiscal_document
    ADD COLUMN IF NOT EXISTS response_hash VARCHAR(128) NULL;
