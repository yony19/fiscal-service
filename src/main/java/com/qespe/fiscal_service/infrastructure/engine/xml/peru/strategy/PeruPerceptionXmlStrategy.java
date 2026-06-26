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
 * Estrategia UBL para el <strong>Comprobante de Percepcion (catalogo 01 = "40")</strong>
 * — regimen de percepciones del IGV (SUNAT, esquema {@code sunat:Perception 2.1}).
 *
 * <p>Es el espejo del Comprobante de Retencion: <em>sincrono</em> ({@code sendBill} ->
 * CDR), misma firma/almacenamiento/estados/reintentos. NO esta en
 * {@code isSummaryDocument(...)}. La diferencia con la retencion es la direccion del
 * flujo (el emisor PERCIBE del cliente, no retiene a un proveedor) y los nombres SUNAT
 * de los nodos ({@code SUNATPerception*} en vez de {@code SUNATRetention*}).
 *
 * <p><strong>Diferencias estructurales</strong> (motivos de rechazo SUNAT si se confunden):
 * <ul>
 *   <li>Raiz {@code Perception} en su propio namespace + {@code sac}/{@code ds}
 *       ({@link XmlDomUtils#createSunatComprobanteRoot}).</li>
 *   <li>Cabecera UBL <strong>2.1 / CustomizationID 1.0</strong>.</li>
 *   <li>{@code cac:AgentParty} (agente de percepcion = emisor) y {@code cac:ReceiverParty}
 *       (cliente percibido).</li>
 *   <li>{@code cbc:SUNATPerceptionSystemCode} (regimen, catalogo 22) y
 *       {@code cbc:SUNATPerceptionPercent} (tasa, p.ej. 2.00).</li>
 *   <li>{@code sac:SUNATPerceptionDocumentReference} con {@code sac:SUNATPerceptionInformation}.</li>
 * </ul>
 *
 * <p><strong>Datos propios todavia no modelados en el request/entidad</strong> (regimen,
 * tasa, importe percibido, total del comprobante relacionado, fecha de cobro): se
 * reutilizan los campos disponibles con fallbacks (ver TODO). Reemplazar cuando el
 * upstream envie los datos reales.
 *
 * <p><strong>Pendiente de homologacion SUNAT:</strong> firma, formato del ID y
 * consistencia de montos/tasas SOLO se validan enviando a SUNAT beta.
 */
@Component
public class PeruPerceptionXmlStrategy extends BasePeruUblDocumentXmlStrategy {

    // Catalogo 22 SUNAT: 01 = percepcion venta interna (2%). TODO: del request cuando exista.
    private static final String DEFAULT_PERCEPTION_SYSTEM_CODE = "01";
    // Tasa del regimen 01 (2%). TODO: parametrizar por request/regimen.
    private static final BigDecimal DEFAULT_PERCEPTION_PERCENT = new BigDecimal("2.00");

    @Override
    public boolean supports(String documentType) {
        return "PERCEPTION".equalsIgnoreCase(documentType);
    }

    @Override
    public Document build(FiscalDocumentEntity document, EmitterContext emitterContext) {
        Document xml = XmlDomUtils.createDocument();
        Element root = XmlDomUtils.createSunatComprobanteRoot(xml, PeruUblNamespaces.UBL_PERCEPTION, "Perception");

        Element extensions = XmlDomUtils.append(xml, root, PeruUblNamespaces.EXT, "ext:UBLExtensions", null);
        Element extension = XmlDomUtils.append(xml, extensions, PeruUblNamespaces.EXT, "ext:UBLExtension", null);
        XmlDomUtils.append(xml, extension, PeruUblNamespaces.EXT, "ext:ExtensionContent", null);

        XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:UBLVersionID", "2.1");
        XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:CustomizationID", "1.0");
        XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:ID", document.getFullNumber());
        XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:IssueDate", document.getIssueDate().toString());
        if (document.getIssueTime() != null) {
            XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:IssueTime", document.getIssueTime().toString());
        }

        appendSignaturePlaceholder(xml, root, emitterContext);

        // Agente de percepcion (emisor) y cliente percibido.
        appendAgentParty(xml, root, document, emitterContext);
        appendReceiverParty(xml, root, document);

        // Regimen y tasa de percepcion. TODO: tomar del request cuando exista.
        String systemCode = safe(document.getOperationTypeCode(), DEFAULT_PERCEPTION_SYSTEM_CODE);
        XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:SUNATPerceptionSystemCode", systemCode);
        XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:SUNATPerceptionPercent",
                DEFAULT_PERCEPTION_PERCENT.toPlainString());
        XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:Note", "Comprobante de Percepcion");

        String currency = safe(document.getCurrencyCode(), "PEN");
        // Importe total percibido. TODO: campo propio "perceivedAmount"; usamos taxAmount.
        XmlDomUtils.appendAmount(xml, root, "cbc:TotalInvoiceAmount", currency, nz(document.getTaxAmount()));
        // sac:SUNATTotalCashed = total cobrado al cliente (incluida la percepcion).
        Element totalCashed = XmlDomUtils.append(xml, root, PeruUblNamespaces.SAC, "sac:SUNATTotalCashed",
                XmlDomUtils.decimal(nz(document.getTotalAmount())));
        totalCashed.setAttribute("currencyID", currency);

        appendPerceptionDocumentReference(xml, root, document, currency);

        return xml;
    }

    /**
     * sac:SUNATPerceptionDocumentReference — un comprobante de pago sobre el que se
     * aplico la percepcion. Caso simple "1 percepcion sobre 1 comprobante" usando los
     * campos {@code relatedDocument*}.
     */
    private void appendPerceptionDocumentReference(Document xml, Element root, FiscalDocumentEntity document, String currency) {
        Element ref = XmlDomUtils.append(xml, root, PeruUblNamespaces.SAC, "sac:SUNATPerceptionDocumentReference", null);

        Element id = XmlDomUtils.append(xml, ref, PeruUblNamespaces.CBC, "cbc:ID",
                safe(document.getRelatedDocumentNumber(), document.getFullNumber()));
        id.setAttribute("schemeID", safe(document.getRelatedDocumentTypeCode(), "01"));

        XmlDomUtils.append(xml, ref, PeruUblNamespaces.CBC, "cbc:IssueDate", resolveRelatedIssueDate(document));
        XmlDomUtils.appendAmount(xml, ref, "cbc:TotalInvoiceAmount", currency, nz(document.getTotalAmount()));

        Element payment = XmlDomUtils.append(xml, ref, PeruUblNamespaces.CAC, "cac:Payment", null);
        XmlDomUtils.append(xml, payment, PeruUblNamespaces.CBC, "cbc:ID", "1");
        XmlDomUtils.appendAmount(xml, payment, "cbc:PaidAmount", currency, nz(document.getTotalAmount()));
        XmlDomUtils.append(xml, payment, PeruUblNamespaces.CBC, "cbc:PaidDate", resolveRelatedIssueDate(document));

        Element info = XmlDomUtils.append(xml, ref, PeruUblNamespaces.SAC, "sac:SUNATPerceptionInformation", null);
        Element percAmount = XmlDomUtils.append(xml, info, PeruUblNamespaces.SAC, "sac:SUNATPerceptionAmount",
                XmlDomUtils.decimal(nz(document.getTaxAmount())));
        percAmount.setAttribute("currencyID", currency);
        XmlDomUtils.append(xml, info, PeruUblNamespaces.SAC, "sac:SUNATPerceptionDate", document.getIssueDate().toString());
        // Total cobrado incluyendo la percepcion (pago + percibido).
        BigDecimal totalToPay = nz(document.getTotalAmount()).add(nz(document.getTaxAmount()));
        Element net = XmlDomUtils.append(xml, info, PeruUblNamespaces.SAC, "sac:SUNATNetTotalCashed",
                XmlDomUtils.decimal(totalToPay));
        net.setAttribute("currencyID", currency);
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

    /** Agente de percepcion (emisor): cac:AgentParty con RUC y razon social. */
    private void appendAgentParty(Document xml, Element root, FiscalDocumentEntity document, EmitterContext ctx) {
        Element agent = XmlDomUtils.append(xml, root, PeruUblNamespaces.CAC, "cac:AgentParty", null);
        Element party = XmlDomUtils.append(xml, agent, PeruUblNamespaces.CAC, "cac:Party", null);

        Element identification = XmlDomUtils.append(xml, party, PeruUblNamespaces.CAC, "cac:PartyIdentification", null);
        Element id = XmlDomUtils.append(xml, identification, PeruUblNamespaces.CBC, "cbc:ID", safe(ctx.documentNumber()));
        id.setAttribute("schemeID", safe(ctx.documentType(), "6"));

        Element legalEntity = XmlDomUtils.append(xml, party, PeruUblNamespaces.CAC, "cac:PartyLegalEntity", null);
        XmlDomUtils.append(xml, legalEntity, PeruUblNamespaces.CBC, "cbc:RegistrationName", safe(ctx.legalName()));

        Element address = XmlDomUtils.append(xml, legalEntity, PeruUblNamespaces.CAC, "cac:RegistrationAddress", null);
        Element addressLine = XmlDomUtils.append(xml, address, PeruUblNamespaces.CAC, "cac:AddressLine", null);
        XmlDomUtils.append(xml, addressLine, PeruUblNamespaces.CBC, "cbc:Line", safe(ctx.fiscalAddress()));
        Element country = XmlDomUtils.append(xml, address, PeruUblNamespaces.CAC, "cac:Country", null);
        XmlDomUtils.append(xml, country, PeruUblNamespaces.CBC, "cbc:IdentificationCode", safe(document.getCountryCode(), "PE"));
    }

    /** Cliente percibido (cliente del request): cac:ReceiverParty. */
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
