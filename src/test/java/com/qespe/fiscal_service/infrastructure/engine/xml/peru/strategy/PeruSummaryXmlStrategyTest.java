package com.qespe.fiscal_service.infrastructure.engine.xml.peru.strategy;

import com.qespe.fiscal_service.core.domain.engine.EmitterContext;
import com.qespe.fiscal_service.core.domain.enums.FiscalDocumentStatus;
import com.qespe.fiscal_service.core.port.out.FiscalDocumentRepositoryPort;
import com.qespe.fiscal_service.infrastructure.engine.xml.peru.util.PeruUblNamespaces;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;
import com.qespe.fiscal_service.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests del XML del Resumen Diario / SummaryDocuments (E1.2 / PRD #2).
 * Trampas que SUNAT rechaza y que estos tests fijan: CustomizationID debe ser
 * 1.1 (la RA usa 1.0), cada linea es UNA boleta con sus montos por afectacion
 * (BillingPayment 01/02/03) e IGV, y ReferenceDate es la fecha RESUMIDA (no la
 * de generacion del RC).
 */
@ExtendWith(MockitoExtension.class)
class PeruSummaryXmlStrategyTest {

    @Mock
    private FiscalDocumentRepositoryPort repository;

    private PeruSummaryXmlStrategy strategy;
    private EmitterContext emitter;

    private final UUID companyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        strategy = new PeruSummaryXmlStrategy(repository);
        emitter = new EmitterContext(
                UUID.randomUUID(), "RUC", "20123456789",
                "MI EMPRESA SAC", "Mi Empresa", "Av. Siempre Viva 123");
    }

    private FiscalDocumentEntity rcDocument() {
        FiscalDocumentEntity rc = new FiscalDocumentEntity();
        rc.setCompanyId(companyId);
        rc.setDocumentType("DAILY_SUMMARY");
        rc.setFullNumber("RC-20260610-1");
        rc.setIssueDate(LocalDate.of(2026, 6, 10));
        rc.setRelatedDocumentNumber("2026-06-09"); // fecha resumida
        rc.setCurrencyCode("PEN");
        return rc;
    }

    private FiscalDocumentEntity boleta(String fullNumber, String taxable, String tax, String total) {
        FiscalDocumentEntity b = new FiscalDocumentEntity();
        b.setCompanyId(companyId);
        b.setDocumentTypeCode("03");
        b.setFullNumber(fullNumber);
        b.setIssueDate(LocalDate.of(2026, 6, 9));
        b.setStatus(FiscalDocumentStatus.SIGNED);
        b.setCustomerDocumentType("DNI");
        b.setCustomerDocumentNumber("45678912");
        b.setTaxableAmount(new BigDecimal(taxable));
        b.setTaxAmount(new BigDecimal(tax));
        b.setTotalAmount(new BigDecimal(total));
        b.setExemptAmount(BigDecimal.ZERO);
        b.setUnaffectedAmount(BigDecimal.ZERO);
        return b;
    }

    private static String text(Document xml, String tag) {
        NodeList list = xml.getElementsByTagName(tag);
        return list.getLength() > 0 ? list.item(0).getTextContent() : null;
    }

    @Test
    @DisplayName("supports: solo DAILY_SUMMARY")
    void supportsOnlyDailySummary() {
        assertThat(strategy.supports("DAILY_SUMMARY")).isTrue();
        assertThat(strategy.supports("VOID")).isFalse();
        assertThat(strategy.supports("INVOICE")).isFalse();
    }

    @Test
    @DisplayName("Raiz SummaryDocuments + cabecera 2.0 / 1.1 (la RA usa 1.0 — confundirlas es rechazo)")
    void rootAndHeader() {
        when(repository.findBoletasByIssueDate(companyId, LocalDate.of(2026, 6, 9)))
                .thenReturn(List.of(boleta("B001-00000001", "100.00", "18.00", "118.00")));

        Document xml = strategy.build(rcDocument(), emitter);
        Element root = xml.getDocumentElement();

        assertThat(root.getLocalName()).isEqualTo("SummaryDocuments");
        assertThat(root.getNamespaceURI()).isEqualTo(PeruUblNamespaces.UBL_SUMMARY);
        assertThat(text(xml, "cbc:UBLVersionID")).isEqualTo("2.0");
        assertThat(text(xml, "cbc:CustomizationID")).isEqualTo("1.1");
        assertThat(text(xml, "cbc:ID")).isEqualTo("RC-20260610-1");
    }

    @Test
    @DisplayName("ReferenceDate = fecha RESUMIDA (de las boletas); IssueDate = generacion del RC")
    void referenceDateIsSummarizedDate() {
        when(repository.findBoletasByIssueDate(companyId, LocalDate.of(2026, 6, 9)))
                .thenReturn(List.of(boleta("B001-00000001", "100.00", "18.00", "118.00")));

        Document xml = strategy.build(rcDocument(), emitter);

        assertThat(text(xml, "cbc:ReferenceDate")).isEqualTo("2026-06-09");
        assertThat(text(xml, "cbc:IssueDate")).isEqualTo("2026-06-10");
    }

    @Test
    @DisplayName("Una linea por boleta: ID serie-numero, tipo 03, estado 1, total, gravado e IGV con currencyID")
    void linePerBoletaWithAmounts() {
        when(repository.findBoletasByIssueDate(companyId, LocalDate.of(2026, 6, 9)))
                .thenReturn(List.of(
                        boleta("B001-00000001", "100.00", "18.00", "118.00"),
                        boleta("B001-00000002", "50.00", "9.00", "59.00")));

        Document xml = strategy.build(rcDocument(), emitter);

        NodeList lines = xml.getElementsByTagName("sac:SummaryDocumentsLine");
        assertThat(lines.getLength()).isEqualTo(2);

        Element first = (Element) lines.item(0);
        assertThat(first.getElementsByTagName("cbc:ID").item(0).getTextContent())
                .isEqualTo("B001-00000001");
        assertThat(first.getElementsByTagName("cbc:DocumentTypeCode").item(0).getTextContent())
                .isEqualTo("03");
        assertThat(first.getElementsByTagName("cbc:ConditionCode").item(0).getTextContent())
                .isEqualTo("1");

        Element totalAmount = (Element) first.getElementsByTagName("sac:TotalAmount").item(0);
        assertThat(totalAmount.getTextContent()).isEqualTo("118.00");
        assertThat(totalAmount.getAttribute("currencyID")).isEqualTo("PEN");

        Element paid = (Element) first.getElementsByTagName("cbc:PaidAmount").item(0);
        assertThat(paid.getTextContent()).isEqualTo("100.00");
        assertThat(first.getElementsByTagName("cbc:InstructionID").item(0).getTextContent())
                .isEqualTo("01");

        Element igv = (Element) first.getElementsByTagName("cbc:TaxAmount").item(0);
        assertThat(igv.getTextContent()).isEqualTo("18.00");
    }

    @Test
    @DisplayName("Receptor de la boleta: DNI -> AdditionalAccountID 1 (catalogo 06)")
    void customerDocTypeMapped() {
        when(repository.findBoletasByIssueDate(companyId, LocalDate.of(2026, 6, 9)))
                .thenReturn(List.of(boleta("B001-00000001", "100.00", "18.00", "118.00")));

        Document xml = strategy.build(rcDocument(), emitter);

        assertThat(text(xml, "cbc:AdditionalAccountID")).isNotNull();
        NodeList customers = xml.getElementsByTagName("cac:AccountingCustomerParty");
        Element customer = (Element) customers.item(0);
        assertThat(customer.getElementsByTagName("cbc:CustomerAssignedAccountID").item(0).getTextContent())
                .isEqualTo("45678912");
        assertThat(customer.getElementsByTagName("cbc:AdditionalAccountID").item(0).getTextContent())
                .isEqualTo("1");
    }

    @Test
    @DisplayName("Sin boletas en la fecha resumida -> BusinessException")
    void noBoletasThrows() {
        when(repository.findBoletasByIssueDate(any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> strategy.build(rcDocument(), emitter))
                .isInstanceOf(BusinessException.class);
    }
}
