-- V2__create_fiscal_emitter_config.sql
-- Fiscal emitter live identity configuration

CREATE TABLE IF NOT EXISTS fiscal_emitter_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    country_code CHAR(2) NOT NULL DEFAULT 'PE',
    tax_authority_code VARCHAR(20) NOT NULL DEFAULT 'SUNAT',
    environment VARCHAR(10) NOT NULL CHECK (environment IN ('TEST','PROD')),
    provider_code VARCHAR(40) NULL,

    document_type VARCHAR(10) NOT NULL,
    document_number VARCHAR(30) NOT NULL,

    legal_name VARCHAR(255) NOT NULL,
    trade_name VARCHAR(255) NULL,

    fiscal_address VARCHAR(500) NOT NULL,
    ubigeo VARCHAR(20) NULL,
    district VARCHAR(120) NULL,
    city VARCHAR(120) NULL,
    state VARCHAR(120) NULL,
    country_name VARCHAR(120) NULL,
    postal_code VARCHAR(20) NULL,

    email VARCHAR(255) NULL,
    phone VARCHAR(60) NULL,

    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE','INACTIVE')),
    is_default BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

-- Natural identity by legal emitter document in scope
CREATE UNIQUE INDEX IF NOT EXISTS uq_fiscal_emitter_identity
    ON fiscal_emitter_config (company_id, country_code, tax_authority_code, environment, document_type, document_number);

-- Only one default per company+country+authority+environment
CREATE UNIQUE INDEX IF NOT EXISTS uq_fiscal_emitter_default_scope
    ON fiscal_emitter_config (company_id, country_code, tax_authority_code, environment)
    WHERE is_default = TRUE;

CREATE INDEX IF NOT EXISTS idx_fiscal_emitter_company
    ON fiscal_emitter_config (company_id, environment, status);

CREATE INDEX IF NOT EXISTS idx_fiscal_emitter_provider
    ON fiscal_emitter_config (company_id, provider_code);
