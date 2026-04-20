ALTER TABLE fiscal_document
    ADD COLUMN cdr_xml_path TEXT,
    ADD COLUMN cdr_xml_hash VARCHAR(128);
