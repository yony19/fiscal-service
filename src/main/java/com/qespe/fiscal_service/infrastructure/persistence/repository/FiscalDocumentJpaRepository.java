package com.qespe.fiscal_service.infrastructure.persistence.repository;

import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface FiscalDocumentJpaRepository extends JpaRepository<FiscalDocumentEntity, UUID>, JpaSpecificationExecutor<FiscalDocumentEntity> {

    Optional<FiscalDocumentEntity> findByCompanyIdAndSourceServiceAndIdempotencyKey(UUID companyId, String sourceService, String idempotencyKey);

    @EntityGraph(attributePaths = {"lines"})
    Optional<FiscalDocumentEntity> findWithLinesById(UUID id);

    @Override
    @EntityGraph(attributePaths = {"lines"})
    Page<FiscalDocumentEntity> findAll(Specification<FiscalDocumentEntity> spec, Pageable pageable);
}

