package com.qespe.fiscal_service.infrastructure.engine.xml.peru.util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.XMLConstants;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class XmlDomUtils {

    private XmlDomUtils() {
    }

    public static Document createDocument() {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setNamespaceAware(true);
            DocumentBuilder builder = dbf.newDocumentBuilder();
            return builder.newDocument();
        } catch (ParserConfigurationException ex) {
            throw new IllegalStateException("Cannot create XML document", ex);
        }
    }

    public static Element createRoot(Document doc, String namespace, String localName) {
        Element root = doc.createElementNS(namespace, localName);
        root.setAttribute("xmlns", namespace);
        root.setAttribute("xmlns:cac", PeruUblNamespaces.CAC);
        root.setAttribute("xmlns:cbc", PeruUblNamespaces.CBC);
        root.setAttribute("xmlns:ext", PeruUblNamespaces.EXT);
        doc.appendChild(root);
        return root;
    }

    public static Element append(Document doc, Element parent, String namespace, String qName, String value) {
        Element child = doc.createElementNS(namespace, qName);
        if (value != null) {
            child.setTextContent(value);
        }
        parent.appendChild(child);
        return child;
    }

    public static Element appendAmount(Document doc, Element parent, String qName, String currencyCode, BigDecimal amount) {
        Element e = append(doc, parent, PeruUblNamespaces.CBC, qName, decimal(amount));
        e.setAttribute("currencyID", currencyCode);
        return e;
    }

    public static String decimal(BigDecimal value) {
        if (value == null) {
            return "0.00";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public static String decimalQty(BigDecimal value) {
        if (value == null) {
            return "0.000000";
        }
        return value.setScale(6, RoundingMode.HALF_UP).toPlainString();
    }
}
