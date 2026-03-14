ALTER TABLE fiscal_document
    ADD COLUMN IF NOT EXISTS retryable_error BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE fiscal_document
    ADD COLUMN IF NOT EXISTS last_failed_stage VARCHAR(40) NULL;

ALTER TABLE fiscal_document
    ADD COLUMN IF NOT EXISTS last_error_at TIMESTAMPTZ NULL;
