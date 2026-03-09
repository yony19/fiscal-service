package com.qespe.fiscal_service.infrastructure.persistence.adapter;

import com.qespe.fiscal_service.core.domain.enums.FiscalEmitterStatus;
import com.qespe.fiscal_service.core.domain.enums.FiscalEnvironment;
import com.qespe.fiscal_service.core.port.out.FiscalEmitterConfigRepositoryPort;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalEmitterConfigEntity;
import com.qespe.fiscal_service.infrastructure.persistence.repository.FiscalEmitterConfigJpaRepository;
import com.qespe.fiscal_service.infrastructure.persistence.repository.FiscalEmitterConfigSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FiscalEmitterConfigRepositoryAdapter implements FiscalEmitterConfigRepositoryPort {

    private final FiscalEmitterConfigJpaRepository repository;

    @Override
    public FiscalEmitterConfigEntity save(FiscalEmitterConfigEntity entity) {
        return repository.save(entity);
    }

    @Override
    public Optional<FiscalEmitterConfigEntity> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public List<FiscalEmitterConfigEntity> search(UUID companyId, String environment, FiscalEmitterStatus status, String countryCode, String taxAuthorityCode, String providerCode) {
        return repository.findAll(FiscalEmitterConfigSpecifications.byFilters(companyId, environment, status, countryCode, taxAuthorityCode, providerCode));
    }

    @Override
    public Optional<FiscalEmitterConfigEntity> findDefault(UUID companyId, String environment, String countryCode, String taxAuthorityCode) {
        return repository.findFirstByCompanyIdAndEnvironmentAndCountryCodeAndTaxAuthorityCodeAndStatusAndIsDefaultTrue(
                companyId,
                FiscalEnvironment.valueOf(environment.toUpperCase()),
                countryCode,
                taxAuthorityCode,
                FiscalEmitterStatus.ACTIVE
        );
    }

    @Override
    public void clearDefaultForScope(UUID companyId, String countryCode, String taxAuthorityCode, String environment, UUID excludeId) {
        repository.clearDefaultForScope(
                companyId,
                countryCode,
                taxAuthorityCode,
                FiscalEnvironment.valueOf(environment.toUpperCase()),
                excludeId
        );
    }
}
