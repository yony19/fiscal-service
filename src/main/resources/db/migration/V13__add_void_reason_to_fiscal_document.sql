-- Motivo de anulacion para la Comunicacion de Baja (RA). Antes el motivo que
-- escribia el operador se descartaba y SUNAT recibia siempre "ANULACION" fijo;
-- ahora se persiste y se envia como sac:VoidReasonDescription. Solo lo usan los
-- documentos VOID; el resto queda NULL.
ALTER TABLE fiscal_document ADD COLUMN IF NOT EXISTS void_reason VARCHAR(250);
