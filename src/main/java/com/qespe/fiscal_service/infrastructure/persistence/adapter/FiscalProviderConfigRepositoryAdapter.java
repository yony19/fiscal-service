package com.qespe.fiscal_service.infrastructure.persistence.adapter;

import com.qespe.fiscal_service.core.port.out.FiscalProviderConfigRepositoryPort;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalProviderConfigEntity;
import com.qespe.fiscal_service.infrastructure.persistence.repository.FiscalProviderConfigJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FiscalProviderConfigRepositoryAdapter implements FiscalProviderConfigRepositoryPort {

    private final FiscalProviderConfigJpaRepository repository;

    @Override
    public FiscalProviderConfigEntity save(FiscalProviderConfigEntity entity) {
        return repository.save(entity);
    }

    @Override
    public Optional<FiscalProviderConfigEntity> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public List<FiscalProviderConfigEntity> findByCompanyId(UUID companyId) {
        return repository.findByCompanyId(companyId);
    }

    @Override
    public List<FiscalProviderConfigEntity> findActiveByScope(UUID companyId, String countryCode, String taxAuthorityCode, String environment) {
        return repository.findByCompanyIdAndCountryCodeAndTaxAuthorityCodeAndEnvironmentAndActiveTrue(
                companyId,
                countryCode,
                taxAuthorityCode,
                environment
        );
    }
}
