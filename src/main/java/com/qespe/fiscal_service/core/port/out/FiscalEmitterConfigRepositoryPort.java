package com.qespe.fiscal_service.core.port.out;

import com.qespe.fiscal_service.core.domain.enums.FiscalEmitterStatus;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalEmitterConfigEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FiscalEmitterConfigRepositoryPort {

    FiscalEmitterConfigEntity save(FiscalEmitterConfigEntity entity);

    Optional<FiscalEmitterConfigEntity> findById(UUID id);

    List<FiscalEmitterConfigEntity> search(
            UUID companyId,
            String environment,
            FiscalEmitterStatus status,
            String countryCode,
            String taxAuthorityCode,
            String providerCode
    );

    Optional<FiscalEmitterConfigEntity> findDefault(
            UUID companyId,
            String environment,
            String countryCode,
            String taxAuthorityCode
    );

    void clearDefaultForScope(UUID companyId, String countryCode, String taxAuthorityCode, String environment, UUID excludeId);
}
