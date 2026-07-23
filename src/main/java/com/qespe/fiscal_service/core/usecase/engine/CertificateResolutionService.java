package com.qespe.fiscal_service.core.usecase.engine;

import com.qespe.fiscal_service.core.domain.engine.CertificateContext;
import com.qespe.fiscal_service.core.port.out.CompanyCertificateRepositoryPort;
import com.qespe.fiscal_service.infrastructure.persistence.entity.CompanyCertificateEntity;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;
import com.qespe.fiscal_service.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CertificateResolutionService {

    private final CompanyCertificateRepositoryPort certificateRepository;

    /**
     * @param providerId el Proveedor efectivo ya resuelto (por ID, no por
     *                    texto) para este documento -- ver ProviderResolutionService.
     *                    Solo certificados vinculados exactamente a ese
     *                    Proveedor (provider_id) califican; uno sin vincular
     *                    o vinculado a otro Proveedor (aunque comparta
     *                    provider_code) queda excluido. Ver
     *                    openspec/changes/certificate-provider-binding.
     */
    public CertificateContext resolve(FiscalDocumentEntity document, UUID providerId) {
        List<CompanyCertificateEntity> active = certificateRepository.findActiveByCompany(document.getCompanyId());
        Instant now = Instant.now();

        List<CompanyCertificateEntity> valid = active.stream()
                .filter(c -> c.getValidFrom() != null && c.getValidTo() != null)
                .filter(c -> !now.isBefore(c.getValidFrom()) && !now.isAfter(c.getValidTo()))
                .filter(c -> c.getProviderId() != null && c.getProviderId().equals(providerId))
                .toList();

        if (valid.isEmpty()) {
            throw new BusinessException("No valid active certificate available for company");
        }

        CompanyCertificateEntity selected = valid.stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsDefault()))
                .findFirst()
                .orElse(valid.stream()
                        .sorted(Comparator.comparing(CompanyCertificateEntity::getValidTo))
                        .findFirst()
                        .orElseThrow(() -> new BusinessException("No certificate candidate found")));

        return new CertificateContext(
                selected.getId(),
                selected.getProviderCode(),
                selected.getAlias(),
                selected.getStorageMode(),
                selected.getCertificatePath(),
                selected.getPrivateKeyPath(),
                selected.getSecretRef(),
                selected.getPasswordSecretRef(),
                selected.getValidFrom(),
                selected.getValidTo()
        );
    }
}
