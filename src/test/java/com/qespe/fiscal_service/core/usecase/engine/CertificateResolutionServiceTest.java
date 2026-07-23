package com.qespe.fiscal_service.core.usecase.engine;

import com.qespe.fiscal_service.core.domain.engine.CertificateContext;
import com.qespe.fiscal_service.core.port.out.CompanyCertificateRepositoryPort;
import com.qespe.fiscal_service.infrastructure.persistence.entity.CompanyCertificateEntity;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;
import com.qespe.fiscal_service.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * openspec/changes/certificate-provider-binding: la resolución de
 * "certificado efectivo" para firmar filtra por provider_id (FK real) exacto
 * al Proveedor efectivo — nunca por coincidencia de texto de provider_code,
 * que no distingue entorno TEST/PROD.
 */
@ExtendWith(MockitoExtension.class)
class CertificateResolutionServiceTest {

    @Mock
    private CompanyCertificateRepositoryPort repository;

    private CertificateResolutionService service;

    private final UUID companyId = UUID.randomUUID();
    private final UUID testProviderId = UUID.randomUUID();
    private final UUID prodProviderId = UUID.randomUUID();

    private FiscalDocumentEntity document;

    @BeforeEach
    void setUp() {
        service = new CertificateResolutionService(repository);
        document = new FiscalDocumentEntity();
        document.setCompanyId(companyId);
    }

    private CompanyCertificateEntity cert(UUID providerId, boolean isDefault, String providerCode) {
        CompanyCertificateEntity c = new CompanyCertificateEntity();
        c.setId(UUID.randomUUID());
        c.setCompanyId(companyId);
        c.setProviderId(providerId);
        c.setProviderCode(providerCode);
        c.setAlias("cert-" + providerCode);
        c.setStatus("ACTIVE");
        c.setIsDefault(isDefault);
        c.setValidFrom(Instant.now().minus(1, ChronoUnit.DAYS));
        c.setValidTo(Instant.now().plus(365, ChronoUnit.DAYS));
        return c;
    }

    @Test
    @DisplayName("Resuelve el certificado cuyo provider_id coincide exactamente con el Proveedor efectivo")
    void resolvesByExactProviderId() {
        CompanyCertificateEntity testCert = cert(testProviderId, true, "SUNAT");
        CompanyCertificateEntity prodCert = cert(prodProviderId, true, "SUNAT");
        when(repository.findActiveByCompany(companyId)).thenReturn(List.of(testCert, prodCert));

        CertificateContext result = service.resolve(document, testProviderId);

        assertThat(result.certificateId()).isEqualTo(testCert.getId());
    }

    @Test
    @DisplayName("Un certificado vinculado a OTRO Proveedor (aunque comparta provider_code) nunca califica")
    void excludesCertificateLinkedToDifferentProviderEvenWithSameCode() {
        CompanyCertificateEntity prodCert = cert(prodProviderId, true, "SUNAT");
        when(repository.findActiveByCompany(companyId)).thenReturn(List.of(prodCert));

        assertThatThrownBy(() -> service.resolve(document, testProviderId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Un certificado sin provider_id vinculado (null) nunca es candidato")
    void excludesCertificateWithoutLinkedProvider() {
        CompanyCertificateEntity unlinked = cert(null, true, "SUNAT");
        when(repository.findActiveByCompany(companyId)).thenReturn(List.of(unlinked));

        assertThatThrownBy(() -> service.resolve(document, testProviderId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Entre varios candidatos del mismo Proveedor, gana el marcado como default")
    void prefersDefaultAmongSameProvider() {
        CompanyCertificateEntity notDefault = cert(testProviderId, false, "SUNAT");
        CompanyCertificateEntity isDefault = cert(testProviderId, true, "SUNAT");
        when(repository.findActiveByCompany(companyId)).thenReturn(List.of(notDefault, isDefault));

        CertificateContext result = service.resolve(document, testProviderId);

        assertThat(result.certificateId()).isEqualTo(isDefault.getId());
    }
}
