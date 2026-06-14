-- La serie de una Comunicacion de Baja es "RA-YYYYMMDD" (11 caracteres), que no
-- entra en VARCHAR(10) y rompia la anulacion con "value too long". Los
-- comprobantes normales usan series cortas (F001/B001), asi que ensanchar es
-- inocuo. 20 deja margen para futuros formatos.
ALTER TABLE fiscal_document ALTER COLUMN series TYPE VARCHAR(20);
