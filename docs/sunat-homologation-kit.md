# Kit de Homologacion SUNAT beta (SUNAT directo)

> Objetivo: dejar todo listo para **homologar y vender rapido** emitiendo directo a SUNAT,
> sin depender de un OSE pagado. La arquitectura ya es pluggable: SUNAT directo es el
> **default** (`SunatSoapFiscalSender.isDefault() = true`); un OSE se agrega despues por
> configuracion + un adapter, sin tocar el ruteo.

## 0. Estado del codigo (listo)

| Pieza | Estado |
|---|---|
| UBL builder (factura/boleta/NC/ND) | Real (`PeruUblFiscalXmlBuilder`) |
| Comunicacion de Baja (RA) builder | Real (`PeruVoidedXmlStrategy`) |
| Resumen Diario (RC) builder | Pendiente (bloqueado por modelo de datos — ver PRD #2) |
| Firma XMLDSIG | Real (`RealXmlDigitalSigner`) |
| Envio sincrono `sendBill` + CDR | Real |
| Envio async `sendSummary` + ticket + `getStatus` | Real |
| Descarga de CDR | Real (`GET /fiscal/documents/{id}/cdr`) |
| Seleccion de proveedor por config (no hardcode) | Real (`resolveSender` por `provider_code`) |

**Lo unico que falta es certificar contra SUNAT beta.** Eso lo corre el dueno con sus credenciales.

## 1. Datos que necesitas (gratis)

1. **Usuario de homologacion SUNAT (MODDATOS)** — usuario SOL de prueba estandar:
   - Username: `{RUC}MODDATOS`  (ej. `20123456789MODDATOS`)
   - Password: `moddatos`
2. **Certificado digital de prueba** (.pfx/.p12). Para homologacion sirve el certificado de
   prueba que SUNAT publica, o uno auto-firmado para beta. Se sube desde el admin
   (pagina de **Certificados**). En PROD se reemplaza por el certificado real de la empresa.

## 2. Configuracion del proveedor (desde el admin > Fiscal > Proveedores)

Crear UN `fiscal_provider_config` por empresa con estos valores:

| Campo | Valor beta (TEST) | Valor produccion (PROD) |
|---|---|---|
| `provider_code` | `SUNAT` | `SUNAT` |
| `environment` | `TEST` | `PROD` |
| `country_code` | `PE` | `PE` |
| `tax_authority_code` | `SUNAT` | `SUNAT` |
| `endpoint_submit_url` | `https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService` | `https://e-factura.sunat.gob.pe/ol-ti-itcpfegem/billService` |
| `endpoint_status_url` | (igual que submit) | (igual que submit) |
| `auth_type` | `WSSE` (UsernameToken) | `WSSE` |
| `credential_ref` | `env:SUNAT_BETA_CREDENTIALS` o `plain:{RUC}MODDATOS:moddatos` | `env:SUNAT_PROD_CREDENTIALS_{companyId}` |
| `timeout_ms` | `30000` | `30000` |
| `max_retries` | `3` | `3` |
| `retry_backoff_ms` | `30000` | `30000` |
| `is_active` | `true` | `true` |

> El `endpoint_submit_url` mostrado es el del servicio de **CPE** (facturas, boletas, NC, ND,
> Comunicacion de Baja, Resumen Diario). La Guia de Remision (GRE) usa otro servicio — no
> aplica a este alcance.

### Credenciales por env var
Si `credential_ref = env:SUNAT_BETA_CREDENTIALS`, setear en el entorno del fiscal-service:
```
SUNAT_BETA_CREDENTIALS={RUC}MODDATOS:moddatos
# o JSON:
SUNAT_BETA_CREDENTIALS={"username":"{RUC}MODDATOS","password":"moddatos"}
```
(Nunca commitear credenciales reales — ver PRD #0.)

## 3. Set de homologacion SUNAT (lo que hay que emitir y dejar ACCEPTED)

SUNAT exige un set minimo. Emitir desde el POS y verificar cada uno `ACCEPTED` + CDR:

- [ ] **Factura** gravada (IGV 18%) — 1+.
- [ ] **Factura** con mas de un item / con descuento.
- [ ] **Boleta** de venta.
- [ ] **Nota de Credito** (sobre una factura aceptada).
- [ ] **Nota de Debito** (sobre una factura aceptada).
- [ ] **Comunicacion de Baja (RA)** — anular un comprobante aceptado (flujo async: TICKETED -> ACCEPTED).
- [ ] **Resumen Diario (RC)** de boletas — *bloqueado hasta cerrar el modelo de datos (PRD #2)*.

> El set exacto depende del padron/perfil de la empresa en SUNAT. Confirmar el set vigente en
> el portal SUNAT (Comprobantes de pago electronicos > Homologacion).

## 4. Verificacion end-to-end

Por cada documento del set:
1. Completar una venta en el POS (genera el draft fiscal y dispara `reserve`).
2. `process()` corre: XML -> firma -> `sendBill`/`sendSummary` -> SUNAT.
3. Confirmar estado **ACCEPTED** (o TICKETED -> ACCEPTED para RA/RC) en `GET /fiscal/documents/{id}`.
4. Descargar el CDR: `GET /fiscal/documents/{id}/cdr`.
5. Revisar el `fiscal_event` (auditoria) — la cadena de transiciones debe estar completa.

Si un documento sale **REJECTED**: leer el `Description` del CDR (codigo SUNAT 2xxx/3xxx),
corregir el dato/estructura y reintentar. Los 3xxx suelen ser descuadres de montos.

## 5. Checklist para "vendible"

- [ ] Certificado de prueba subido (admin > Certificados).
- [ ] `fiscal_provider_config` SUNAT/TEST creado (seccion 2) + env de credenciales.
- [ ] Set de homologacion emitido y ACCEPTED (seccion 3).
- [ ] Cambiar `environment` a PROD + endpoint PROD + certificado real de la empresa.
- [ ] (Recomendado) Auto-provisionar el `fiscal_provider_config` SUNAT al onboarding de cada
      empresa, para no configurarlo a mano (ver "Pendiente" abajo).

## Pendiente para "default sin configurar a mano"

Hoy cada empresa debe crear su `fiscal_provider_config` (es per-company). Para que SUNAT
directo sea el **default automatico** al dar de alta una empresa, conviene **auto-provisionar**
una fila `fiscal_provider_config` (provider_code=SUNAT, environment segun ambiente, endpoints
estandar, credential_ref por convencion) en el flujo de onboarding. Es una mejora de la
integracion auth/registration -> fiscal; no esta hecha. Con eso, una empresa nueva queda lista
para emitir tras subir su certificado, sin tocar la config manualmente.

## OSE (futuro, no ahora)

No tienes cuenta OSE, asi que SUNAT directo es el camino. Cuando un cliente exija un OSE
(o por volumen/SLA), se agrega un `OseFiscalSender` (`@Component` con `supports("NUBEFACT")`
u otro) y se crea un `fiscal_provider_config` con ese `provider_code` desde el admin — **sin
tocar el ruteo**. La arquitectura ya lo soporta.
