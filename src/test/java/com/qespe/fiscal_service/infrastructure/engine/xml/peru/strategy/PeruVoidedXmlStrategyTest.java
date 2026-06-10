package com.qespe.fiscal_service.infrastructure.engine.xml.peru.strategy;

import com.qespe.fiscal_service.core.domain.engine.EmitterContext;
import com.qespe.fiscal_service.infrastructure.engine.xml.peru.util.PeruUblNamespaces;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests del XML de la Comunicacion de Baja / VoidedDocuments (E1.2).
 * SUNAT rechaza el RA entero por errores de estructura: cabecera UBL
 * equivocada (debe ser 2.0/1.0, NO 2.1), namespace raiz incorrecto,
 * ReferenceDate que no es la fecha de emision del comprobante anulado, o
 * serie/numero mal partidos. Estos tests fijan exactamente esas trampas.
 */
class PeruVoidedXmlStrategyTest {

    private PeruVoidedXmlStrategy strategy;
    private EmitterContext emitter;

    @BeforeEach
    void setUp() {
        strategy = new PeruVoidedXmlStrategy();
        emitter = new EmitterContext(
                UUID.randomUUID(), "RUC", "20123456789",
                "MI EMPRESA SAC", "Mi Empresa", "Av. Siempre Viva 123");
    }

    private FiscalDocumentEntity voidDocument() {
        FiscalDocumentEntity related = new FiscalDocumentEntity();
        related.setIssueDate(LocalDate.of(2026, 6, 1));

        FiscalDocumentEntity doc = new FiscalDocumentEntity();
        doc.setDocumentType("VOID");
        doc.setFullNumber("RA-20260603-001");
        doc.setIssueDate(LocalDate.of(2026, 6, 3));
        doc.setRelatedDocument(related);
        doc.setRelatedDocumentTypeCode("03"); // catalogo 01: boleta
        doc.setRelatedDocumentNumber("B001-00000123");
        return doc;
    }

    private static String text(Document xml, String tag) {
        NodeList list = xml.getElementsByTagName(tag);
        return list.getLength() > 0 ? list.item(0).getTextContent() : null;
    }

    @Test
    @DisplayName("supports: solo VOID")
    void supportsOnlyVoid() {
        assertThat(strategy.supports("VOID")).isTrue();
        assertThat(strategy.supports("void")).isTrue();
        assertThat(strategy.supports("INVOICE")).isFalse();
        assertThat(strategy.supports("DAILY_SUMMARY")).isFalse();
    }

    @Test
    @DisplayName("Raiz VoidedDocuments en el namespace SUNAT correcto")
    void rootElementAndNamespace() {
        Document xml = strategy.build(voidDocument(), emitter);
        Element root = xml.getDocumentElement();

        assertThat(root.getLocalName()).isEqualTo("VoidedDocuments");
        assertThat(root.getNamespaceURI()).isEqualTo(PeruUblNamespaces.UBL_VOIDED);
    }

    @Test
    @DisplayName("Cabecera UBL 2.0 / CustomizationID 1.0 (NO 2.1 como las facturas) — rechazo tipico de SUNAT")
    void ublHeaderVersionsAreSummaryVersions() {
        Document xml = strategy.build(voidDocument(), emitter);

        assertThat(text(xml, "cbc:UBLVersionID")).isEqualTo("2.0");
        assertThat(text(xml, "cbc:CustomizationID")).isEqualTo("1.0");
    }

    @Test
    @DisplayName("cbc:ID toma el numero RA-YYYYMMDD-NNN del documento")
    void idIsFullNumber() {
        Document xml = strategy.build(voidDocument(), emitter);

        assertThat(text(xml, "cbc:ID")).isEqualTo("RA-20260603-001");
    }

    @Test
    @DisplayName("ReferenceDate = emision del comprobante ANULADO; IssueDate = generacion del RA")
    void referenceDateComesFromRelatedDocument() {
        Document xml = strategy.build(voidDocument(), emitter);

        assertThat(text(xml, "cbc:ReferenceDate")).isEqualTo("2026-06-01");
        assertThat(text(xml, "cbc:IssueDate")).isEqualTo("2026-06-03");
    }

    @Test
    @DisplayName("Sin documento relacionado navegable: ReferenceDate cae a la fecha del RA (fallback)")
    void referenceDateFallsBackToOwnIssueDate() {
        FiscalDocumentEntity doc = voidDocument();
        doc.setRelatedDocument(null);

        Document xml = strategy.build(doc, emitter);

        assertThat(text(xml, "cbc:ReferenceDate")).isEqualTo("2026-06-03");
    }

    @Test
    @DisplayName("Linea de baja: serie B001 + numero 123 (sin ceros a la izquierda) + tipo 03 + motivo")
    void voidedLineSplitsSerieAndNumber() {
        Document xml = strategy.build(voidDocument(), emitter);

        assertThat(text(xml, "cbc:DocumentTypeCode")).isEqualTo("03");
        assertThat(text(xml, "sac:DocumentSerialID")).isEqualTo("B001");
        assertThat(text(xml, "sac:DocumentNumberID")).isEqualTo("123");
        assertThat(text(xml, "sac:VoidReasonDescription")).isEqualTo("ANULACION");
        assertThat(text(xml, "cbc:LineID")).isEqualTo("1");
    }

    @Test
    @DisplayName("ExtensionContent existe y esta vacio (el firmador inyecta ahi el ds:Signature)")
    void extensionContentPlaceholderExists() {
        Document xml = strategy.build(voidDocument(), emitter);

        NodeList content = xml.getElementsByTagName("ext:ExtensionContent");
        assertThat(content.getLength()).isEqualTo(1);
        assertThat(content.item(0).getChildNodes().getLength()).isZero();
    }

    @Test
    @DisplayName("Emisor: RUC en CustomerAssignedAccountID, AdditionalAccountID=6 (catalogo 06) y razon social")
    void supplierBlockCarriesEmitter() {
        Document xml = strategy.build(voidDocument(), emitter);

        assertThat(text(xml, "cbc:CustomerAssignedAccountID")).isEqualTo("20123456789");
        assertThat(text(xml, "cbc:AdditionalAccountID")).isEqualTo("6");
        assertThat(text(xml, "cbc:RegistrationName")).isEqualTo("MI EMPRESA SAC");
    }
}
