package com.qespe.fiscal_service.core.port.out;

import com.qespe.fiscal_service.core.domain.enums.FiscalEnvironment;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalSeriesEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FiscalSeriesRepositoryPort {
    FiscalSeriesEntity save(FiscalSeriesEntity entity);
    Optional<FiscalSeriesEntity> findById(UUID id);
    List<FiscalSeriesEntity> findByCompanyId(UUID companyId);
    List<FiscalSeriesEntity> findActiveForUpdate(UUID companyId, String countryCode, String taxAuthorityCode, String documentTypeCode, FiscalEnvironment environment);
}

