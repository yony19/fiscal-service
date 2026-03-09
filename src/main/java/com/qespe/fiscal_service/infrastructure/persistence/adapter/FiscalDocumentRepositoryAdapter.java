package com.qespe.fiscal_service.infrastructure.persistence.adapter;

import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;
import com.qespe.fiscal_service.infrastructure.persistence.repository.FiscalDocumentJpaRepository;
import com.qespe.fiscal_service.core.port.out.FiscalDocumentRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FiscalDocumentRepositoryAdapter implements FiscalDocumentRepositoryPort {

    private final FiscalDocumentJpaRepository repository;

    @Override
    public FiscalDocumentEntity save(FiscalDocumentEntity entity) {
        return repository.save(entity);
    }

    @Override
    public Optional<FiscalDocumentEntity> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public Optional<FiscalDocumentEntity> findWithLinesById(UUID id) {
        return repository.findWithLinesById(id);
    }

    @Override
    public Optional<FiscalDocumentEntity> findByIdempotency(UUID companyId, String sourceService, String idempotencyKey) {
        return repository.findByCompanyIdAndSourceServiceAndIdempotencyKey(companyId, sourceService, idempotencyKey);
    }

    @Override
    public Page<FiscalDocumentEntity> search(Specification<FiscalDocumentEntity> specification, Pageable pageable) {
        return repository.findAll(specification, pageable);
    }
}

