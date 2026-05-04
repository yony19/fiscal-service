-- ─────────────────────────────────────────────────────────────────────────────
-- V11: Friendly certificate upload support.
--
-- The previous design exposed certificate storage details (paths, secret refs,
-- fingerprints) directly to the user. The new flow lets the user just upload
-- a .pfx file and type the password — the backend extracts metadata, computes
-- the fingerprint, encrypts the password, and stores the bytes inline.
--
-- Changes:
--  • Add password_encrypted BYTEA + password_iv BYTEA so AES-GCM encrypted
--    passwords can be persisted without leaving plaintext in the DB.
--  • Make valid_from / valid_to nullable: when the user uploads a .pfx, those
--    dates are extracted automatically from the cert and don't need user input.
--    Same for storage_mode (defaults to INLINE_ENCRYPTED on upload).
--  • Add upload_filename + uploaded_at audit columns so the user UI can show
--    "Subido: cert-2024.pfx, hace 3 días" without exposing internals.
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE company_certificate
    ADD COLUMN IF NOT EXISTS password_encrypted BYTEA,
    ADD COLUMN IF NOT EXISTS password_iv        BYTEA,
    ADD COLUMN IF NOT EXISTS upload_filename    VARCHAR(255),
    ADD COLUMN IF NOT EXISTS uploaded_at        TIMESTAMPTZ;

-- valid_from / valid_to: NULL when the user is editing metadata only and
-- hasn't uploaded the .pfx yet. Once uploaded, they're populated from the
-- cert and can never go back to NULL.
ALTER TABLE company_certificate
    ALTER COLUMN valid_from DROP NOT NULL,
    ALTER COLUMN valid_to   DROP NOT NULL;

-- storage_mode: NULL until the user picks how the cert is stored. After upload
-- it's set to 'INLINE_ENCRYPTED' automatically.
ALTER TABLE company_certificate
    ALTER COLUMN storage_mode DROP NOT NULL;

-- Helpful index for "show certificates expiring in <30 days" queries the UI
-- runs to drive the badge alerts in the certificates list.
CREATE INDEX IF NOT EXISTS idx_company_certificate_valid_to
    ON company_certificate(company_id, valid_to)
    WHERE valid_to IS NOT NULL;
