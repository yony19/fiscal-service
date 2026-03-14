# D2.3 / D2.4 Retry Policy

Fecha: 2026-03-14

## Estado actual

El `fiscal-service` ya soporta:

- generacion real de XML UBL
- firma real con certificado PKCS#12
- sender SUNAT beta
- persistencia de XML, signed XML y CDR
- clasificacion base de errores recuperables vs no recuperables
- reproceso desde `SIGNED` para errores recuperables de envio
- contador y programacion base de retry para fallos recuperables de envio

## Semantica operativa

### Estados finales relevantes

- `ACCEPTED`: SUNAT acepto el documento
- `REJECTED`: SUNAT rechazo el documento por negocio fiscal
- `ERROR`: fallo tecnico o de proceso

### Campos nuevos en `fiscal_document`

- `retryable_error`
- `last_failed_stage`
- `last_error_at`
- `retry_count`
- `next_retry_at`

### Significado

`retryable_error = true`

- el documento puede volver a procesarse
- hoy el caso soportado es fallo en etapa `SEND`
- el reproceso reutiliza el `signed_xml_path` existente
- el reintento puede quedar diferido hasta `next_retry_at`

`retryable_error = false`

- requiere correccion funcional o de configuracion
- no debe esperarse que un reintento automatico lo resuelva

## Clasificacion actual

### No recuperables

- payload fiscal invalido
- emisor invalido o mal configurado
- provider faltante o inconsistente
- certificado invalido
- alias no encontrado
- password incorrecta
- XML no firmable

### Recuperables

- timeout al enviar
- error de red
- respuesta vacia de SUNAT
- SOAP Fault
- error de parseo de respuesta o CDR

## Reproceso actual

El endpoint:

- `POST /fiscal/documents/{id}/process`

ahora puede continuar desde:

- `RESERVED`
- `XML_GENERATED`
- `SIGNED`
- `ERROR` cuando `retryable_error = true` y `last_failed_stage = SEND`
- `ERROR` recuperable solo cuando ya se cumplio `next_retry_at`

## Flujo de retry soportado

1. Documento firmado queda en `SIGNED`
2. Falla el envio
3. Documento termina en `ERROR`
4. `retryable_error = true`
5. `last_failed_stage = SEND`
6. `retry_count` aumenta
7. `next_retry_at` se calcula segun provider
8. Nuevo `POST /process` despues de la ventana de retry
9. El sistema reanuda desde el signed XML ya existente

## Flujo que no debe reintentarse automaticamente

Si el documento falla por:

- certificado
- alias
- password
- emisor
- validacion fiscal

quedara en `ERROR` con `retryable_error = false`

## Que revisar en API

### `GET /fiscal/documents/{id}`

Validar:

- `status`
- `errorCode`
- `errorMessage`
- `retryableError`
- `lastFailedStage`
- `retryCount`
- `nextRetryAt`
- `xmlPath`
- `signedXmlPath`

### `GET /fiscal/documents/{id}/events`

Validar eventos como:

- `PROCESSING_STARTED`
- `XML_GENERATED`
- `XML_SIGNED`
- `SEND_ATTEMPTED`
- `SEND_FAILED`
- `PROCESSING_RESUMED`
- `PROCESSING_SKIPPED` con `nextRetryAt` cuando aun no corresponde reintentar

## Prueba recomendada de error recuperable

1. Generar un documento nuevo
2. Procesarlo con endpoint SUNAT incorrecto o sin conectividad
3. Verificar:
   - `status = ERROR`
   - `retryableError = true`
   - `lastFailedStage = SEND`
   - `retryCount >= 1`
   - `nextRetryAt` no nulo
   - `signedXmlPath` no nulo
4. Corregir conectividad
5. Esperar a que se cumpla `nextRetryAt`
6. Ejecutar otra vez `POST /process`
7. Verificar que reanuda desde firmado y no genera nuevo XML

## Limites actuales

- aun no existe scheduler de retry automatico
- aun no existe endpoint dedicado tipo `/retry`
- aun no hay soporte de ticket async `getStatus`

## Recomendacion siguiente

El siguiente paso de arquitectura debe ser:

1. agregar job o comando de reproceso automatico
2. separar ZIP enviado como artefacto persistido
3. persistir respuesta cruda del provider
4. soportar ticket async y `getStatus`
