package com.qespe.fiscal_service.infrastructure.engine.xml.peru.strategy;

import com.qespe.fiscal_service.core.domain.engine.EmitterContext;
import com.qespe.fiscal_service.infrastructure.engine.xml.peru.util.PeruUblNamespaces;
import com.qespe.fiscal_service.infrastructure.engine.xml.peru.util.XmlDomUtils;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.math.BigDecimal;

/**
 * Estrategia UBL para el <strong>Comprobante de Retencion (catalogo 01 = "20")</strong>
 * — regimen de retenciones del IGV (SUNAT, esquema {@code sunat:Retention 2.1}).
 *
 * <p><strong>A diferencia de RA/RC</strong> (resumenes async via {@code sendSummary}),
 * el Comprobante de Retencion es <em>sincrono</em>: viaja por {@code sendBill} y SUNAT
 * responde con CDR — exactamente el mismo canal/firma/almacenamiento que la factura.
 * Por eso NO esta en {@code isSummaryDocument(...)} y se reutiliza toda la
 * infraestructura existente (firma, ZIP, CDR, estados, reintentos).
 *
 * <p><strong>Diferencias estructurales con un comprobante normal</strong> (motivos de
 * rechazo SUNAT si se confunden):
 * <ul>
 *   <li>Raiz {@code Retention} en su propio namespace + {@code sac}/{@code ds}
 *       ({@link XmlDomUtils#createSunatComprobanteRoot}).</li>
 *   <li>Cabecera UBL <strong>2.1 / CustomizationID 1.0</strong>.</li>
 *   <li>El emisor es {@code cac:AgentParty} (agente de retencion) y el cliente es
 *       {@code cac:ReceiverParty} (proveedor al que se le retiene) — NO
 *       AccountingSupplierParty/CustomerParty.</li>
 *   <li>{@code cbc:SUNATRetentionSystemCode} (regimen, catalogo 23) y
 *       {@code cbc:SUNATRetentionPercent} (tasa, p.ej. 3.00).</li>
 *   <li>{@code sac:SUNATRetentionDocumentReference} por cada comprobante de pago
 *       sobre el que se aplico la retencion, con {@code sac:SUNATRetentionInformation}
 *       (importe retenido, fecha y, si aplica, tipo de cambio).</li>
 * </ul>
 *
 * <p><strong>Datos propios todavia no modelados en el request/entidad.</strong> El
 * {@code FiscalDocumentReserveRequest} actual no trae regimen, tasa, importe retenido,
 * importe total del comprobante relacionado ni fecha de pago. Para no bloquear la
 * estructura del XML se reutilizan los campos disponibles con fallbacks razonables
 * (ver los TODO). Cuando el upstream (sales/AP) envie estos datos, reemplazar los
 * fallbacks por los valores reales.
 *
 * <p><strong>Pendiente de homologacion SUNAT:</strong> la firma {@code ds:Signature}
 * (se inyecta en {@code ext:ExtensionContent} por el firmador), el formato del ID y
 * la consistencia de montos/tasas SOLO se validan enviando a SUNAT beta.
 */
@Component
public class PeruRetentionXmlStrategy extends BasePeruUblDocumentXmlStrategy {

    // Catalogo 23 SUNAT: 01 = Tasa 3% (regimen general de retenciones). TODO: debe
    // venir del request cuando exista; por defecto regimen general.
    private static final String DEFAULT_RETENTION_SYSTEM_CODE = "01";
    // Tasa del regimen 01 (3%). TODO: parametrizar por request/regimen.
    private static final BigDecimal DEFAULT_RETENTION_PERCENT = new BigDecimal("3.00");

    @Override
    public boolean supports(String documentType) {
        return "RETENTION".equalsIgnoreCase(documentType);
    }

    @Override
    public Document build(FiscalDocumentEntity document, EmitterContext emitterContext) {
        Document xml = XmlDomUtils.createDocument();
        Element root = XmlDomUtils.createSunatComprobanteRoot(xml, PeruUblNamespaces.UBL_RETENTION, "Retention");

        // ext:UBLExtensions/ext:UBLExtension/ext:ExtensionContent (vacio; el firmador
        // inyecta aqui el ds:Signature). CRITICO que exista aunque vacio.
        Element extensions = XmlDomUtils.append(xml, root, PeruUblNamespaces.EXT, "ext:UBLExtensions", null);
        Element extension = XmlDomUtils.append(xml, extensions, PeruUblNamespaces.EXT, "ext:UBLExtension", null);
        XmlDomUtils.append(xml, extension, PeruUblNamespaces.EXT, "ext:ExtensionContent", null);

        // Cabecera: 2.1 / 1.0.
        XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:UBLVersionID", "2.1");
        XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:CustomizationID", "1.0");
        XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:ID", document.getFullNumber());
        XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:IssueDate", document.getIssueDate().toString());
        if (document.getIssueTime() != null) {
            XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:IssueTime", document.getIssueTime().toString());
        }

        appendSignaturePlaceholder(xml, root, emitterContext);

        // Agente de retencion (emisor).
        appendAgentParty(xml, root, document, emitterContext);
        // Proveedor al que se le retiene (cliente del request).
        appendReceiverParty(xml, root, document);

        // Regimen y tasa de retencion. TODO: tomar del request cuando exista
        // (operationTypeCode podria reutilizarse para el regimen).
        String systemCode = safe(document.getOperationTypeCode(), DEFAULT_RETENTION_SYSTEM_CODE);
        XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:SUNATRetentionSystemCode", systemCode);
        XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:SUNATRetentionPercent",
                DEFAULT_RETENTION_PERCENT.toPlainString());
        XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:Note", "Comprobante de Retencion");

        String currency = safe(document.getCurrencyCode(), "PEN");
        // Importe total retenido (cat. comprobante): se mapea taxAmount como importe
        // retenido del documento. TODO: usar campo propio "retainedAmount" cuando exista.
        XmlDomUtils.appendAmount(xml, root, "cbc:TotalInvoiceAmount", currency, nz(document.getTaxAmount()));
        // sac:SUNATTotalCashed = importe total pagado al proveedor (neto). Reutilizamos
        // totalAmount como pago total. TODO: separar pago vs retenido cuando llegue del request.
        Element totalCashed = XmlDomUtils.append(xml, root, PeruUblNamespaces.SAC, "sac:SUNATTotalCashed",
                XmlDomUtils.decimal(nz(document.getTotalAmount())));
        totalCashed.setAttribute("currencyID", currency);

        appendRetentionDocumentReference(xml, root, document, currency);

        return xml;
    }

    /**
     * sac:SUNATRetentionDocumentReference — un comprobante de pago sobre el que se
     * aplico la retencion. Caso simple "1 retencion sobre 1 comprobante" usando los
     * campos {@code relatedDocument*}. Agrupar N comprobantes es mejora futura.
     */
    private void appendRetentionDocumentReference(Document xml, Element root, FiscalDocumentEntity document, String currency) {
        Element ref = XmlDomUtils.append(xml, root, PeruUblNamespaces.SAC, "sac:SUNATRetentionDocumentReference", null);

        // Catalogo 01 del comprobante relacionado (01 factura, 03 boleta, 07 NC, 08 ND...).
        Element id = XmlDomUtils.append(xml, ref, PeruUblNamespaces.CBC, "cbc:ID",
                safe(document.getRelatedDocumentNumber(), document.getFullNumber()));
        id.setAttribute("schemeID", safe(document.getRelatedDocumentTypeCode(), "01"));

        // Fecha de emision del comprobante relacionado; fallback a la fecha del CR.
        XmlDomUtils.append(xml, ref, PeruUblNamespaces.CBC, "cbc:IssueDate", resolveRelatedIssueDate(document));

        // Importe total del comprobante relacionado. TODO: campo propio; usamos totalAmount.
        XmlDomUtils.appendAmount(xml, ref, "cbc:TotalInvoiceAmount", currency, nz(document.getTotalAmount()));

        // cac:Payment — pago que origina la retencion (id, importe, fecha).
        Element payment = XmlDomUtils.append(xml, ref, PeruUblNamespaces.CAC, "cac:Payment", null);
        XmlDomUtils.append(xml, payment, PeruUblNamespaces.CBC, "cbc:ID", "1");
        XmlDomUtils.appendAmount(xml, payment, "cbc:PaidAmount", currency, nz(document.getTotalAmount()));
        XmlDomUtils.append(xml, payment, PeruUblNamespaces.CBC, "cbc:PaidDate", resolveRelatedIssueDate(document));

        // sac:SUNATRetentionInformation — importe retenido, fecha y (opcional) tipo de cambio.
        Element info = XmlDomUtils.append(xml, ref, PeruUblNamespaces.SAC, "sac:SUNATRetentionInformation", null);
        Element retAmount = XmlDomUtils.append(xml, info, PeruUblNamespaces.SAC, "sac:SUNATRetentionAmount",
                XmlDomUtils.decimal(nz(document.getTaxAmount())));
        retAmount.setAttribute("currencyID", currency);
        XmlDomUtils.append(xml, info, PeruUblNamespaces.SAC, "sac:SUNATRetentionDate", document.getIssueDate().toString());
        // Importe neto pagado tras la retencion (pago - retenido).
        BigDecimal netPaid = nz(document.getTotalAmount()).subtract(nz(document.getTaxAmount()));
        Element net = XmlDomUtils.append(xml, info, PeruUblNamespaces.SAC, "sac:SUNATNetTotalCashed",
                XmlDomUtils.decimal(netPaid));
        net.setAttribute("currencyID", currency);
        // Tipo de cambio solo si la moneda del comprobante relacionado difiere; SUNAT lo
        // exige cuando hay conversion. TODO: poblar cuando el request traiga exchangeRate.
        if (document.getExchangeRate() != null) {
            Element exch = XmlDomUtils.append(xml, info, PeruUblNamespaces.CAC, "cac:ExchangeRate", null);
            XmlDomUtils.append(xml, exch, PeruUblNamespaces.CBC, "cbc:SourceCurrencyCode", currency);
            XmlDomUtils.append(xml, exch, PeruUblNamespaces.CBC, "cbc:TargetCurrencyCode", "PEN");
            XmlDomUtils.append(xml, exch, PeruUblNamespaces.CBC, "cbc:CalculationRate",
                    document.getExchangeRate().toPlainString());
            XmlDomUtils.append(xml, exch, PeruUblNamespaces.CBC, "cbc:Date", document.getIssueDate().toString());
        }
    }

    private String resolveRelatedIssueDate(FiscalDocumentEntity document) {
        if (document.getRelatedDocument() != null && document.getRelatedDocument().getIssueDate() != null) {
            return document.getRelatedDocument().getIssueDate().toString();
        }
        return document.getIssueDate().toString();
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
        Element extRef = XmlDomUtils.append(xml, attach, PeruUblNamespaces.CAC, "cac:ExternalReference", null);
        XmlDomUtils.append(xml, extRef, PeruUblNamespaces.CBC, "cbc:URI", "#SignatureSP");
    }

    /** Agente de retencion (emisor): cac:AgentParty con RUC y razon social. */
    private void appendAgentParty(Document xml, Element root, FiscalDocumentEntity document, EmitterContext ctx) {
        Element agent = XmlDomUtils.append(xml, root, PeruUblNamespaces.CAC, "cac:AgentParty", null);
        Element party = XmlDomUtils.append(xml, agent, PeruUblNamespaces.CAC, "cac:Party", null);

        Element identification = XmlDomUtils.append(xml, party, PeruUblNamespaces.CAC, "cac:PartyIdentification", null);
        Element id = XmlDomUtils.append(xml, identification, PeruUblNamespaces.CBC, "cbc:ID", safe(ctx.documentNumber()));
        // schemeID 6 = RUC (catalogo 06).
        id.setAttribute("schemeID", safe(ctx.documentType(), "6"));

        Element legalEntity = XmlDomUtils.append(xml, party, PeruUblNamespaces.CAC, "cac:PartyLegalEntity", null);
        XmlDomUtils.append(xml, legalEntity, PeruUblNamespaces.CBC, "cbc:RegistrationName", safe(ctx.legalName()));

        Element address = XmlDomUtils.append(xml, legalEntity, PeruUblNamespaces.CAC, "cac:RegistrationAddress", null);
        Element addressLine = XmlDomUtils.append(xml, address, PeruUblNamespaces.CAC, "cac:AddressLine", null);
        XmlDomUtils.append(xml, addressLine, PeruUblNamespaces.CBC, "cbc:Line", safe(ctx.fiscalAddress()));
        Element country = XmlDomUtils.append(xml, address, PeruUblNamespaces.CAC, "cac:Country", null);
        XmlDomUtils.append(xml, country, PeruUblNamespaces.CBC, "cbc:IdentificationCode", safe(document.getCountryCode(), "PE"));
    }

    /** Proveedor al que se le retiene (cliente del request): cac:ReceiverParty. */
    private void appendReceiverParty(Document xml, Element root, FiscalDocumentEntity document) {
        Element receiver = XmlDomUtils.append(xml, root, PeruUblNamespaces.CAC, "cac:ReceiverParty", null);
        Element party = XmlDomUtils.append(xml, receiver, PeruUblNamespaces.CAC, "cac:Party", null);

        if (document.getCustomerDocumentNumber() != null && !document.getCustomerDocumentNumber().isBlank()) {
            Element identification = XmlDomUtils.append(xml, party, PeruUblNamespaces.CAC, "cac:PartyIdentification", null);
            Element id = XmlDomUtils.append(xml, identification, PeruUblNamespaces.CBC, "cbc:ID", document.getCustomerDocumentNumber());
            if (document.getCustomerDocumentType() != null && !document.getCustomerDocumentType().isBlank()) {
                id.setAttribute("schemeID", document.getCustomerDocumentType());
            }
        }

        Element legalEntity = XmlDomUtils.append(xml, party, PeruUblNamespaces.CAC, "cac:PartyLegalEntity", null);
        XmlDomUtils.append(xml, legalEntity, PeruUblNamespaces.CBC, "cbc:RegistrationName", safe(document.getCustomerName()));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
