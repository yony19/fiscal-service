-- V3__add_signed_xml_metadata_to_fiscal_document.sql
ALTER TABLE fiscal_document
    ADD COLUMN IF NOT EXISTS signed_xml_path TEXT NULL;

ALTER TABLE fiscal_document
    ADD COLUMN IF NOT EXISTS signed_xml_hash VARCHAR(128) NULL;
