package com.qespe.fiscal_service.infrastructure.engine.xml.peru.strategy;

import com.qespe.fiscal_service.core.domain.engine.EmitterContext;
import com.qespe.fiscal_service.infrastructure.engine.xml.peru.util.PeruUblNamespaces;
import com.qespe.fiscal_service.infrastructure.engine.xml.peru.util.XmlDomUtils;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentLineEntity;
import com.qespe.fiscal_service.shared.util.FiscalTaxRateUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.math.BigDecimal;

public abstract class BasePeruUblDocumentXmlStrategy implements PeruUblDocumentXmlStrategy {

    protected void appendUblCoreHeaders(Document doc, Element root, FiscalDocumentEntity fiscalDocument) {
        Element extensions = XmlDomUtils.append(doc, root, PeruUblNamespaces.EXT, "ext:UBLExtensions", null);
        Element extension = XmlDomUtils.append(doc, extensions, PeruUblNamespaces.EXT, "ext:UBLExtension", null);
        XmlDomUtils.append(doc, extension, PeruUblNamespaces.EXT, "ext:ExtensionContent", null);

        XmlDomUtils.append(doc, root, PeruUblNamespaces.CBC, "cbc:UBLVersionID", "2.1");
        XmlDomUtils.append(doc, root, PeruUblNamespaces.CBC, "cbc:CustomizationID", "2.0");
        XmlDomUtils.append(doc, root, PeruUblNamespaces.CBC, "cbc:ProfileID", resolveOperationTypeCode(fiscalDocument));
        XmlDomUtils.append(doc, root, PeruUblNamespaces.CBC, "cbc:ID", fiscalDocument.getFullNumber());
        XmlDomUtils.append(doc, root, PeruUblNamespaces.CBC, "cbc:IssueDate", fiscalDocument.getIssueDate().toString());
        if (fiscalDocument.getIssueTime() != null) {
            XmlDomUtils.append(doc, root, PeruUblNamespaces.CBC, "cbc:IssueTime", fiscalDocument.getIssueTime().toString());
        }
    }

    protected void appendDocumentCurrencyCode(Document doc, Element root, FiscalDocumentEntity fiscalDocument) {
        XmlDomUtils.append(doc, root, PeruUblNamespaces.CBC, "cbc:DocumentCurrencyCode", fiscalDocument.getCurrencyCode());
    }

    protected void appendInvoiceTypeCode(Document doc, Element root, FiscalDocumentEntity fiscalDocument) {
        Element invoiceTypeCode = XmlDomUtils.append(doc, root, PeruUblNamespaces.CBC, "cbc:InvoiceTypeCode", fiscalDocument.getDocumentTypeCode());
        invoiceTypeCode.setAttribute("listID", resolveOperationTypeCode(fiscalDocument));
        invoiceTypeCode.setAttribute("listAgencyName", "PE:SUNAT");
        invoiceTypeCode.setAttribute("listName", "Tipo de Documento");
    }

    protected void appendSupplierParty(Document doc, Element root, FiscalDocumentEntity fiscalDocument, EmitterContext emitterContext) {
        Element supplier = XmlDomUtils.append(doc, root, PeruUblNamespaces.CAC, "cac:AccountingSupplierParty", null);
        Element party = XmlDomUtils.append(doc, supplier, PeruUblNamespaces.CAC, "cac:Party", null);

        Element identification = XmlDomUtils.append(doc, party, PeruUblNamespaces.CAC, "cac:PartyIdentification", null);
        Element id = XmlDomUtils.append(doc, identification, PeruUblNamespaces.CBC, "cbc:ID", emitterContext.documentNumber());
        id.setAttribute("schemeID", safe(emitterContext.documentType()));

        Element legalEntity = XmlDomUtils.append(doc, party, PeruUblNamespaces.CAC, "cac:PartyLegalEntity", null);
        XmlDomUtils.append(doc, legalEntity, PeruUblNamespaces.CBC, "cbc:RegistrationName", safe(emitterContext.legalName()));

        Element address = XmlDomUtils.append(doc, legalEntity, PeruUblNamespaces.CAC, "cac:RegistrationAddress", null);
        XmlDomUtils.append(doc, address, PeruUblNamespaces.CBC, "cbc:AddressTypeCode", fiscalDocument.getSeries());
        Element addressLine = XmlDomUtils.append(doc, address, PeruUblNamespaces.CAC, "cac:AddressLine", null);
        XmlDomUtils.append(doc, addressLine, PeruUblNamespaces.CBC, "cbc:Line", safe(emitterContext.fiscalAddress()));
        Element country = XmlDomUtils.append(doc, address, PeruUblNamespaces.CAC, "cac:Country", null);
        XmlDomUtils.append(doc, country, PeruUblNamespaces.CBC, "cbc:IdentificationCode", safe(fiscalDocument.getCountryCode(), "PE"));
    }

    protected void appendCustomerParty(Document doc, Element root, FiscalDocumentEntity fiscalDocument) {
        Element customer = XmlDomUtils.append(doc, root, PeruUblNamespaces.CAC, "cac:AccountingCustomerParty", null);
        Element party = XmlDomUtils.append(doc, customer, PeruUblNamespaces.CAC, "cac:Party", null);

        if (fiscalDocument.getCustomerDocumentNumber() != null && !fiscalDocument.getCustomerDocumentNumber().isBlank()) {
            Element identification = XmlDomUtils.append(doc, party, PeruUblNamespaces.CAC, "cac:PartyIdentification", null);
            Element id = XmlDomUtils.append(doc, identification, PeruUblNamespaces.CBC, "cbc:ID", fiscalDocument.getCustomerDocumentNumber());
            if (fiscalDocument.getCustomerDocumentType() != null && !fiscalDocument.getCustomerDocumentType().isBlank()) {
                id.setAttribute("schemeID", fiscalDocument.getCustomerDocumentType());
            }
        }

        Element legalEntity = XmlDomUtils.append(doc, party, PeruUblNamespaces.CAC, "cac:PartyLegalEntity", null);
        XmlDomUtils.append(doc, legalEntity, PeruUblNamespaces.CBC, "cbc:RegistrationName", safe(fiscalDocument.getCustomerName()));
    }

    protected void appendTaxTotal(Document doc, Element root, FiscalDocumentEntity fiscalDocument) {
        Element taxTotal = XmlDomUtils.append(doc, root, PeruUblNamespaces.CAC, "cac:TaxTotal", null);
        XmlDomUtils.appendAmount(doc, taxTotal, "cbc:TaxAmount", fiscalDocument.getCurrencyCode(), fiscalDocument.getTaxAmount());

        Element taxSub = XmlDomUtils.append(doc, taxTotal, PeruUblNamespaces.CAC, "cac:TaxSubtotal", null);
        XmlDomUtils.appendAmount(doc, taxSub, "cbc:TaxableAmount", fiscalDocument.getCurrencyCode(), fiscalDocument.getTaxableAmount());
        XmlDomUtils.appendAmount(doc, taxSub, "cbc:TaxAmount", fiscalDocument.getCurrencyCode(), fiscalDocument.getTaxAmount());

        Element taxCategory = XmlDomUtils.append(doc, taxSub, PeruUblNamespaces.CAC, "cac:TaxCategory", null);
        Element taxScheme = XmlDomUtils.append(doc, taxCategory, PeruUblNamespaces.CAC, "cac:TaxScheme", null);
        XmlDomUtils.append(doc, taxScheme, PeruUblNamespaces.CBC, "cbc:ID", "1000");
        XmlDomUtils.append(doc, taxScheme, PeruUblNamespaces.CBC, "cbc:Name", "IGV");
        XmlDomUtils.append(doc, taxScheme, PeruUblNamespaces.CBC, "cbc:TaxTypeCode", "VAT");
    }

    protected void appendMonetaryTotal(Document doc, Element root, FiscalDocumentEntity fiscalDocument, String monetaryTag) {
        Element legalTotal = XmlDomUtils.append(doc, root, PeruUblNamespaces.CAC, monetaryTag, null);
        XmlDomUtils.appendAmount(doc, legalTotal, "cbc:LineExtensionAmount", fiscalDocument.getCurrencyCode(), fiscalDocument.getTaxableAmount());
        XmlDomUtils.appendAmount(doc, legalTotal, "cbc:TaxInclusiveAmount", fiscalDocument.getCurrencyCode(), fiscalDocument.getTotalAmount());
        XmlDomUtils.appendAmount(doc, legalTotal, "cbc:PayableAmount", fiscalDocument.getCurrencyCode(), fiscalDocument.getTotalAmount());
    }

    protected void appendInvoiceLine(Document doc, Element root, FiscalDocumentEntity fiscalDocument, FiscalDocumentLineEntity line) {
        Element invoiceLine = XmlDomUtils.append(doc, root, PeruUblNamespaces.CAC, "cac:InvoiceLine", null);
        XmlDomUtils.append(doc, invoiceLine, PeruUblNamespaces.CBC, "cbc:ID", String.valueOf(line.getLineNo()));

        Element qty = XmlDomUtils.append(doc, invoiceLine, PeruUblNamespaces.CBC, "cbc:InvoicedQuantity", XmlDomUtils.decimalQty(line.getQuantity()));
        qty.setAttribute("unitCode", normalizeFiscalUnitCode(line.getUnitCode()));

        XmlDomUtils.appendAmount(doc, invoiceLine, "cbc:LineExtensionAmount", fiscalDocument.getCurrencyCode(), line.getTaxableBaseAmount());

        Element pricing = XmlDomUtils.append(doc, invoiceLine, PeruUblNamespaces.CAC, "cac:PricingReference", null);
        Element altPrice = XmlDomUtils.append(doc, pricing, PeruUblNamespaces.CAC, "cac:AlternativeConditionPrice", null);
        XmlDomUtils.appendAmount(doc, altPrice, "cbc:PriceAmount", fiscalDocument.getCurrencyCode(), line.getUnitPrice());
        XmlDomUtils.append(doc, altPrice, PeruUblNamespaces.CBC, "cbc:PriceTypeCode", "01");

        appendLineTax(doc, invoiceLine, fiscalDocument, line);

        Element item = XmlDomUtils.append(doc, invoiceLine, PeruUblNamespaces.CAC, "cac:Item", null);
        XmlDomUtils.append(doc, item, PeruUblNamespaces.CBC, "cbc:Description", safe(line.getDescription()));
        if (line.getItemCode() != null && !line.getItemCode().isBlank()) {
            Element sellerId = XmlDomUtils.append(doc, item, PeruUblNamespaces.CAC, "cac:SellersItemIdentification", null);
            XmlDomUtils.append(doc, sellerId, PeruUblNamespaces.CBC, "cbc:ID", line.getItemCode());
        }

        Element price = XmlDomUtils.append(doc, invoiceLine, PeruUblNamespaces.CAC, "cac:Price", null);
        XmlDomUtils.appendAmount(doc, price, "cbc:PriceAmount", fiscalDocument.getCurrencyCode(), line.getUnitValue() != null ? line.getUnitValue() : line.getUnitPrice());
    }

    protected void appendLineTax(Document doc, Element lineRoot, FiscalDocumentEntity fiscalDocument, FiscalDocumentLineEntity line) {
        Element taxTotal = XmlDomUtils.append(doc, lineRoot, PeruUblNamespaces.CAC, "cac:TaxTotal", null);
        XmlDomUtils.appendAmount(doc, taxTotal, "cbc:TaxAmount", fiscalDocument.getCurrencyCode(), line.getTaxAmount());

        Element sub = XmlDomUtils.append(doc, taxTotal, PeruUblNamespaces.CAC, "cac:TaxSubtotal", null);
        XmlDomUtils.appendAmount(doc, sub, "cbc:TaxableAmount", fiscalDocument.getCurrencyCode(), line.getTaxableBaseAmount());
        XmlDomUtils.appendAmount(doc, sub, "cbc:TaxAmount", fiscalDocument.getCurrencyCode(), line.getTaxAmount());

        Element cat = XmlDomUtils.append(doc, sub, PeruUblNamespaces.CAC, "cac:TaxCategory", null);
        BigDecimal igvRate = resolveLineIgvRate(line);
        if (igvRate != null) {
            XmlDomUtils.append(doc, cat, PeruUblNamespaces.CBC, "cbc:Percent", FiscalTaxRateUtils.toPercent(igvRate).toPlainString());
        }
        if (line.getTaxAffectationCode() != null && !line.getTaxAffectationCode().isBlank()) {
            XmlDomUtils.append(doc, cat, PeruUblNamespaces.CBC, "cbc:TaxExemptionReasonCode", line.getTaxAffectationCode());
        }
        Element scheme = XmlDomUtils.append(doc, cat, PeruUblNamespaces.CAC, "cac:TaxScheme", null);
        XmlDomUtils.append(doc, scheme, PeruUblNamespaces.CBC, "cbc:ID", "1000");
        XmlDomUtils.append(doc, scheme, PeruUblNamespaces.CBC, "cbc:Name", "IGV");
        XmlDomUtils.append(doc, scheme, PeruUblNamespaces.CBC, "cbc:TaxTypeCode", "VAT");
    }

    protected String safe(String value) {
        return safe(value, "-");
    }

    protected String safe(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    protected String resolveOperationTypeCode(FiscalDocumentEntity fiscalDocument) {
        if (fiscalDocument.getOperationTypeCode() != null && !fiscalDocument.getOperationTypeCode().isBlank()) {
            return fiscalDocument.getOperationTypeCode().trim();
        }
        // Default SUNAT sale operation for local taxable/internal sales.
        return "0101";
    }

    protected String normalizeFiscalUnitCode(String unitCode) {
        if (unitCode == null || unitCode.isBlank()) {
            return "NIU";
        }

        String normalized = unitCode.trim().toUpperCase();
        return switch (normalized) {
            case "UN", "UND", "UNI", "UNIT", "UNIDAD" -> "NIU";
            default -> normalized;
        };
    }

    protected BigDecimal resolveLineIgvRate(FiscalDocumentLineEntity line) {
        if (line.getIgvRate() != null) {
            return FiscalTaxRateUtils.normalizeRatio(line.getIgvRate());
        }
        if (isTaxableAffectation(line.getTaxAffectationCode())) {
            return FiscalTaxRateUtils.DEFAULT_IGV_RATE_RATIO;
        }
        return null;
    }

    protected boolean isTaxableAffectation(String taxAffectationCode) {
        if (taxAffectationCode == null || taxAffectationCode.isBlank()) {
            return true;
        }
        return taxAffectationCode.trim().startsWith("10");
    }
}
