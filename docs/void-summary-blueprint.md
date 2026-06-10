I now have everything I need: exact field names, the entity has `lines`, `relatedDocument`, `relatedDocumentTypeCode/Number`, `igvAmount`, `taxableAmount`, `exemptAmount`, `unaffectedAmount`, `totalAmount`, no `voidReason`, no `relatedDocumentIssueDate`, no `metadata`. The sender `send()` does ZIP+store internally and is hardwired to `sendBill` + CDR parsing. I have all firmas to write a precise, compilable blueprint.

Producing the blueprint now.

---

# BLUEPRINT DE IMPLEMENTACIÓN — VOID (Comunicación de Baja / RA) y Resumen Diario (RC) en `fiscal-service`

Raíz del módulo: `C:/Users/User/TARGET/projects/IdeaProjects/point-of-sale/fiscal-service/src/main/java/com/qespe/fiscal_service/`

---

## 1. Resumen de viabilidad

### Lo que se implementa SIN fricción siguiendo el patrón existente

| Pieza | Por qué es directo |
|---|---|
| **Estrategias XML `PeruVoidedXmlStrategy` y `PeruSummaryXmlStrategy`** | El dispatch (`PeruUblFiscalXmlBuilder`) autodescubre cualquier `@Component` que implemente `PeruUblDocumentXmlStrategy` vía `List<>` inyectada y filtra por `supports(String)`. Basta crear 2 beans nuevos. **No se toca builder, interfaz ni enum.** El enum `FiscalDocumentType` ya tiene `VOID` y `DAILY_SUMMARY`. |
| **DOM con `XmlDomUtils`** | `createDocument()`, `append()`, `appendAmount()`, `decimal()` cubren todo lo de RA/RC. Solo hay que respetar el orden de hijos (UBL es `xsd:sequence`). |
| **Estado `TICKETED` y `queryStatus()`** | El flujo async ya existe: `SENT → TICKETED → {ACCEPTED, REJECTED, OBSERVED}`. `queryStatus()` del service (líneas 240-304) y `getStatus` del sender (`buildGetStatusEnvelope`) **son reutilizables tal cual** — el ticket de summary se consulta igual que el de boleta. `SendResult.ticketed()` ya existe. |

### Lo que requiere cambios más profundos (honestidad)

| Gap | Profundidad | Decisión recomendada |
|---|---|---|
| **`createRoot()` solo declara `cac/cbc/ext`** | RA/RC necesitan namespace por defecto propio (`VoidedDocuments-1` / `SummaryDocuments-1`) + prefijos `ds:` y `sac:`. `createRoot` no los declara. | Crear un helper `createSummaryRoot(...)` en `XmlDomUtils` (sin tocar el existente, para no contaminar los 4 comprobantes) que declare los 6 namespaces. Añadir constantes `UBL_VOIDED`, `UBL_SUMMARY`, `SAC`, `DS` en `PeruUblNamespaces`. |
| **Sender NO soporta `sendSummary`** | `send()` está cableado a `<ser:sendBill>` + parser que exige `applicationResponse`/CDR inline (líneas 222, 292-295). La respuesta de `sendSummary` trae `<ticket>`, no CDR → hoy caería en `INVALID_RESPONSE`/ERROR. | Añadir `sendSummary(...)` al puerto `FiscalSenderPort` + `buildSendSummaryEnvelope()` + `parseSendSummaryResponse()` que extraiga `<ticket>` y devuelva `SendResult.TICKETED`. |
| **`process()` no rutea por `documentType`** | Pipeline lineal asume `sendBill` síncrono; llama `sender.send()` incondicionalmente (línea 120). | Ramificar antes de la línea 120: si `VOID`/`DAILY_SUMMARY` → `sender.sendSummary(...)` (siempre TICKETED). El resto del pipeline (XML→firma→ZIP) se reusa. |
| **Naming de archivo** | `buildOfficialDocumentBaseName` produce `RUC-tipo-serie-numero` (líneas 188-197). RA/RC usan `RUC-RA-YYYYMMDD-NNN` / `RUC-RC-YYYYMMDD-NNN`. | Naming específico para summaries dentro del método `sendSummary` del sender. |
| **State machine: `VOIDED` huérfano** | `VOID_PENDING` y `VOIDED` existen en el enum pero **no tienen transiciones** en `ALLOWED`. `assertTransition` reventaría. `isTerminalOrAdvanced` ya marca `VOIDED` terminal pero no hay camino para llegar. | Decidir el modelo semántico (ver §5). Recomendado MVP: el **documento RA/RC** sigue el flujo estándar terminando en `ACCEPTED` (no inventar estados nuevos para el documento-resumen). La marca `VOIDED` se aplica al **comprobante original** referenciado, en un paso aparte tras CDR aceptado. |
| **Datos faltantes** | `voidReason` (motivo de baja) no existe como columna. `relatedDocumentIssueDate` no existe. Filtro por `documentTypeCode` y agregación para RC no existen. Vínculo boleta→resumen no existe. | Ver §6. Requiere migración Flyway/Liquibase + cambios en request DTO. |

### Qué NO se puede validar sin homologación SUNAT

- **La firma digital (`ds:Signature`) es el #1 motivo de rechazo.** El blueprint deja `ext:ExtensionContent` vacío (como ya hace `appendUblCoreHeaders`); el firmador (`FiscalSignerPort`) la inyecta. **No se puede confirmar que la firma enveloped sea válida para RA/RC sin enviar a SUNAT beta.**
- **El cuadre de montos del RC** (`TotalAmount = Σ bases + Σ tributos − descuentos`) y la coherencia IGV ≈ 18% **solo se validan contra SUNAT** (errores 3xxx). El XML puede ser estructuralmente correcto y aun así rechazarse por descuadre.
- **`ReferenceDate` vs `IssueDate`**, formato/unicidad de `cbc:ID`, plazo de 7 días: validables por inspección, pero el rechazo real lo dicta SUNAT.
- **`UBLVersionID=2.0` + `CustomizationID=1.0`** (¡distinto de los comprobantes 2.1/2.0!): hay que sobrescribir, no reusar `appendUblCoreHeaders` que emite 2.1/2.0.

**Conclusión:** las 2 estrategias XML + wiring del sender + ruteo en `process()` son implementables ya y de bajo riesgo de compilación. La validación funcional final (firma + cuadre RC + aceptación) **queda pendiente de homologación en SUNAT beta** y debe marcarse como criterio de "Done" externo.

---

## 2. Archivos a crear / modificar (rutas exactas)

Prefijo común: `.../com/qespe/fiscal_service/`

### Crear

| Archivo | Propósito |
|---|---|
| `infrastructure/engine/xml/peru/strategy/PeruVoidedXmlStrategy.java` | Estrategia `supports("VOID")` → `<VoidedDocuments>` (RA) |
| `infrastructure/engine/xml/peru/strategy/PeruSummaryXmlStrategy.java` | Estrategia `supports("DAILY_SUMMARY")` → `<SummaryDocuments>` (RC) |
| `core/dto/document/FiscalSummaryLineView.java` *(opcional)* | Vista de cada boleta que abarca un RC (si se persiste el detalle) |
| `db/migration/V__add_void_summary_fields.sql` *(o equivalente Liquibase)* | Columnas nuevas (§6) |

### Modificar

| Archivo | Cambio |
|---|---|
| `infrastructure/engine/xml/peru/util/PeruUblNamespaces.java` | Añadir `UBL_VOIDED`, `UBL_SUMMARY`, `SAC`, `DS` |
| `infrastructure/engine/xml/peru/util/XmlDomUtils.java` | Añadir `createSummaryRoot(doc, ns, localName)` (6 namespaces) |
| `core/port/out/FiscalSenderPort.java` | Añadir `SendResult sendSummary(...)` |
| `infrastructure/engine/sunat/SunatSoapFiscalSender.java` | Implementar `sendSummary`: envelope `<ser:sendSummary>`, parser de `<ticket>`, naming `RUC-RA/RC-YYYYMMDD-NNN` |
| `core/usecase/engine/FiscalDocumentProcessingService.java` | Ramificar en `process()` por `documentType` antes de `sender.send()` |
| `core/domain/engine/FiscalDocumentStateMachine.java` | (Solo si se adopta `VOID_PENDING`/`VOIDED`) añadir transiciones |
| `infrastructure/persistence/entity/FiscalDocumentEntity.java` | Campos `voidReason`, `relatedDocumentIssueDate`, `summaryId`/`includedInSummaryAt` (§6) |
| `infrastructure/persistence/repository/FiscalDocumentSpecifications.java` | Filtro `documentTypeCode` + (opcional) por `issueDate` exacto |
| `core/dto/document/FiscalDocumentReserveRequest.java` | Campos `voidReason`, `relatedDocumentIssueDate` |

---

## 3. Skeleton de cada estrategia nueva

### 3.0 Prerrequisito — `PeruUblNamespaces.java`

```java
// añadir al final de la clase
public static final String UBL_VOIDED  = "urn:sunat:names:specification:ubl:peru:schema:xsd:VoidedDocuments-1";
public static final String UBL_SUMMARY = "urn:sunat:names:specification:ubl:peru:schema:xsd:SummaryDocuments-1";
public static final String SAC = "urn:sunat:names:specification:ubl:peru:schema:xsd:SunatAggregateComponents-1";
public static final String DS  = "http://www.w3.org/2000/09/xmldsig#";
```

### 3.0b Prerrequisito — `XmlDomUtils.createSummaryRoot(...)`

`createRoot` solo declara `cac/cbc/ext`. RA/RC necesitan además `ds` y `sac`. Añadir un método nuevo (no tocar `createRoot`):

```java
public static Element createSummaryRoot(Document doc, String namespace, String localName) {
    Element root = doc.createElementNS(namespace, localName);
    root.setAttribute("xmlns", namespace);
    root.setAttribute("xmlns:cac", PeruUblNamespaces.CAC);
    root.setAttribute("xmlns:cbc", PeruUblNamespaces.CBC);
    root.setAttribute("xmlns:ext", PeruUblNamespaces.EXT);
    root.setAttribute("xmlns:ds",  PeruUblNamespaces.DS);
    root.setAttribute("xmlns:sac", PeruUblNamespaces.SAC);
    doc.appendChild(root);
    return root;
}
```

> Nota: NO se puede reusar `appendUblCoreHeaders` (emite `UBLVersionID=2.1` / `CustomizationID=2.0`; RA/RC exigen **2.0 / 1.0**). El skeleton emite la cabecera a mano. El `ext:ExtensionContent` se deja **vacío** — el firmador inyecta el `ds:Signature` ahí (igual que hoy). El orden de hijos respeta el `xsd:sequence` del mapeo 5.

---

### 3.1 `PeruVoidedXmlStrategy` (RA — Comunicación de Baja)

```java
package com.qespe.fiscal_service.infrastructure.engine.xml.peru.strategy;

import com.qespe.fiscal_service.core.domain.engine.EmitterContext;
import com.qespe.fiscal_service.infrastructure.engine.xml.peru.util.PeruUblNamespaces;
import com.qespe.fiscal_service.infrastructure.engine.xml.peru.util.XmlDomUtils;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.time.LocalDate;

@Component
public class PeruVoidedXmlStrategy extends BasePeruUblDocumentXmlStrategy {

    @Override
    public boolean supports(String documentType) {
        return "VOID".equalsIgnoreCase(documentType);
    }

    @Override
    public Document build(FiscalDocumentEntity document, EmitterContext emitterContext) {
        Document xml = XmlDomUtils.createDocument();
        Element root = XmlDomUtils.createSummaryRoot(xml, PeruUblNamespaces.UBL_VOIDED, "VoidedDocuments");

        // ext:UBLExtensions/ext:UBLExtension/ext:ExtensionContent  (vacío -> firma se inyecta aquí) [CRÍTICO]
        Element extensions = XmlDomUtils.append(xml, root, PeruUblNamespaces.EXT, "ext:UBLExtensions", null);
        Element extension  = XmlDomUtils.append(xml, extensions, PeruUblNamespaces.EXT, "ext:UBLExtension", null);
        XmlDomUtils.append(xml, extension, PeruUblNamespaces.EXT, "ext:ExtensionContent", null);

        // Cabecera RA — OJO: 2.0 / 1.0 (NO 2.1/2.0) [CRÍTICO]
        XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:UBLVersionID", "2.0");
        XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:CustomizationID", "1.0");
        XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:ID", document.getFullNumber()); // "RA-YYYYMMDD-NNN" [CRÍTICO formato y unicidad]

        // ReferenceDate = fecha de EMISIÓN del/los comprobantes anulados [CRÍTICO]
        LocalDate referenceDate = resolveReferenceDate(document);
        XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:ReferenceDate", referenceDate.toString());
        // IssueDate = fecha de GENERACIÓN del RA [CRÍTICO]
        XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:IssueDate", document.getIssueDate().toString());

        // cac:Signature (metadatos de firma)
        appendSignaturePlaceholder(xml, root, emitterContext);

        // cac:AccountingSupplierParty (EMISOR) [CRÍTICO]
        appendVoidedSupplier(xml, root, emitterContext);

        // sac:VoidedDocumentsLine (1..N) — una por comprobante anulado
        int lineId = 1;
        for (var line : document.getLines()) {
            Element vLine = XmlDomUtils.append(xml, root, PeruUblNamespaces.SAC, "sac:VoidedDocumentsLine", null);
            XmlDomUtils.append(xml, vLine, PeruUblNamespaces.CBC, "cbc:LineID", String.valueOf(lineId++)); // [CRÍTICO único/consecutivo]
            // Catálogo 01: 01 fact, 03 boleta, 07 NC, 08 ND [CRÍTICO]
            XmlDomUtils.append(xml, vLine, PeruUblNamespaces.CBC, "cbc:DocumentTypeCode", safe(document.getRelatedDocumentTypeCode()));
            // serie + número del doc anulado (sac:) [CRÍTICO]
            String[] serieNum = splitSerieNumero(safe(document.getRelatedDocumentNumber(), document.getFullNumber()));
            XmlDomUtils.append(xml, vLine, PeruUblNamespaces.SAC, "sac:DocumentSerialID", serieNum[0]);
            XmlDomUtils.append(xml, vLine, PeruUblNamespaces.SAC, "sac:DocumentNumberID", serieNum[1]);
            // motivo 1..250 chars, NO vacío [CRÍTICO]
            XmlDomUtils.append(xml, vLine, PeruUblNamespaces.SAC, "sac:VoidReasonDescription",
                    safe(document.getVoidReason(), "ANULACION"));
        }
        return xml;
    }

    // --- helpers locales (no contaminan la base) ---

    private LocalDate resolveReferenceDate(FiscalDocumentEntity document) {
        if (document.getRelatedDocument() != null && document.getRelatedDocument().getIssueDate() != null) {
            return document.getRelatedDocument().getIssueDate();              // camino preferido (navegación)
        }
        if (document.getRelatedDocumentIssueDate() != null) {
            return document.getRelatedDocumentIssueDate();                    // campo plano nuevo (§6)
        }
        return document.getIssueDate(); // fallback; SUNAT puede rechazar si no coincide con emisión real
    }

    private void appendSignaturePlaceholder(Document xml, Element root, EmitterContext ctx) {
        Element sig = XmlDomUtils.append(xml, root, PeruUblNamespaces.CAC, "cac:Signature", null);
        XmlDomUtils.append(xml, sig, PeruUblNamespaces.CBC, "cbc:ID", safe(ctx.documentNumber()));
        Element party = XmlDomUtils.append(xml, sig, PeruUblNamespaces.CAC, "cac:SignatoryParty", null);
        Element pid = XmlDomUtils.append(xml, party, PeruUblNamespaces.CAC, "cac:PartyIdentification", null);
        XmlDomUtils.append(xml, pid, PeruUblNamespaces.CBC, "cbc:ID", safe(ctx.documentNumber()));
        Element pname = XmlDomUtils.append(xml, party, PeruUblNamespaces.CAC, "cac:PartyName", null);
        XmlDomUtils.append(xml, pname, PeruUblNamespaces.CBC, "cbc:Name", safe(ctx.legalName()));
        Element attach = XmlDomUtils.append(xml, sig, PeruUblNamespaces.CAC, "cac:DigitalSignatureAttachment", null);
        Element ext = XmlDomUtils.append(xml, attach, PeruUblNamespaces.CAC, "cac:ExternalReference", null);
        XmlDomUtils.append(xml, ext, PeruUblNamespaces.CBC, "cbc:URI", "#SignatureSP");
    }

    private void appendVoidedSupplier(Document xml, Element root, EmitterContext ctx) {
        Element supplier = XmlDomUtils.append(xml, root, PeruUblNamespaces.CAC, "cac:AccountingSupplierParty", null);
        XmlDomUtils.append(xml, supplier, PeruUblNamespaces.CBC, "cbc:CustomerAssignedAccountID", safe(ctx.documentNumber())); // RUC
        XmlDomUtils.append(xml, supplier, PeruUblNamespaces.CBC, "cbc:AdditionalAccountID", "6"); // catálogo 06 = RUC [CRÍTICO]
        Element party = XmlDomUtils.append(xml, supplier, PeruUblNamespaces.CAC, "cac:Party", null);
        Element legal = XmlDomUtils.append(xml, party, PeruUblNamespaces.CAC, "cac:PartyLegalEntity", null);
        XmlDomUtils.append(xml, legal, PeruUblNamespaces.CBC, "cbc:RegistrationName", safe(ctx.legalName()));
    }

    private String[] splitSerieNumero(String full) {
        if (full != null && full.contains("-")) {
            int i = full.indexOf('-');
            return new String[]{ full.substring(0, i), stripLeadingZeros(full.substring(i + 1)) };
        }
        return new String[]{ full == null ? "-" : full, "0" };
    }

    private String stripLeadingZeros(String n) {
        String s = n.replaceFirst("^0+", "");
        return s.isEmpty() ? "0" : s;
    }
}
```

> **Decisión de modelado VOID:** aquí cada `FiscalDocumentLineEntity` representa un comprobante anulado. Para el caso simple "1 RA anula 1 comprobante" se usa `relatedDocument`/`relatedDocumentNumber` directamente (una sola línea). Si se quiere agrupar N comprobantes de la misma fecha en un RA, hay que poblar `document.getLines()` con esos N originales — eso es un cambio de cómo se construye el documento RA aguas arriba (fuera del XML).

---

### 3.2 `PeruSummaryXmlStrategy` (RC — Resumen Diario)

```java
package com.qespe.fiscal_service.infrastructure.engine.xml.peru.strategy;

import com.qespe.fiscal_service.core.domain.engine.EmitterContext;
import com.qespe.fiscal_service.infrastructure.engine.xml.peru.util.PeruUblNamespaces;
import com.qespe.fiscal_service.infrastructure.engine.xml.peru.util.XmlDomUtils;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentLineEntity;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@Component
public class PeruSummaryXmlStrategy extends BasePeruUblDocumentXmlStrategy {

    @Override
    public boolean supports(String documentType) {
        return "DAILY_SUMMARY".equalsIgnoreCase(documentType);
    }

    @Override
    public Document build(FiscalDocumentEntity document, EmitterContext emitterContext) {
        Document xml = XmlDomUtils.createDocument();
        Element root = XmlDomUtils.createSummaryRoot(xml, PeruUblNamespaces.UBL_SUMMARY, "SummaryDocuments");

        // ext:UBLExtensions ... ExtensionContent (vacío -> firma) [CRÍTICO]
        Element extensions = XmlDomUtils.append(xml, root, PeruUblNamespaces.EXT, "ext:UBLExtensions", null);
        Element extension  = XmlDomUtils.append(xml, extensions, PeruUblNamespaces.EXT, "ext:UBLExtension", null);
        XmlDomUtils.append(xml, extension, PeruUblNamespaces.EXT, "ext:ExtensionContent", null);

        // Cabecera RC — 2.0 / 1.0 [CRÍTICO]
        XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:UBLVersionID", "2.0");
        XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:CustomizationID", "1.0");
        XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:ID", document.getFullNumber()); // "RC-YYYYMMDD-NNN" [CRÍTICO]
        // ReferenceDate = fecha de EMISIÓN de las boletas reportadas [CRÍTICO]
        XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:ReferenceDate", resolveReferenceDate(document));
        // IssueDate = fecha de GENERACIÓN del resumen [CRÍTICO]
        XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:IssueDate", document.getIssueDate().toString());

        appendSignaturePlaceholder(xml, root, emitterContext);
        appendSummarySupplier(xml, root, emitterContext);

        // sac:SummaryDocumentsLine (1..N) — una por boleta / NC-ND de boleta
        int lineId = 1;
        for (FiscalDocumentLineEntity line : document.getLines()) {
            Element sLine = XmlDomUtils.append(xml, root, PeruUblNamespaces.SAC, "sac:SummaryDocumentsLine", null);
            XmlDomUtils.append(xml, sLine, PeruUblNamespaces.CBC, "cbc:LineID", String.valueOf(lineId++)); // [CRÍTICO]
            // Catálogo 01 (03 boleta / 07 NC / 08 ND) — mapear desde el line (ver §6 datos)
            XmlDomUtils.append(xml, sLine, PeruUblNamespaces.CBC, "cbc:DocumentTypeCode", safe(line.getTaxAffectationCode())); // PLACEHOLDER: requiere campo dedicado
            // cbc:ID de línea = SERIE-CORRELATIVO (p.ej. B001-25) [CRÍTICO formato]
            XmlDomUtils.append(xml, sLine, PeruUblNamespaces.CBC, "cbc:ID", safe(line.getDescription())); // PLACEHOLDER: serie-correlativo de la boleta

            // cac:AccountingCustomerParty (cliente, puede ser genérico)
            Element customer = XmlDomUtils.append(xml, sLine, PeruUblNamespaces.CAC, "cac:AccountingCustomerParty", null);
            XmlDomUtils.append(xml, customer, PeruUblNamespaces.CBC, "cbc:CustomerAssignedAccountID", "00000000");
            XmlDomUtils.append(xml, customer, PeruUblNamespaces.CBC, "cbc:AdditionalAccountID", "0"); // catálogo 06

            // cac:Status/cbc:ConditionCode  1=Adicionar 2=Modificar 3=Anular (catálogo 19) [CRÍTICO]
            Element status = XmlDomUtils.append(xml, sLine, PeruUblNamespaces.CAC, "cac:Status", null);
            XmlDomUtils.append(xml, status, PeruUblNamespaces.CBC, "cbc:ConditionCode", resolveConditionCode(line));

            // sac:TotalAmount @currencyID [CRÍTICO]
            Element total = XmlDomUtils.append(xml, sLine, PeruUblNamespaces.SAC, "sac:TotalAmount",
                    XmlDomUtils.decimal(line.getTotalAmount()));
            total.setAttribute("currencyID", document.getCurrencyCode());

            // sac:BillingPayment (1..N) — un nodo por tipo de operación (catálogo 17) [CRÍTICO]
            appendBillingPayment(xml, sLine, document, "01", line.getTaxableBaseAmount()); // 01 = gravadas
            // si hubiese exoneradas/inafectas: appendBillingPayment(..., "02", ...) / ("03", ...)

            // cac:TaxTotal (1..N) — IGV (catálogo 05) [CRÍTICO]
            appendSummaryTax(xml, sLine, document, line);
        }
        return xml;
    }

    private void appendBillingPayment(Document xml, Element sLine, FiscalDocumentEntity doc, String instructionId, java.math.BigDecimal amount) {
        Element bp = XmlDomUtils.append(xml, sLine, PeruUblNamespaces.SAC, "sac:BillingPayment", null);
        Element paid = XmlDomUtils.append(xml, bp, PeruUblNamespaces.CBC, "cbc:PaidAmount", XmlDomUtils.decimal(amount));
        paid.setAttribute("currencyID", doc.getCurrencyCode());
        XmlDomUtils.append(xml, bp, PeruUblNamespaces.CBC, "cbc:InstructionID", instructionId); // catálogo 17
    }

    private void appendSummaryTax(Document xml, Element sLine, FiscalDocumentEntity doc, FiscalDocumentLineEntity line) {
        Element taxTotal = XmlDomUtils.append(xml, sLine, PeruUblNamespaces.CAC, "cac:TaxTotal", null);
        Element taxAmt = XmlDomUtils.append(xml, taxTotal, PeruUblNamespaces.CBC, "cbc:TaxAmount", XmlDomUtils.decimal(line.getTaxAmount()));
        taxAmt.setAttribute("currencyID", doc.getCurrencyCode());

        Element sub = XmlDomUtils.append(xml, taxTotal, PeruUblNamespaces.CAC, "cac:TaxSubtotal", null);
        Element base = XmlDomUtils.append(xml, sub, PeruUblNamespaces.CBC, "cbc:TaxableAmount", XmlDomUtils.decimal(line.getTaxableBaseAmount()));
        base.setAttribute("currencyID", doc.getCurrencyCode());
        Element cat = XmlDomUtils.append(xml, sub, PeruUblNamespaces.CAC, "cac:TaxCategory", null);
        Element scheme = XmlDomUtils.append(xml, cat, PeruUblNamespaces.CAC, "cac:TaxScheme", null);
        XmlDomUtils.append(xml, scheme, PeruUblNamespaces.CBC, "cbc:ID", "1000");   // catálogo 05: IGV [CRÍTICO]
        XmlDomUtils.append(xml, scheme, PeruUblNamespaces.CBC, "cbc:Name", "IGV");
        XmlDomUtils.append(xml, scheme, PeruUblNamespaces.CBC, "cbc:TaxTypeCode", "VAT");
    }

    private String resolveConditionCode(FiscalDocumentLineEntity line) {
        // 1=Adicionar (normal). Mapear 3=Anular cuando la boleta ya fue informada en RC previo (§6).
        return "1";
    }

    private String resolveReferenceDate(FiscalDocumentEntity document) {
        // Todas las boletas del RC tienen la MISMA fecha de emisión = ReferenceDate [CRÍTICO]
        if (document.getRelatedDocumentIssueDate() != null) {
            return document.getRelatedDocumentIssueDate().toString();
        }
        return document.getIssueDate().minusDays(1).toString(); // fallback; ajustar a la fecha real de boletas
    }

    private void appendSignaturePlaceholder(Document xml, Element root, EmitterContext ctx) {
        Element sig = XmlDomUtils.append(xml, root, PeruUblNamespaces.CAC, "cac:Signature", null);
        XmlDomUtils.append(xml, sig, PeruUblNamespaces.CBC, "cbc:ID", safe(ctx.documentNumber()));
        Element party = XmlDomUtils.append(xml, sig, PeruUblNamespaces.CAC, "cac:SignatoryParty", null);
        Element pid = XmlDomUtils.append(xml, party, PeruUblNamespaces.CAC, "cac:PartyIdentification", null);
        XmlDomUtils.append(xml, pid, PeruUblNamespaces.CBC, "cbc:ID", safe(ctx.documentNumber()));
        Element pname = XmlDomUtils.append(xml, party, PeruUblNamespaces.CAC, "cac:PartyName", null);
        XmlDomUtils.append(xml, pname, PeruUblNamespaces.CBC, "cbc:Name", safe(ctx.legalName()));
        Element attach = XmlDomUtils.append(xml, sig, PeruUblNamespaces.CAC, "cac:DigitalSignatureAttachment", null);
        Element ext = XmlDomUtils.append(xml, attach, PeruUblNamespaces.CAC, "cac:ExternalReference", null);
        XmlDomUtils.append(xml, ext, PeruUblNamespaces.CBC, "cbc:URI", "#SignatureSP");
    }

    private void appendSummarySupplier(Document xml, Element root, EmitterContext ctx) {
        Element supplier = XmlDomUtils.append(xml, root, PeruUblNamespaces.CAC, "cac:AccountingSupplierParty", null);
        XmlDomUtils.append(xml, supplier, PeruUblNamespaces.CBC, "cbc:CustomerAssignedAccountID", safe(ctx.documentNumber()));
        XmlDomUtils.append(xml, supplier, PeruUblNamespaces.CBC, "cbc:AdditionalAccountID", "6"); // RUC [CRÍTICO]
        Element party = XmlDomUtils.append(xml, supplier, PeruUblNamespaces.CAC, "cac:Party", null);
        Element legal = XmlDomUtils.append(xml, party, PeruUblNamespaces.CAC, "cac:PartyLegalEntity", null);
        XmlDomUtils.append(xml, legal, PeruUblNamespaces.CBC, "cbc:RegistrationName", safe(ctx.legalName()));
    }
}
```

> **Placeholders marcados en el RC que dependen de §6:** `cbc:DocumentTypeCode` y `cbc:ID` de cada línea necesitan campos dedicados en `FiscalDocumentLineEntity` (tipo de comprobante de la boleta y su serie-correlativo). Hoy `FiscalDocumentLineEntity` modela líneas de producto, **no** boletas. El RC requiere que `document.getLines()` represente **boletas**, no ítems → ver riesgo en §7. El cuadre `TotalAmount = bases + IGV − descuentos` **se valida solo en SUNAT**.

---

## 4. Wiring (registro/dispatch)

**No se modifica `PeruUblFiscalXmlBuilder`, ni `PeruUblDocumentXmlStrategy`, ni el enum.** El dispatch es por inyección de `List<PeruUblDocumentXmlStrategy>` + `filter(supports).findFirst()`. Al anotar las 2 clases con `@Component` y extender `BasePeruUblDocumentXmlStrategy`, Spring las autodescubre.

Garantía de no-rotura:
- `supports("VOID")` y `supports("DAILY_SUMMARY")` matchean strings únicos → `findFirst()` no colisiona con INVOICE/RECEIPT/CREDIT_NOTE/DEBIT_NOTE.
- El `documentType` que devuelve `FiscalDocumentEntity.getDocumentType()` para estos documentos debe ser exactamente `"VOID"` / `"DAILY_SUMMARY"` (coincidente con `FiscalDocumentType.name()`). Verificar en el alta del documento.

Único punto a confirmar: que el paquete `infrastructure.engine.xml.peru.strategy` esté bajo component-scan (lo está; las 4 estrategias actuales viven ahí).

---

## 5. Flujo async (sender / process / state machine)

### 5.1 `FiscalSenderPort` — añadir método

```java
SendResult sendSummary(FiscalDocumentEntity document, SignedArtifactResult signedArtifactResult, ProviderContext providerContext);
```

### 5.2 `SunatSoapFiscalSender` — implementar `sendSummary`

Reutiliza ZIP + storage + RestTemplate de `send()`. Difiere en: (a) operación SOAP `<ser:sendSummary>`, (b) naming `RUC-RA/RC-YYYYMMDD-NNN`, (c) parser que extrae `<ticket>` → `SendResult.TICKETED`.

```java
@Override
public SendResult sendSummary(FiscalDocumentEntity document, SignedArtifactResult signed, ProviderContext ctx) {
    validateInputs(signed, ctx);
    SunatCredentials credentials = resolveCredentials(ctx);
    String baseName = buildSummaryDocumentBaseName(document); // RUC-RA/RC-YYYYMMDD-NNN
    String xmlFilename = baseName + ".xml";
    String zipFilename = baseName + ".zip";
    byte[] zipBytes = zipSignedXml(signed, xmlFilename);
    StoredArtifactResult storedZip = artifactStoragePort.storeZip(document, zipBytes, zipFilename);
    String soapBody = buildSendSummaryEnvelope(credentials, zipFilename, zipBytes);

    try {
        ResponseEntity<String> response = buildRestTemplate(ctx.timeoutMs())
                .postForEntity(ctx.endpointSubmitUrl(), buildHttpEntity(soapBody), String.class);
        return parseSendSummaryResponse(document, response.getBody(), storedZip);
    } catch (HttpStatusCodeException ex) {
        return parseSendSummaryResponse(document, ex.getResponseBodyAsString(), storedZip);
    } catch (BusinessException ex) {
        throw ex;
    } catch (Exception ex) {
        return new SendResult(FiscalDocumentStatus.ERROR, "SEND_TRANSPORT_ERROR", "SUNAT sendSummary failed",
                null, storedZip.path(), storedZip.sha256(), null, null, null, null, null, null, true);
    }
}

private String buildSendSummaryEnvelope(SunatCredentials c, String zipFilename, byte[] zipBytes) {
    String content = Base64.getEncoder().encodeToString(zipBytes);
    // idéntico a buildSoapEnvelope pero <ser:sendSummary>
    return """
            <?xml version="1.0" encoding="UTF-8"?>
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                              xmlns:ser="http://service.sunat.gob.pe"
                              xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
              <soapenv:Header><wsse:Security><wsse:UsernameToken>
                <wsse:Username>%s</wsse:Username><wsse:Password>%s</wsse:Password>
              </wsse:UsernameToken></wsse:Security></soapenv:Header>
              <soapenv:Body>
                <ser:sendSummary><fileName>%s</fileName><contentFile>%s</contentFile></ser:sendSummary>
              </soapenv:Body>
            </soapenv:Envelope>
            """.formatted(escapeXml(c.username()), escapeXml(c.password()), escapeXml(zipFilename), content);
}

private SendResult parseSendSummaryResponse(FiscalDocumentEntity document, String body, StoredArtifactResult storedZip) {
    StoredArtifactResult storedResponse = storeResponseIfPresent(document, body);
    if (body == null || body.isBlank()) {
        return new SendResult(FiscalDocumentStatus.ERROR, "EMPTY_RESPONSE", "SUNAT returned empty response",
                null, storedZip.path(), storedZip.sha256(), responsePath(storedResponse), responseHash(storedResponse),
                null, null, null, null, true);
    }
    try {
        Document soapDoc = parseXml(body);
        Element fault = firstElementByLocalName(soapDoc.getDocumentElement(), "Fault");
        if (fault != null) {
            return new SendResult(FiscalDocumentStatus.ERROR,
                    blankToDefault(childText(fault, "faultcode"), "SOAP_FAULT"),
                    blankToDefault(childText(fault, "faultstring"), "SUNAT SOAP fault"),
                    null, storedZip.path(), storedZip.sha256(), responsePath(storedResponse), responseHash(storedResponse),
                    null, null, null, null, true);
        }
        // La respuesta de sendSummary trae <ticket>, NO applicationResponse/CDR
        String ticket = textByLocalName(soapDoc.getDocumentElement(), "ticket");
        if (ticket == null || ticket.isBlank()) {
            return new SendResult(FiscalDocumentStatus.ERROR, "INVALID_RESPONSE", "SUNAT sendSummary did not return a ticket",
                    null, storedZip.path(), storedZip.sha256(), responsePath(storedResponse), responseHash(storedResponse),
                    null, null, null, null, true);
        }
        return new SendResult(FiscalDocumentStatus.TICKETED, "98", "SUNAT summary accepted, ticket issued",
                ticket.trim(), storedZip.path(), storedZip.sha256(), responsePath(storedResponse), responseHash(storedResponse),
                null, null, null, null, false);
    } catch (Exception ex) {
        return new SendResult(FiscalDocumentStatus.ERROR, "PARSE_ERROR", "Unable to parse SUNAT sendSummary response",
                null, storedZip.path(), storedZip.sha256(), responsePath(storedResponse), responseHash(storedResponse),
                null, null, null, null, true);
    }
}

private String buildSummaryDocumentBaseName(FiscalDocumentEntity document) {
    String ruc = requireValue(document.getEmitterDocumentNumber(), "Emitter RUC required for summary filename");
    // El ID ya viene como RA-YYYYMMDD-NNN / RC-YYYYMMDD-NNN en fullNumber
    String id = requireValue(document.getFullNumber(), "Summary ID required for filename");
    return safeFilename(ruc) + "-" + safeFilename(id);
}
```

`getStatus`/`queryStatus` del sender **no se tocan** — sirven igual para el ticket de summary.

### 5.3 `process()` — ramificar por `documentType`

El pipeline RESERVED→…→SIGNED→QUEUED_FOR_SEND→SENT es idéntico (XML lo genera la estrategia correcta vía dispatch; la firma y el ZIP no cambian). **Lo único que cambia es la llamada al sender** (línea 120) y que el resultado **siempre** es TICKETED.

Cambio mínimo, reemplazando la línea 120:

```java
boolean isSummary = isSummaryType(document.getDocumentType());
SendResult sendResult = isSummary
        ? sender.sendSummary(document, signedArtifact, providerContext)
        : sender.send(document, signedArtifact, providerContext);
```

con helper:

```java
private boolean isSummaryType(String documentType) {
    return "VOID".equalsIgnoreCase(documentType) || "DAILY_SUMMARY".equalsIgnoreCase(documentType);
}
```

Las ramas de resultado (122-192) **se reusan sin cambios**: `sendResult.ticketed()` (137-153) ya transiciona `SENT → TICKETED` y persiste `authorityTicket`. Como `sendSummary` nunca devuelve `accepted()`, la rama 122 simplemente no se ejecuta para summaries. La resolución final ocurre vía `queryStatus()` (240-304), **reutilizable tal cual**.

### 5.4 State machine

**Camino que YA funciona sin tocar nada (recomendado para MVP):**
`SENT → TICKETED → {ACCEPTED, REJECTED, OBSERVED}` ya está en `ALLOWED`. El documento RA/RC termina en `ACCEPTED` cuando SUNAT acepta el ticket. **No se necesitan `VOID_PENDING`/`VOIDED` para que el documento-resumen funcione.**

**`VOID_PENDING` / `VOIDED` quedan como TODO justificado:** representan el estado del **comprobante original anulado**, no del documento RA. Marcar el original como `VOIDED` es un paso de negocio posterior (tras CDR del RA aceptado) que requiere:
1. Localizar el comprobante original (`relatedDocument`).
2. Transicionarlo a `VOIDED`.

Para habilitarlo más adelante, añadir a `ALLOWED`:
```java
FiscalDocumentStatus.ACCEPTED,    Set.of(FiscalDocumentStatus.VOID_PENDING),
FiscalDocumentStatus.VOID_PENDING, Set.of(FiscalDocumentStatus.VOIDED, FiscalDocumentStatus.ERROR)
```
**Justificación de dejarlo como TODO:** no es necesario para emitir/aceptar el RA/RC en SUNAT; es un refinamiento de trazabilidad del original. Implementarlo ahora añade superficie sin desbloquear la homologación. Documentarlo y diferirlo.

---

## 6. Datos (campos / queries faltantes)

### Para VOID (RA)

| Campo | Acción | Detalle |
|---|---|---|
| `voidReason` (motivo) | **Añadir columna** a `FiscalDocumentEntity` | `@Column(name="void_reason", length=250) private String voidReason;` + getter Lombok. No existe `metadata` JSON en el documento (solo en line/series), así que no hay alternativa limpia. Exponer en el request de baja. |
| `relatedDocumentIssueDate` | **Añadir columna** | `@Column(name="related_document_issue_date") private LocalDate relatedDocumentIssueDate;` Solo necesario si el comprobante anulado puede no existir como `relatedDocument` en BD. Si siempre existe, se obtiene por navegación (`getRelatedDocument().getIssueDate()`) y este campo es opcional. SUNAT exige esa fecha (= `ReferenceDate`). |
| Request de baja | **Añadir campos** a `FiscalDocumentReserveRequest` (o crear request específico) | `voidReason`, `relatedDocumentIssueDate`. El reserve request actual no modela motivo. |

### Para Resumen Diario (RC)

| Gap | Acción |
|---|---|
| Filtro por `documentTypeCode` | Añadir `eq("documentTypeCode", ...)` a `FiscalDocumentSpecifications.byFilters(...)` (el campo ya existe en la entidad). El RC es específico de boletas (03). |
| Query del conjunto del día | Reusar `JpaSpecificationExecutor.findAll(spec, pageable)` con filtro `companyId + environment + series + issueDate == <día> + documentTypeCode == "03"`. Para `issueDate` exacto basta `issueDateFrom == issueDateTo == día` (ya soportado por `byFilters`). |
| Totales (`SUM(totalAmount)`, `SUM(igvAmount)`, `COUNT`) | Calcular en memoria tras listar las boletas, **o** añadir `@Query` agregada `GROUP BY issue_date, series`. Para MVP: en memoria. |
| **Modelado del RC como documento** | El mayor hueco: `document.getLines()` del RC debe representar **boletas**, no ítems de producto. `FiscalDocumentLineEntity` no tiene `documentTypeCode`/`serie-correlativo`/`conditionCode` de boleta. Opciones: (a) crear una proyección/DTO de líneas de resumen que la estrategia consuma (requiere cambiar la firma de `build` o un adaptador), o (b) reusar `FiscalDocumentLineEntity` poblando `description` = "B001-25", `itemCode` = "03", etc. (frágil, los placeholders del §3.2 lo reflejan). **Recomendado: añadir campos dedicados a `FiscalDocumentLineEntity`** (`refDocumentTypeCode`, `refSerieCorrelativo`, `conditionCode`) o un sub-entity `summary_line`. |
| Marca boleta→resumen (anti doble declaración) | Añadir `summaryId` (UUID, FK al doc RC) + `includedInSummaryAt` (Instant) a `FiscalDocumentEntity`. Sin esto, riesgo de declarar una boleta en 2 RC. |

---

## 7. Orden de implementación, riesgos y pendiente de homologación

### Orden recomendado

1. **Namespaces + `createSummaryRoot`** (`PeruUblNamespaces`, `XmlDomUtils`) — base inerte, no rompe nada.
2. **`PeruVoidedXmlStrategy`** (RA es más simple: sin cuadre de montos). Test unitario que serialice el DOM (vía `XmlSerializer` existente) y valide orden de nodos + `2.0/1.0` + `ReferenceDate≠IssueDate`.
3. **Migración de datos VOID** (`voidReason`, `relatedDocumentIssueDate`) + request.
4. **`sendSummary` en puerto + sender** (`buildSendSummaryEnvelope`, `parseSendSummaryResponse`, naming). Test con respuesta SOAP mock que contenga `<ticket>`.
5. **Ruteo en `process()`** (`isSummaryType` + branch). Test: documento VOID termina en `TICKETED` con `authorityTicket` poblado.
6. **Validar end-to-end VOID** contra SUNAT beta (homologación) → ajustar firma/estructura según CDR.
7. **`PeruSummaryXmlStrategy` (RC)** + modelado de líneas-boleta + agregación + filtro `documentTypeCode`. Más complejo por el cuadre.
8. **Validar RC** contra SUNAT beta.
9. (Diferido) `VOID_PENDING`/`VOIDED` para el comprobante original.

### Riesgos

- **Firma digital (RA/RC):** `ext:ExtensionContent` vacío + firma enveloped la inyecta `FiscalSignerPort`. **No verificado para RA/RC** — el firmador puede asumir estructura de Invoice. Riesgo alto: validar que firma el `VoidedDocuments`/`SummaryDocuments` completo y referencia `#SignatureSP`. **Bloqueante de homologación.**
- **Cuadre de montos RC:** `TotalAmount = Σ bases + Σ IGV − descuentos`. Descuadre = rechazo 3xxx. Solo verificable en SUNAT.
- **Modelado de líneas RC:** `FiscalDocumentLineEntity` no encaja con "una línea = una boleta". Los placeholders del §3.2 (`getDescription()` como serie-correlativo, `getTaxAffectationCode()` como tipo) son **provisionales** y deben reemplazarse por campos dedicados antes de homologar.
- **`ReferenceDate` vs `IssueDate`:** confusión = error más común. Asegurar `ReferenceDate` = emisión de comprobantes/boletas; `IssueDate` = generación del RA/RC.
- **Correlativo `NNN` del `cbc:ID`:** debe ser único por (RUC, tipoDoc, fecha) y persistido (`FiscalSeriesEntity` ya da correlativos por `documentTypeCode+series`). No reutilizar IDs aceptados.
- **`createRoot` vs `createSummaryRoot`:** no reusar `createRoot` (no declara `ds`/`sac`) — usar el nuevo helper.

### Pendiente de homologación SUNAT (criterios de "Done" externos, no verificables en código)

1. Firma enveloped válida en RA y RC (cert vigente, referencia correcta).
2. RA aceptado vía `sendSummary` → ticket → `getStatus` → CDR código 0.
3. RC con cuadre de montos correcto e IGV ≈ 18% aceptado.
4. `ReferenceDate`/`IssueDate`/formato `cbc:ID` aceptados sin error de estructura/plazo.

---

**Archivos fuente verificados (rutas absolutas):**
- `.../infrastructure/engine/xml/peru/strategy/{PeruUblDocumentXmlStrategy,BasePeruUblDocumentXmlStrategy,PeruDebitNoteXmlStrategy}.java`
- `.../infrastructure/engine/xml/peru/util/{XmlDomUtils,PeruUblNamespaces,XmlSerializer}.java`
- `.../infrastructure/engine/xml/peru/PeruUblFiscalXmlBuilder.java` (dispatch, no se modifica)
- `.../infrastructure/engine/sunat/SunatSoapFiscalSender.java` (send: 52-74; getStatus: 76-103; sendBill env: 206-234; parse: 263-323; naming: 188-197)
- `.../core/usecase/engine/FiscalDocumentProcessingService.java` (send: línea 120; ramas: 122-192; queryStatus: 240-304)
- `.../core/domain/engine/{FiscalDocumentStateMachine,SendResult,EmitterContext}.java`
- `.../core/port/out/FiscalSenderPort.java`
- `.../infrastructure/persistence/entity/FiscalDocumentEntity.java` (sin `voidReason`/`relatedDocumentIssueDate`/`metadata`/`summaryId`)
- `.../core/domain/enums/{FiscalDocumentType,FiscalDocumentStatus}.java` (VOID, DAILY_SUMMARY, VOID_PENDING, VOIDED, TICKETED confirmados)