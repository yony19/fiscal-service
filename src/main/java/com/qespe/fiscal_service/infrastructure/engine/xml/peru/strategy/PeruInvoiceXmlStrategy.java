package com.qespe.fiscal_service.infrastructure.engine.xml.peru.strategy;

import com.qespe.fiscal_service.core.domain.engine.EmitterContext;
import com.qespe.fiscal_service.infrastructure.engine.xml.peru.util.PeruUblNamespaces;
import com.qespe.fiscal_service.infrastructure.engine.xml.peru.util.XmlDomUtils;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@Component
public class PeruInvoiceXmlStrategy extends BasePeruUblDocumentXmlStrategy {

    @Override
    public boolean supports(String documentType) {
        return "INVOICE".equalsIgnoreCase(documentType);
    }

    @Override
    public Document build(FiscalDocumentEntity document, EmitterContext emitterContext) {
        Document xml = XmlDomUtils.createDocument();
        Element root = XmlDomUtils.createRoot(xml, PeruUblNamespaces.UBL_INVOICE, "Invoice");

        appendUblCoreHeaders(xml, root, document);
        XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:InvoiceTypeCode", document.getDocumentTypeCode());

        appendSupplierParty(xml, root, document, emitterContext);
        appendCustomerParty(xml, root, document);
        appendTaxTotal(xml, root, document);
        appendMonetaryTotal(xml, root, document, "cac:LegalMonetaryTotal");

        for (var line : document.getLines()) {
            appendInvoiceLine(xml, root, document, line);
        }
        return xml;
    }
}
