package com.qespe.fiscal_service.infrastructure.engine.xml.peru.strategy;

import com.qespe.fiscal_service.core.domain.engine.EmitterContext;
import com.qespe.fiscal_service.infrastructure.engine.xml.peru.util.PeruUblNamespaces;
import com.qespe.fiscal_service.infrastructure.engine.xml.peru.util.XmlDomUtils;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@Component
public class PeruDebitNoteXmlStrategy extends BasePeruUblDocumentXmlStrategy {

    @Override
    public boolean supports(String documentType) {
        return "DEBIT_NOTE".equalsIgnoreCase(documentType);
    }

    @Override
    public Document build(FiscalDocumentEntity document, EmitterContext emitterContext) {
        Document xml = XmlDomUtils.createDocument();
        Element root = XmlDomUtils.createRoot(xml, PeruUblNamespaces.UBL_DEBIT_NOTE, "DebitNote");

        appendUblCoreHeaders(xml, root, document);
        XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:DebitNoteTypeCode", document.getDocumentTypeCode());
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
        appendMonetaryTotal(xml, root, document, "cac:RequestedMonetaryTotal");

        for (var line : document.getLines()) {
            Element debitLine = XmlDomUtils.append(xml, root, PeruUblNamespaces.CAC, "cac:DebitNoteLine", null);
            XmlDomUtils.append(xml, debitLine, PeruUblNamespaces.CBC, "cbc:ID", String.valueOf(line.getLineNo()));
            Element qty = XmlDomUtils.append(xml, debitLine, PeruUblNamespaces.CBC, "cbc:DebitedQuantity", XmlDomUtils.decimalQty(line.getQuantity()));
            qty.setAttribute("unitCode", safe(line.getUnitCode(), "NIU"));
            XmlDomUtils.appendAmount(xml, debitLine, "cbc:LineExtensionAmount", document.getCurrencyCode(), line.getTaxableBaseAmount());
            appendLineTax(xml, debitLine, document, line);

            Element item = XmlDomUtils.append(xml, debitLine, PeruUblNamespaces.CAC, "cac:Item", null);
            XmlDomUtils.append(xml, item, PeruUblNamespaces.CBC, "cbc:Description", safe(line.getDescription()));

            Element price = XmlDomUtils.append(xml, debitLine, PeruUblNamespaces.CAC, "cac:Price", null);
            XmlDomUtils.appendAmount(xml, price, "cbc:PriceAmount", document.getCurrencyCode(), line.getUnitValue() != null ? line.getUnitValue() : line.getUnitPrice());
        }

        return xml;
    }
}
