package com.qespe.fiscal_service.infrastructure.persistence.adapter;

import com.qespe.fiscal_service.core.domain.enums.FiscalEnvironment;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalSeriesEntity;
import com.qespe.fiscal_service.infrastructure.persistence.repository.FiscalSeriesJpaRepository;
import com.qespe.fiscal_service.core.port.out.FiscalSeriesRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FiscalSeriesRepositoryAdapter implements FiscalSeriesRepositoryPort {

    private final FiscalSeriesJpaRepository repository;

    @Override
    public FiscalSeriesEntity save(FiscalSeriesEntity entity) {
        return repository.save(entity);
    }

    @Override
    public Optional<FiscalSeriesEntity> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public List<FiscalSeriesEntity> findByCompanyId(UUID companyId) {
        return repository.findByCompanyId(companyId);
    }

    @Override
    public List<FiscalSeriesEntity> findActiveForUpdate(UUID companyId, String countryCode, String taxAuthorityCode, String documentTypeCode, FiscalEnvironment environment) {
        return repository.findActiveForUpdate(companyId, countryCode, taxAuthorityCode, documentTypeCode, environment);
    }
}

