package com.qespe.fiscal_service.core.usecase.engine;

import com.qespe.fiscal_service.core.domain.engine.ProviderContext;
import com.qespe.fiscal_service.core.port.out.FiscalProviderConfigRepositoryPort;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalProviderConfigEntity;
import com.qespe.fiscal_service.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ProviderResolutionService {

    private final FiscalProviderConfigRepositoryPort providerRepository;

    public ProviderContext resolve(FiscalDocumentEntity document) {
        List<FiscalProviderConfigEntity> active = providerRepository.findActiveByScope(
                document.getCompanyId(),
                document.getCountryCode(),
                document.getTaxAuthorityCode(),
                document.getEnvironment().name()
        );

        if (active.isEmpty()) {
            throw new BusinessException("Active provider config not found for document scope");
        }

        FiscalProviderConfigEntity selected = active.stream()
                .sorted(Comparator.comparing(FiscalProviderConfigEntity::getPriority, Comparator.nullsLast(Integer::compareTo)))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Unable to resolve provider config"));

        return new ProviderContext(
                selected.getId(),
                selected.getProviderCode(),
                selected.getEnvironment(),
                selected.getEndpointSubmitUrl(),
                selected.getEndpointStatusUrl(),
                selected.getEndpointCdrUrl(),
                selected.getAuthType(),
                selected.getCredentialRef(),
                selected.getTimeoutMs(),
                selected.getMaxRetries(),
                selected.getRetryBackoffMs(),
                selected.getConfigJson()
        );
    }
}
