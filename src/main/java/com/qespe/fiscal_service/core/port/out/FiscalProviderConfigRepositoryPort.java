package com.qespe.fiscal_service.core.port.out;

import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalProviderConfigEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FiscalProviderConfigRepositoryPort {
    FiscalProviderConfigEntity save(FiscalProviderConfigEntity entity);
    Optional<FiscalProviderConfigEntity> findById(UUID id);
    List<FiscalProviderConfigEntity> findByCompanyId(UUID companyId);
    List<FiscalProviderConfigEntity> findActiveByScope(UUID companyId, String countryCode, String taxAuthorityCode, String environment);
}
