package com.qespe.fiscal_service.core.usecase;

import com.qespe.fiscal_service.core.dto.certificate.CompanyCertificateRequest;
import com.qespe.fiscal_service.core.dto.certificate.CompanyCertificateResponse;
import com.qespe.fiscal_service.infrastructure.mapper.CompanyCertificateMapper;
import com.qespe.fiscal_service.infrastructure.persistence.entity.CompanyCertificateEntity;
import com.qespe.fiscal_service.core.port.in.CompanyCertificateUseCase;
import com.qespe.fiscal_service.core.port.out.CompanyCertificateRepositoryPort;
import com.qespe.fiscal_service.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        CompanyCertificateEntity saved = repository.save(entity);
        enforceSingleDefault(saved);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public CompanyCertificateResponse update(UUID id, CompanyCertificateRequest request) {
        CompanyCertificateEntity entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Company certificate not found: " + id));
        mapper.updateEntity(request, entity);
        CompanyCertificateEntity saved = repository.save(entity);
        enforceSingleDefault(saved);
        return mapper.toResponse(saved);
    }

    /**
     * Un unico certificado por defecto por empresa: si el que se acaba de guardar
     * quedo como default, desmarca los demas de la misma compania. Antes se podian
     * tener varios "por defecto", dejando ambiguo cual se usaba al firmar.
     */
    private void enforceSingleDefault(CompanyCertificateEntity saved) {
        if (!Boolean.TRUE.equals(saved.getIsDefault())) {
            return;
        }
        for (CompanyCertificateEntity other : repository.findByCompanyId(saved.getCompanyId())) {
            if (!other.getId().equals(saved.getId()) && Boolean.TRUE.equals(other.getIsDefault())) {
                other.setIsDefault(false);
                repository.save(other);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyCertificateResponse getById(UUID id) {
        return mapper.toResponse(repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Company certificate not found: " + id)));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CompanyCertificateResponse> list(UUID companyId, Pageable pageable) {
        return repository.findByCompanyId(companyId, pageable).map(mapper::toResponse);
    }
}
