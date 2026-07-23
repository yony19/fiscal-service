package com.qespe.fiscal_service.core.usecase;

import com.qespe.fiscal_service.core.dto.certificate.CompanyCertificateResponse;
import com.qespe.fiscal_service.core.port.in.CertificateUploadUseCase;
import com.qespe.fiscal_service.core.port.in.GenerateTestCertificateUseCase;
import com.qespe.fiscal_service.core.port.out.CompanyCertificateRepositoryPort;
import com.qespe.fiscal_service.core.port.out.FiscalProviderConfigRepositoryPort;
import com.qespe.fiscal_service.infrastructure.persistence.entity.CompanyCertificateEntity;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalProviderConfigEntity;
import com.qespe.fiscal_service.shared.exception.BadRequestException;
import com.qespe.fiscal_service.shared.exception.NotFoundException;
import com.qespe.fiscal_service.shared.security.TestCertificateGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Ver openspec/changes/certificate-test-generation. El certificado generado
 * entra por el MISMO pipeline que un .pfx subido a mano
 * ({@link CertificateUploadUseCase#uploadPfx}) — no duplica la lógica de
 * extracción de metadata ni de cifrado de contraseña, así que hereda
 * automáticamente cualquier fix futuro a ese pipeline.
 */
@Service
@RequiredArgsConstructor
public class GenerateTestCertificateService implements GenerateTestCertificateUseCase {

    private static final String TEST_ALIAS_PREFIX = "TEST-";

    private final CompanyCertificateRepositoryPort certificateRepository;
    private final FiscalProviderConfigRepositoryPort providerRepository;
    private final TestCertificateGenerator generator;
    private final CertificateUploadUseCase uploadUseCase;

    @Override
    @Transactional
    public CompanyCertificateResponse generateTest(UUID certificateId) {
        CompanyCertificateEntity entity = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new NotFoundException("Certificado no encontrado: " + certificateId));

        if (entity.getProviderId() == null) {
            throw new BadRequestException(
                    "Este certificado no está vinculado a un Proveedor todavía. Elige un Proveedor antes de generar el certificado de prueba.");
        }
        FiscalProviderConfigEntity provider = providerRepository.findById(entity.getProviderId())
                .orElseThrow(() -> new BadRequestException("El proveedor vinculado ya no existe."));

        // Bloqueo estricto server-side, independiente de la UI: nunca se genera
        // un certificado de prueba para un Proveedor en PROD.
        if (!"TEST".equalsIgnoreCase(provider.getEnvironment())) {
            throw new BadRequestException(
                    "Solo se puede generar un certificado de prueba para un Proveedor en entorno TEST.");
        }

        // Alias con prefijo reconocible -- evita que se confunda con un
        // certificado real de producción en la tabla.
        if (entity.getAlias() == null || !entity.getAlias().startsWith(TEST_ALIAS_PREFIX)) {
            entity.setAlias(TEST_ALIAS_PREFIX + (entity.getAlias() == null ? "" : entity.getAlias()));
            certificateRepository.save(entity);
        }

        TestCertificateGenerator.GeneratedCertificate generated = generator.generate(entity.getAlias());

        return uploadUseCase.uploadPfx(
                certificateId,
                generated.pkcs12Bytes(),
                generated.password(),
                "generado-automaticamente-test.pfx"
        );
    }
}
