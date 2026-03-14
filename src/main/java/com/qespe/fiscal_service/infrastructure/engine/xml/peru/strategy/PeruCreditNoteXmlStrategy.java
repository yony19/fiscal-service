package com.qespe.fiscal_service.infrastructure.engine.xml.peru.strategy;

import com.qespe.fiscal_service.core.domain.engine.EmitterContext;
import com.qespe.fiscal_service.infrastructure.engine.xml.peru.util.PeruUblNamespaces;
import com.qespe.fiscal_service.infrastructure.engine.xml.peru.util.XmlDomUtils;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@Component
public class PeruCreditNoteXmlStrategy extends BasePeruUblDocumentXmlStrategy {

    @Override
    public boolean supports(String documentType) {
        return "CREDIT_NOTE".equalsIgnoreCase(documentType);
    }

    @Override
    public Document build(FiscalDocumentEntity document, EmitterContext emitterContext) {
        Document xml = XmlDomUtils.createDocument();
        Element root = XmlDomUtils.createRoot(xml, PeruUblNamespaces.UBL_CREDIT_NOTE, "CreditNote");

        appendUblCoreHeaders(xml, root, document);
        XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:CreditNoteTypeCode", document.getDocumentTypeCode());
        appendDocumentCurrencyCode(xml, root, document);

        Element discrepancy = XmlDomUtils.append(xml, root, PeruUblNamespaces.CAC, "cac:DiscrepancyResponse", null);
        XmlDomUtils.append(xml, discrepancy, PeruUblNamespaces.CBC, "cbc:ReferenceID", safe(document.getRelatedDocumentNumber(), document.getFullNumber()));
        XmlDomUtils.append(xml, discrepancy, PeruUblNamespaces.CBC, "cbc:ResponseCode", "01");
        XmlDomUtils.append(xml, discrepancy, PeruUblNamespaces.CBC, "cbc:Description", "Ajuste de documento");

        if (document.getRelatedDocumentNumber() != null && !document.getRelatedDocumentNumber().isBlank()) {
            Element billingReference = XmlDomUtils.append(xml, root, PeruUblNamespaces.CAC, "cac:BillingReference", null);
            Element invoiceRef = XmlDomUtils.append(xml, billingReference, PeruUblNamespaces.CAC, "cac:InvoiceDocumentReference", null);
            XmlDomUtils.append(xml, invoiceRef, PeruUblNamespaces.CBC, "cbc:ID", document.getRelatedDocumentNumber());
            if (document.getRelatedDocumentTypeCode() != null) {
                XmlDomUtils.append(xml, invoiceRef, PeruUblNamespaces.CBC, "cbc:DocumentTypeCode", document.getRelatedDocumentTypeCode());
            }
        }

        appendSupplierParty(xml, root, document, emitterContext);
        appendCustomerParty(xml, root, document);
        appendTaxTotal(xml, root, document);
        appendMonetaryTotal(xml, root, document, "cac:LegalMonetaryTotal");

        for (var line : document.getLines()) {
            Element creditLine = XmlDomUtils.append(xml, root, PeruUblNamespaces.CAC, "cac:CreditNoteLine", null);
            XmlDomUtils.append(xml, creditLine, PeruUblNamespaces.CBC, "cbc:ID", String.valueOf(line.getLineNo()));
            Element qty = XmlDomUtils.append(xml, creditLine, PeruUblNamespaces.CBC, "cbc:CreditedQuantity", XmlDomUtils.decimalQty(line.getQuantity()));
            qty.setAttribute("unitCode", normalizeFiscalUnitCode(line.getUnitCode()));
            XmlDomUtils.appendAmount(xml, creditLine, "cbc:LineExtensionAmount", document.getCurrencyCode(), line.getTaxableBaseAmount());
            appendLineTax(xml, creditLine, document, line);

            Element item = XmlDomUtils.append(xml, creditLine, PeruUblNamespaces.CAC, "cac:Item", null);
            XmlDomUtils.append(xml, item, PeruUblNamespaces.CBC, "cbc:Description", safe(line.getDescription()));

            Element price = XmlDomUtils.append(xml, creditLine, PeruUblNamespaces.CAC, "cac:Price", null);
            XmlDomUtils.appendAmount(xml, price, "cbc:PriceAmount", document.getCurrencyCode(), line.getUnitValue() != null ? line.getUnitValue() : line.getUnitPrice());
        }

        return xml;
    }
}
