package com.qespe.fiscal_service.infrastructure.engine.xml.peru.strategy;

import com.qespe.fiscal_service.core.domain.engine.EmitterContext;
import com.qespe.fiscal_service.core.port.out.FiscalDocumentRepositoryPort;
import com.qespe.fiscal_service.infrastructure.engine.xml.peru.util.PeruUblNamespaces;
import com.qespe.fiscal_service.infrastructure.engine.xml.peru.util.XmlDomUtils;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;
import com.qespe.fiscal_service.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Estrategia UBL para el <strong>Resumen Diario de boletas (RC)</strong> —
 * SummaryDocuments (E1.2 / PRD #2).
 *
 * <p>Como la Comunicacion de Baja, es un documento <em>resumen</em> async
 * ({@code sendSummary} → ticket → {@code getStatus}), pero con dos diferencias
 * que SUNAT rechaza si se confunden:
 * <ul>
 *   <li>Cabecera UBL <strong>2.0 / CustomizationID 1.1</strong> (la RA usa 1.0).</li>
 *   <li>Cada linea {@code sac:SummaryDocumentsLine} es UNA boleta con sus montos
 *       por afectacion ({@code sac:BillingPayment} instructionID 01 gravado /
 *       02 exonerado / 03 inafecto) + IGV.</li>
 * </ul>
 *
 * <p>Las lineas NO estan persistidas: se derivan consultando las boletas de la
 * fecha resumida, que viaja en {@code relatedDocumentNumber} (ISO-8601) — ver
 * {@code DailySummaryService}.
 */
@Component
@RequiredArgsConstructor
public class PeruSummaryXmlStrategy extends BasePeruUblDocumentXmlStrategy {

    private final FiscalDocumentRepositoryPort repository;

    @Override
    public boolean supports(String documentType) {
        return "DAILY_SUMMARY".equalsIgnoreCase(documentType);
    }

    @Override
    public Document build(FiscalDocumentEntity document, EmitterContext emitterContext) {
        LocalDate summaryDate = resolveSummaryDate(document);
        List<FiscalDocumentEntity> boletas =
                repository.findBoletasByIssueDate(document.getCompanyId(), summaryDate);
        if (boletas.isEmpty()) {
            throw new BusinessException(
                    "El Resumen Diario " + document.getFullNumber() + " no tiene boletas el " + summaryDate);
        }

        Document xml = XmlDomUtils.createDocument();
        Element root = XmlDomUtils.createSummaryRoot(xml, PeruUblNamespaces.UBL_SUMMARY, "SummaryDocuments");

        Element extensions = XmlDomUtils.append(xml, root, PeruUblNamespaces.EXT, "ext:UBLExtensions", null);
        Element extension = XmlDomUtils.append(xml, extensions, PeruUblNamespaces.EXT, "ext:UBLExtension", null);
        XmlDomUtils.append(xml, extension, PeruUblNamespaces.EXT, "ext:ExtensionContent", null);

        // Cabecera RC: 2.0 / 1.1 (la RA usa 1.0 — confundirlos es rechazo seguro).
        XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:UBLVersionID", "2.0");
        XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:CustomizationID", "1.1");
        XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:ID", document.getFullNumber());
        XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:ReferenceDate", summaryDate.toString());
        XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:IssueDate", document.getIssueDate().toString());

        appendSignaturePlaceholder(xml, root, emitterContext);
        appendSummarySupplier(xml, root, emitterContext);

        String currency = document.getCurrencyCode() == null ? "PEN" : document.getCurrencyCode();
        int lineNumber = 1;
        for (FiscalDocumentEntity boleta : boletas) {
            appendSummaryLine(xml, root, boleta, lineNumber++, currency);
        }

        return xml;
    }

    /** Una boleta = una sac:SummaryDocumentsLine con estado 1 (adicionar). */
    private void appendSummaryLine(Document xml, Element root, FiscalDocumentEntity boleta,
                                   int lineNumber, String currency) {
        Element line = XmlDomUtils.append(xml, root, PeruUblNamespaces.SAC, "sac:SummaryDocumentsLine", null);
        XmlDomUtils.append(xml, line, PeruUblNamespaces.CBC, "cbc:LineID", String.valueOf(lineNumber));
        XmlDomUtils.append(xml, line, PeruUblNamespaces.CBC, "cbc:DocumentTypeCode", "03");
        XmlDomUtils.append(xml, line, PeruUblNamespaces.CBC, "cbc:ID", boleta.getFullNumber());

        // Receptor de la boleta (DNI tipico). "-" cuando es venta a publico general.
        Element customer = XmlDomUtils.append(xml, line, PeruUblNamespaces.CAC, "cac:AccountingCustomerParty", null);
        XmlDomUtils.append(xml, customer, PeruUblNamespaces.CBC, "cbc:CustomerAssignedAccountID",
                safe(boleta.getCustomerDocumentNumber(), "-"));
        XmlDomUtils.append(xml, customer, PeruUblNamespaces.CBC, "cbc:AdditionalAccountID",
                customerDocTypeCode(boleta.getCustomerDocumentType()));

        // Estado 1 = adicionar (catalogo 19). Modificacion (2) y baja (3) son mejora futura.
        Element status = XmlDomUtils.append(xml, line, PeruUblNamespaces.SAC, "sac:Status", null);
        XmlDomUtils.append(xml, status, PeruUblNamespaces.CBC, "cbc:ConditionCode", "1");

        amount(xml, line, PeruUblNamespaces.SAC, "sac:TotalAmount", boleta.getTotalAmount(), currency);

        appendBillingPayment(xml, line, "01", boleta.getTaxableAmount(), currency);
        appendBillingPayment(xml, line, "02", boleta.getExemptAmount(), currency);
        appendBillingPayment(xml, line, "03", boleta.getUnaffectedAmount(), currency);

        Element taxTotal = XmlDomUtils.append(xml, line, PeruUblNamespaces.CAC, "cac:TaxTotal", null);
        amount(xml, taxTotal, PeruUblNamespaces.CBC, "cbc:TaxAmount", boleta.getTaxAmount(), currency);
        Element taxSub = XmlDomUtils.append(xml, taxTotal, PeruUblNamespaces.CAC, "cac:TaxSubtotal", null);
        amount(xml, taxSub, PeruUblNamespaces.CBC, "cbc:TaxAmount", boleta.getTaxAmount(), currency);
        Element taxCategory = XmlDomUtils.append(xml, taxSub, PeruUblNamespaces.CAC, "cac:TaxCategory", null);
        Element taxScheme = XmlDomUtils.append(xml, taxCategory, PeruUblNamespaces.CAC, "cac:TaxScheme", null);
        XmlDomUtils.append(xml, taxScheme, PeruUblNamespaces.CBC, "cbc:ID", "1000");
        XmlDomUtils.append(xml, taxScheme, PeruUblNamespaces.CBC, "cbc:Name", "IGV");
        XmlDomUtils.append(xml, taxScheme, PeruUblNamespaces.CBC, "cbc:TaxTypeCode", "VAT");
    }

    /**
     * sac:BillingPayment por tipo de operacion (catalogo 11 del RC):
     * 01 gravado, 02 exonerado, 03 inafecto. Se emite incluso en 0.00 para el
     * gravado (SUNAT lo espera); los otros solo cuando aplican.
     */
    private void appendBillingPayment(Document xml, Element line, String instructionId,
                                      BigDecimal amount, String currency) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        if (!"01".equals(instructionId) && value.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        Element billing = XmlDomUtils.append(xml, line, PeruUblNamespaces.SAC, "sac:BillingPayment", null);
        amount(xml, billing, PeruUblNamespaces.CBC, "cbc:PaidAmount", value, currency);
        XmlDomUtils.append(xml, billing, PeruUblNamespaces.CBC, "cbc:InstructionID", instructionId);
    }

    private Element amount(Document xml, Element parent, String ns, String tag,
                           BigDecimal value, String currency) {
        BigDecimal v = value == null ? BigDecimal.ZERO : value;
        Element el = XmlDomUtils.append(xml, parent, ns, tag, v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        el.setAttribute("currencyID", currency);
        return el;
    }

    /** Fecha resumida (ver DailySummaryService). Fallback: la fecha del propio RC. */
    private LocalDate resolveSummaryDate(FiscalDocumentEntity document) {
        String raw = document.getRelatedDocumentNumber();
        if (raw != null && !raw.isBlank()) {
            try {
                return LocalDate.parse(raw.trim());
            } catch (Exception ignored) {
                // formato inesperado: cae al fallback
            }
        }
        return document.getIssueDate();
    }

    /** Catalogo 06: 1 DNI, 6 RUC, 0 sin documento. */
    private String customerDocTypeCode(String documentType) {
        if (documentType == null) return "0";
        return switch (documentType.toUpperCase()) {
            case "DNI", "1" -> "1";
            case "RUC", "6" -> "6";
            default -> "0";
        };
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

    private void appendSummarySupplier(Document xml, Element root, EmitterContext ctx) {
        Element supplier = XmlDomUtils.append(xml, root, PeruUblNamespaces.CAC, "cac:AccountingSupplierParty", null);
        XmlDomUtils.append(xml, supplier, PeruUblNamespaces.CBC, "cbc:CustomerAssignedAccountID", safe(ctx.documentNumber()));
        XmlDomUtils.append(xml, supplier, PeruUblNamespaces.CBC, "cbc:AdditionalAccountID", "6");
        Element party = XmlDomUtils.append(xml, supplier, PeruUblNamespaces.CAC, "cac:Party", null);
        Element legal = XmlDomUtils.append(xml, party, PeruUblNamespaces.CAC, "cac:PartyLegalEntity", null);
        XmlDomUtils.append(xml, legal, PeruUblNamespaces.CBC, "cbc:RegistrationName", safe(ctx.legalName()));
    }
}
