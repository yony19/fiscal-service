# D2.4 SUNAT beta

Estado actual:
- El `FiscalSenderPort` ya usa `SunatSoapFiscalSender` como implementacion primaria.
- El flujo arma ZIP del XML firmado, invoca `sendBill` por SOAP y procesa `applicationResponse` o `Fault`.
- El documento ahora puede terminar en `ACCEPTED`, `REJECTED` o `ERROR` segun la respuesta de SUNAT.
- El modelo ya contempla `TICKETED` para respuestas asincronas, aunque `getStatus` aun no esta implementado.
- El contrato de aplicacion ya expone una operacion de consulta de estado para documentos `TICKETED`.
- El adapter SUNAT ya tiene una primera implementacion de `getStatus` por ticket.

## Credenciales del provider

`fiscal_provider_config.credential_ref` debe resolver a uno de estos formatos:

1. `env:SUNAT_BETA_CREDENTIALS`
   Valor esperado del env:
   - `usuario:clave`
   - o JSON: `{"username":"usuario","password":"clave"}`

2. `plain:usuario:clave`

`endpoint_submit_url` debe apuntar al endpoint SOAP beta configurado para SUNAT.

## Artefactos

- El XML firmado se envia comprimido como `{fullNumber}.zip`.
- El ZIP enviado se persiste en el storage fiscal y su metadata queda disponible en BD.
- La respuesta SOAP cruda del provider tambien se persiste para diagnostico.
- El CDR retornado por SUNAT se guarda como `{fullNumber}-cdr.zip`.
- El hash del ZIP y del CDR tambien se persisten para trazabilidad.

## Limitaciones de esta primera version

- Implementa `sendBill` sincronico.
- `getStatus` esta implementado en una primera version y asume la semantica comun de estados `0`, `98` y `99`.
- El parser clasifica `ResponseCode = 0` como `ACCEPTED`; cualquier otro codigo del CDR se clasifica como `REJECTED`.
- `authority_ticket` se reserva para el ticket tecnico de SUNAT, no para tickets operativos del POS o `sales-service`.
