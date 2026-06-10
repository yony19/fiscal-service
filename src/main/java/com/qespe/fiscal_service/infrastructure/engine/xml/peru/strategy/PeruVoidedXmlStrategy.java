package com.qespe.fiscal_service.infrastructure.engine.xml.peru.strategy;

import com.qespe.fiscal_service.core.domain.engine.EmitterContext;
import com.qespe.fiscal_service.infrastructure.engine.xml.peru.util.PeruUblNamespaces;
import com.qespe.fiscal_service.infrastructure.engine.xml.peru.util.XmlDomUtils;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.time.LocalDate;

/**
 * Estrategia UBL para la <strong>Comunicacion de Baja (RA)</strong> — la anulacion
 * de un comprobante ya emitido ante SUNAT (E1.2 / PRD #1).
 *
 * <p>Es un documento <em>resumen</em>: se envia con {@code sendSummary} (async),
 * SUNAT devuelve un ticket y luego se consulta el estado. Esta clase solo construye
 * el XML; el envio async lo maneja el sender (pendiente: ver {@code sendSummary}).
 *
 * <p><strong>Diferencias clave con un comprobante normal</strong> (motivos #1 de
 * rechazo SUNAT si se equivocan):
 * <ul>
 *   <li>Raiz {@code VoidedDocuments} en su propio namespace + {@code sac}/{@code ds}
 *       ({@link XmlDomUtils#createSummaryRoot}).</li>
 *   <li>Cabecera UBL <strong>2.0 / 1.0</strong> (no 2.1/2.0 como las facturas).</li>
 *   <li>{@code cbc:ID} con formato {@code RA-YYYYMMDD-NNN} (lo provee el numerador
 *       aguas arriba via {@code getFullNumber()}).</li>
 *   <li>{@code cbc:ReferenceDate} = fecha de EMISION del comprobante anulado;
 *       {@code cbc:IssueDate} = fecha de generacion del RA.</li>
 * </ul>
 *
 * <p>Modela el caso "1 RA anula 1 comprobante" usando los campos
 * {@code relatedDocument*}. Agrupar N comprobantes en un RA es una mejora futura
 * (poblar varias {@code VoidedDocumentsLine}).
 *
 * <p><strong>Pendiente de homologacion SUNAT:</strong> la firma {@code ds:Signature}
 * (se inyecta en {@code ext:ExtensionContent} por el firmador) y el formato/unicidad
 * del ID solo se validan enviando a SUNAT beta.
 */
@Component
public class PeruVoidedXmlStrategy extends BasePeruUblDocumentXmlStrategy {

    private static final String DEFAULT_VOID_REASON = "ANULACION";

    @Override
    public boolean supports(String documentType) {
        return "VOID".equalsIgnoreCase(documentType);
    }

    @Override
    public Document build(FiscalDocumentEntity document, EmitterContext emitterContext) {
        Document xml = XmlDomUtils.createDocument();
        Element root = XmlDomUtils.createSummaryRoot(xml, PeruUblNamespaces.UBL_VOIDED, "VoidedDocuments");

        // ext:UBLExtensions/ext:UBLExtension/ext:ExtensionContent (vacio; el firmador
        // inyecta aqui el ds:Signature). CRITICO que exista aunque vacio.
        Element extensions = XmlDomUtils.append(xml, root, PeruUblNamespaces.EXT, "ext:UBLExtensions", null);
        Element extension = XmlDomUtils.append(xml, extensions, PeruUblNamespaces.EXT, "ext:UBLExtension", null);
        XmlDomUtils.append(xml, extension, PeruUblNamespaces.EXT, "ext:ExtensionContent", null);

        // Cabecera RA: OJO 2.0 / 1.0 (no 2.1/2.0).
        XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:UBLVersionID", "2.0");
        XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:CustomizationID", "1.0");
        XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:ID", document.getFullNumber());

        LocalDate referenceDate = resolveReferenceDate(document);
        XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:ReferenceDate", referenceDate.toString());
        XmlDomUtils.append(xml, root, PeruUblNamespaces.CBC, "cbc:IssueDate", document.getIssueDate().toString());

        appendSignaturePlaceholder(xml, root, emitterContext);
        appendVoidedSupplier(xml, root, emitterContext);

        // sac:VoidedDocumentsLine — una por comprobante anulado. Caso simple:
        // un RA anula el comprobante referenciado en relatedDocument*.
        Element vLine = XmlDomUtils.append(xml, root, PeruUblNamespaces.SAC, "sac:VoidedDocumentsLine", null);
        XmlDomUtils.append(xml, vLine, PeruUblNamespaces.CBC, "cbc:LineID", "1");
        // Catalogo 01: 01 factura, 03 boleta, 07 NC, 08 ND.
        XmlDomUtils.append(xml, vLine, PeruUblNamespaces.CBC, "cbc:DocumentTypeCode",
                safe(document.getRelatedDocumentTypeCode()));
        String[] serieNumero = splitSerieNumero(safe(document.getRelatedDocumentNumber(), document.getFullNumber()));
        XmlDomUtils.append(xml, vLine, PeruUblNamespaces.SAC, "sac:DocumentSerialID", serieNumero[0]);
        XmlDomUtils.append(xml, vLine, PeruUblNamespaces.SAC, "sac:DocumentNumberID", serieNumero[1]);
        XmlDomUtils.append(xml, vLine, PeruUblNamespaces.SAC, "sac:VoidReasonDescription", DEFAULT_VOID_REASON);

        return xml;
    }

    /**
     * Fecha de emision del comprobante anulado. Preferimos navegar al documento
     * relacionado; si no esta disponible, caemos a la fecha del propio RA (SUNAT
     * puede rechazar si no coincide con la emision real — es un fallback).
     */
    private LocalDate resolveReferenceDate(FiscalDocumentEntity document) {
        if (document.getRelatedDocument() != null && document.getRelatedDocument().getIssueDate() != null) {
            return document.getRelatedDocument().getIssueDate();
        }
        return document.getIssueDate();
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

    private void appendVoidedSupplier(Document xml, Element root, EmitterContext ctx) {
        Element supplier = XmlDomUtils.append(xml, root, PeruUblNamespaces.CAC, "cac:AccountingSupplierParty", null);
        XmlDomUtils.append(xml, supplier, PeruUblNamespaces.CBC, "cbc:CustomerAssignedAccountID", safe(ctx.documentNumber()));
        XmlDomUtils.append(xml, supplier, PeruUblNamespaces.CBC, "cbc:AdditionalAccountID", "6"); // catalogo 06 = RUC
        Element party = XmlDomUtils.append(xml, supplier, PeruUblNamespaces.CAC, "cac:Party", null);
        Element legal = XmlDomUtils.append(xml, party, PeruUblNamespaces.CAC, "cac:PartyLegalEntity", null);
        XmlDomUtils.append(xml, legal, PeruUblNamespaces.CBC, "cbc:RegistrationName", safe(ctx.legalName()));
    }

    /** Parte "B001-123" -> ["B001", "123"] (numero sin ceros a la izquierda). */
    private String[] splitSerieNumero(String full) {
        if (full != null && full.contains("-")) {
            int i = full.indexOf('-');
            return new String[]{full.substring(0, i), stripLeadingZeros(full.substring(i + 1))};
        }
        return new String[]{full == null ? "-" : full, "0"};
    }

    private String stripLeadingZeros(String n) {
        String s = n.replaceFirst("^0+", "");
        return s.isEmpty() ? "0" : s;
    }
}
