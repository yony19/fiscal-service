package com.qespe.fiscal_service.core.usecase;

import com.qespe.fiscal_service.core.dto.certificate.CompanyCertificateResponse;
import com.qespe.fiscal_service.core.port.in.CertificateUploadUseCase;
import com.qespe.fiscal_service.core.port.out.CompanyCertificateRepositoryPort;
import com.qespe.fiscal_service.core.port.out.FiscalProviderConfigRepositoryPort;
import com.qespe.fiscal_service.infrastructure.persistence.entity.CompanyCertificateEntity;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalProviderConfigEntity;
import com.qespe.fiscal_service.shared.exception.BadRequestException;
import com.qespe.fiscal_service.shared.security.TestCertificateGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * openspec/changes/certificate-test-generation: el endpoint de generación
 * SHALL rechazar la operación cuando el Proveedor vinculado no está en TEST
 * — validado server-side, independiente de que la UI oculte el botón.
 */
@ExtendWith(MockitoExtension.class)
class GenerateTestCertificateServiceTest {

    @Mock
    private CompanyCertificateRepositoryPort certificateRepository;
    @Mock
    private FiscalProviderConfigRepositoryPort providerRepository;
    @Mock
    private TestCertificateGenerator generator;
    @Mock
    private CertificateUploadUseCase uploadUseCase;

    private GenerateTestCertificateService service;

    private final UUID certificateId = UUID.randomUUID();
    private final UUID providerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new GenerateTestCertificateService(certificateRepository, providerRepository, generator, uploadUseCase);
    }

    private CompanyCertificateEntity certLinkedTo(UUID providerId) {
        CompanyCertificateEntity c = new CompanyCertificateEntity();
        c.setId(certificateId);
        c.setAlias("Cert demo");
        c.setProviderId(providerId);
        return c;
    }

    private FiscalProviderConfigEntity providerWithEnvironment(String env) {
        FiscalProviderConfigEntity p = new FiscalProviderConfigEntity();
        p.setId(providerId);
        p.setEnvironment(env);
        return p;
    }

    @Test
    @DisplayName("Rechaza generar un certificado de prueba para un Proveedor en PROD")
    void rejectsGenerationForProdProvider() {
        when(certificateRepository.findById(certificateId)).thenReturn(Optional.of(certLinkedTo(providerId)));
        when(providerRepository.findById(providerId)).thenReturn(Optional.of(providerWithEnvironment("PROD")));

        assertThatThrownBy(() -> service.generateTest(certificateId))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(generator, uploadUseCase);
    }

    @Test
    @DisplayName("Rechaza generar si el certificado no tiene Proveedor vinculado")
    void rejectsGenerationWithoutLinkedProvider() {
        when(certificateRepository.findById(certificateId)).thenReturn(Optional.of(certLinkedTo(null)));

        assertThatThrownBy(() -> service.generateTest(certificateId))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(generator, uploadUseCase);
    }

    @Test
    @DisplayName("Genera y sube el certificado cuando el Proveedor está en TEST")
    void generatesForTestProvider() {
        when(certificateRepository.findById(certificateId)).thenReturn(Optional.of(certLinkedTo(providerId)));
        when(providerRepository.findById(providerId)).thenReturn(Optional.of(providerWithEnvironment("TEST")));
        when(generator.generate(anyString()))
                .thenReturn(new TestCertificateGenerator.GeneratedCertificate(new byte[]{1, 2, 3}, "pass123"));
        CompanyCertificateResponse expected = mock(CompanyCertificateResponse.class);
        when(uploadUseCase.uploadPfx(eq(certificateId), any(), eq("pass123"), anyString())).thenReturn(expected);

        CompanyCertificateResponse result = service.generateTest(certificateId);

        verify(uploadUseCase).uploadPfx(eq(certificateId), any(), eq("pass123"), anyString());
        org.assertj.core.api.Assertions.assertThat(result).isSameAs(expected);
    }
}
