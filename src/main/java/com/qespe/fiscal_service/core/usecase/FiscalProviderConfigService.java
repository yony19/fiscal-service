package com.qespe.fiscal_service.core.usecase;

import com.qespe.fiscal_service.core.dto.provider.FiscalProviderConfigRequest;
import com.qespe.fiscal_service.core.dto.provider.FiscalProviderConfigResponse;
import com.qespe.fiscal_service.infrastructure.mapper.FiscalProviderConfigMapper;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalProviderConfigEntity;
import com.qespe.fiscal_service.core.port.in.FiscalProviderConfigUseCase;
import com.qespe.fiscal_service.core.port.out.FiscalProviderConfigRepositoryPort;
import com.qespe.fiscal_service.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FiscalProviderConfigService implements FiscalProviderConfigUseCase {

    private final FiscalProviderConfigRepositoryPort repository;
    private final FiscalProviderConfigMapper mapper;

    @Override
    @Transactional
    public FiscalProviderConfigResponse create(FiscalProviderConfigRequest request) {
        FiscalProviderConfigEntity entity = mapper.toEntity(request);
        return mapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public FiscalProviderConfigResponse update(UUID id, FiscalProviderConfigRequest request) {
        FiscalProviderConfigEntity entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Fiscal provider config not found: " + id));
        mapper.updateEntity(request, entity);
        return mapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public FiscalProviderConfigResponse getById(UUID id) {
        return mapper.toResponse(repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Fiscal provider config not found: " + id)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FiscalProviderConfigResponse> list(UUID companyId) {
        return repository.findByCompanyId(companyId).stream()
                .map(mapper::toResponse)
                .toList();
    }
}

