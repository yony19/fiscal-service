-- =============================================
-- V1: Initial fiscal-service schema
-- Tables: fiscal_series, fiscal_document, fiscal_document_line,
--         fiscal_event, company_certificate, fiscal_provider_config
-- =============================================

-- 1. Fiscal Series
CREATE TABLE fiscal_series (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    country_code CHAR(2) NOT NULL,
    tax_authority_code VARCHAR(20) NOT NULL,
    document_type_code VARCHAR(10) NOT NULL,
    series VARCHAR(10) NOT NULL,
    next_number BIGINT NOT NULL DEFAULT 1,
    environment VARCHAR(10) NOT NULL CHECK (environment IN ('TEST','PROD')),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    valid_from DATE,
    valid_to DATE,
    metadata JSONB,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE UNIQUE INDEX uq_fiscal_series_scope
    ON fiscal_series (company_id, country_code, tax_authority_code, document_type_code, series, environment);

CREATE INDEX idx_fiscal_series_company ON fiscal_series(company_id, environment);

-- 2. Fiscal Document
CREATE TABLE fiscal_document (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    country_code CHAR(2) NOT NULL,
    tax_authority_code VARCHAR(20) NOT NULL,
    environment VARCHAR(10) NOT NULL CHECK (environment IN ('TEST','PROD')),
    source_service VARCHAR(60) NOT NULL,
    source_id VARCHAR(100) NOT NULL,
    source_code VARCHAR(100),
    source_event_type VARCHAR(40),
    source_event_id VARCHAR(120),
    idempotency_key VARCHAR(120) NOT NULL,
    request_fingerprint_sha256 VARCHAR(64),
    document_type VARCHAR(30) NOT NULL,
    document_type_code VARCHAR(10) NOT NULL,
    operation_type_code VARCHAR(10),
    issue_date DATE NOT NULL,
    issue_time TIME,
    series_id UUID REFERENCES fiscal_series(id),
    series VARCHAR(10) NOT NULL,
    number BIGINT NOT NULL,
    full_number VARCHAR(30) NOT NULL,
    related_document_id UUID REFERENCES fiscal_document(id),
    related_document_type_code VARCHAR(10),
    related_document_number VARCHAR(30),
    currency_code VARCHAR(3) NOT NULL,
    exchange_rate NUMERIC(18,6),
    emitter_document_type VARCHAR(10) NOT NULL,
    emitter_document_number VARCHAR(30) NOT NULL,
    emitter_legal_name VARCHAR(255) NOT NULL,
    emitter_trade_name VARCHAR(255),
    emitter_address VARCHAR(500) NOT NULL,
    customer_id UUID,
    customer_document_type VARCHAR(10),
    customer_document_number VARCHAR(30),
    customer_name VARCHAR(255) NOT NULL,
    customer_address VARCHAR(500),
    customer_email VARCHAR(255),
    taxable_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    exempt_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    unaffected_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    free_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    discount_total NUMERIC(18,2) NOT NULL DEFAULT 0,
    charge_total NUMERIC(18,2) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    igv_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    isc_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    other_tax_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    total_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    status VARCHAR(25) NOT NULL,
    xml_path TEXT,
    cdr_path TEXT,
    xml_hash VARCHAR(128),
    qr_data TEXT,
    provider_code VARCHAR(40),
    send_attempt_count INTEGER NOT NULL DEFAULT 0,
    error_code VARCHAR(60),
    error_message TEXT,
    authority_ticket VARCHAR(120),
    authority_status_code VARCHAR(40),
    authority_status_message TEXT,
    sent_at TIMESTAMPTZ,
    accepted_at TIMESTAMPTZ,
    rejected_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE UNIQUE INDEX uq_fiscal_document_idempotency
    ON fiscal_document (company_id, source_service, idempotency_key);

CREATE INDEX idx_fiscal_document_company ON fiscal_document(company_id, status);
CREATE INDEX idx_fiscal_document_source ON fiscal_document(source_service, source_id);

-- 3. Fiscal Document Line
CREATE TABLE fiscal_document_line (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fiscal_document_id UUID NOT NULL REFERENCES fiscal_document(id) ON DELETE CASCADE,
    line_no INTEGER NOT NULL,
    item_id VARCHAR(100),
    item_code VARCHAR(100),
    sku VARCHAR(100),
    barcode VARCHAR(100),
    item_sunat_code VARCHAR(30),
    description VARCHAR(1000) NOT NULL,
    unit_code VARCHAR(10),
    unit_name VARCHAR(50),
    quantity NUMERIC(18,6) NOT NULL,
    unit_price NUMERIC(18,6) NOT NULL,
    unit_value NUMERIC(18,6),
    tax_affectation_code VARCHAR(10),
    igv_rate NUMERIC(7,4),
    isc_rate NUMERIC(7,4),
    discount_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    taxable_base_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    line_total NUMERIC(18,2) NOT NULL DEFAULT 0,
    metadata JSONB,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE INDEX idx_fiscal_document_line_doc ON fiscal_document_line(fiscal_document_id);

-- 4. Fiscal Event
CREATE TABLE fiscal_event (
    id BIGSERIAL PRIMARY KEY,
    fiscal_document_id UUID NOT NULL REFERENCES fiscal_document(id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    payload JSONB,
    message TEXT,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_fiscal_event_doc ON fiscal_event(fiscal_document_id, created_at);

-- 5. Company Certificate
CREATE TABLE company_certificate (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    provider_code VARCHAR(40) NOT NULL,
    alias VARCHAR(80) NOT NULL,
    storage_mode VARCHAR(20) NOT NULL,
    certificate_path TEXT,
    certificate_data BYTEA,
    private_key_path TEXT,
    private_key_data BYTEA,
    secret_ref VARCHAR(200),
    password_secret_ref VARCHAR(200),
    fingerprint_sha256 VARCHAR(64),
    valid_from TIMESTAMPTZ NOT NULL,
    valid_to TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE INDEX idx_company_certificate_company ON company_certificate(company_id, status);

-- 6. Fiscal Provider Config
CREATE TABLE fiscal_provider_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    country_code CHAR(2) NOT NULL,
    tax_authority_code VARCHAR(20) NOT NULL,
    provider_code VARCHAR(40) NOT NULL,
    environment VARCHAR(10) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    priority INTEGER NOT NULL DEFAULT 0,
    endpoint_submit_url TEXT,
    endpoint_status_url TEXT,
    endpoint_cdr_url TEXT,
    auth_type VARCHAR(20) NOT NULL,
    credential_ref VARCHAR(200),
    timeout_ms INTEGER NOT NULL DEFAULT 30000,
    max_retries INTEGER NOT NULL DEFAULT 3,
    retry_backoff_ms INTEGER NOT NULL DEFAULT 1000,
    config_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE INDEX idx_fiscal_provider_company ON fiscal_provider_config(company_id, environment);
