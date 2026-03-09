package com.qespe.fiscal_service.core.usecase;

import com.qespe.fiscal_service.core.dto.certificate.CompanyCertificateRequest;
import com.qespe.fiscal_service.core.dto.certificate.CompanyCertificateResponse;
import com.qespe.fiscal_service.infrastructure.mapper.CompanyCertificateMapper;
import com.qespe.fiscal_service.infrastructure.persistence.entity.CompanyCertificateEntity;
import com.qespe.fiscal_service.core.port.in.CompanyCertificateUseCase;
import com.qespe.fiscal_service.core.port.out.CompanyCertificateRepositoryPort;
import com.qespe.fiscal_service.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanyCertificateService implements CompanyCertificateUseCase {

    private final CompanyCertificateRepositoryPort repository;
    private final CompanyCertificateMapper mapper;

    @Override
    @Transactional
    public CompanyCertificateResponse create(CompanyCertificateRequest request) {
        CompanyCertificateEntity entity = mapper.toEntity(request);
        return mapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public CompanyCertificateResponse update(UUID id, CompanyCertificateRequest request) {
        CompanyCertificateEntity entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Company certificate not found: " + id));
        mapper.updateEntity(request, entity);
        return mapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyCertificateResponse getById(UUID id) {
        return mapper.toResponse(repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Company certificate not found: " + id)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompanyCertificateResponse> list(UUID companyId) {
        return repository.findByCompanyId(companyId).stream()
                .map(mapper::toResponse)
                .toList();
    }
}

