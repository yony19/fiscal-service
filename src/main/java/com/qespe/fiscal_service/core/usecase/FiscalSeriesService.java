package com.qespe.fiscal_service.core.usecase;

import com.qespe.fiscal_service.core.dto.series.FiscalSeriesRequest;
import com.qespe.fiscal_service.core.dto.series.FiscalSeriesResponse;
import com.qespe.fiscal_service.infrastructure.mapper.FiscalSeriesMapper;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalSeriesEntity;
import com.qespe.fiscal_service.core.port.in.FiscalSeriesUseCase;
import com.qespe.fiscal_service.core.port.out.FiscalSeriesRepositoryPort;
import com.qespe.fiscal_service.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FiscalSeriesService implements FiscalSeriesUseCase {

    private final FiscalSeriesRepositoryPort repository;
    private final FiscalSeriesMapper mapper;

    @Override
    @Transactional
    public FiscalSeriesResponse create(FiscalSeriesRequest request) {
        FiscalSeriesEntity entity = mapper.toEntity(request);
        if (entity.getVersion() == null) {
            entity.setVersion(0L);
        }
        return mapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public FiscalSeriesResponse update(UUID id, FiscalSeriesRequest request) {
        FiscalSeriesEntity entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Fiscal series not found: " + id));
        mapper.updateEntity(request, entity);
        return mapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public FiscalSeriesResponse getById(UUID id) {
        return mapper.toResponse(repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Fiscal series not found: " + id)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FiscalSeriesResponse> list(UUID companyId) {
        return repository.findByCompanyId(companyId).stream()
                .map(mapper::toResponse)
                .toList();
    }
}

