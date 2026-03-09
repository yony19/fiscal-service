-- D1 seed mínimo para pruebas de reserva fiscal (BOLETA/FACTURA)
-- Proyecto: fiscal-service
-- Idempotente: usa ON CONFLICT DO NOTHING

-- Reemplazar este UUID por el company_id real de pruebas
-- Ejemplo: 11111111-1111-1111-1111-111111111111
-- Puede ejecutarse varias veces sin duplicar series base.

INSERT INTO fiscal_series (
    company_id,
    country_code,
    tax_authority_code,
    document_type_code,
    series,
    next_number,
    environment,
    is_active,
    valid_from,
    valid_to,
    metadata,
    version,
    created_at,
    updated_at,
    created_by,
    updated_by
)
VALUES
(
    '__COMPANY_ID__'::uuid,
    'PE',
    'SUNAT',
    '03',
    'B001',
    1,
    'TEST',
    TRUE,
    NULL,
    NULL,
    '{}'::jsonb,
    0,
    now(),
    now(),
    'seed',
    'seed'
),
(
    '__COMPANY_ID__'::uuid,
    'PE',
    'SUNAT',
    '01',
    'F001',
    1,
    'TEST',
    TRUE,
    NULL,
    NULL,
    '{}'::jsonb,
    0,
    now(),
    now(),
    'seed',
    'seed'
)
ON CONFLICT (company_id, country_code, tax_authority_code, document_type_code, series, environment)
DO NOTHING;

-- Para preparar PROD, duplicar con:
-- environment='PROD'
-- series BOLETA/FAC: Bxxx/Fxxx según política real del emisor.

