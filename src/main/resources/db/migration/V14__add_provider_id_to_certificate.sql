-- Vincula cada certificado a un Proveedor especifico (provider_id), en vez de
-- resolverlo por coincidencia de texto (provider_code) que no distingue
-- entorno TEST/PROD. provider_code se mantiene (denormalizado, de solo
-- lectura una vez que provider_id esta seteado) para no romper lecturas
-- existentes. Ver openspec/changes/certificate-provider-binding.
ALTER TABLE company_certificate ADD COLUMN IF NOT EXISTS provider_id UUID REFERENCES fiscal_provider_config(id);

CREATE INDEX IF NOT EXISTS idx_company_certificate_provider ON company_certificate(provider_id);

-- Backfill conservador: solo asigna provider_id cuando hay EXACTAMENTE un
-- fiscal_provider_config activo que matchea company_id + provider_code
-- (case-insensitive). Si hay 0 o 2+ candidatos (ambiguo entre TEST/PROD, o
-- ningun Proveedor con ese codigo todavia), se deja NULL a proposito -- nunca
-- se adivina el entorno. Un certificado con provider_id NULL se trata como no
-- elegible para firmar (ver CertificateResolutionService) hasta que alguien
-- lo re-vincule explicitamente desde la UI.
WITH candidate_counts AS (
    SELECT cc.id AS certificate_id,
           fp.id AS provider_id,
           count(*) OVER (PARTITION BY cc.id) AS match_count
    FROM company_certificate cc
    JOIN fiscal_provider_config fp
        ON fp.company_id = cc.company_id
       AND fp.is_active = TRUE
       AND upper(fp.provider_code) = upper(cc.provider_code)
)
UPDATE company_certificate cc
SET provider_id = cc2.provider_id
FROM candidate_counts cc2
WHERE cc.id = cc2.certificate_id
  AND cc2.match_count = 1;
