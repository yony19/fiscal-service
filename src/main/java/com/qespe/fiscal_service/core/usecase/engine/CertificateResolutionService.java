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

@Component
@RequiredArgsConstructor
public class CertificateResolutionService {

    private final CompanyCertificateRepositoryPort certificateRepository;

    public CertificateContext resolve(FiscalDocumentEntity document, String providerCode) {
        List<CompanyCertificateEntity> active = certificateRepository.findActiveByCompany(document.getCompanyId());
        Instant now = Instant.now();

        List<CompanyCertificateEntity> valid = active.stream()
                .filter(c -> c.getValidFrom() != null && c.getValidTo() != null)
                .filter(c -> !now.isBefore(c.getValidFrom()) && !now.isAfter(c.getValidTo()))
                .toList();

        if (valid.isEmpty()) {
            throw new BusinessException("No valid active certificate available for company");
        }

        CompanyCertificateEntity selected = valid.stream()
                .filter(c -> providerCode != null && providerCode.equalsIgnoreCase(c.getProviderCode()))
                .filter(c -> Boolean.TRUE.equals(c.getIsDefault()))
                .findFirst()
                .or(() -> valid.stream().filter(c -> Boolean.TRUE.equals(c.getIsDefault())).findFirst())
                .orElse(valid.stream()
                        .sorted(Comparator.comparing(CompanyCertificateEntity::getValidTo))
                        .findFirst()
                        .orElseThrow(() -> new BusinessException("No certificate candidate found")));

        return new CertificateContext(
                selected.getId(),
                selected.getProviderCode(),
                selected.getAlias(),
                selected.getStorageMode(),
                selected.getSecretRef(),
                selected.getPasswordSecretRef(),
                selected.getValidFrom(),
                selected.getValidTo()
        );
    }
}
