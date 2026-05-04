package com.qespe.fiscal_service.infrastructure.persistence.repository;

import com.qespe.fiscal_service.core.domain.enums.FiscalDocumentStatus;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FiscalDocumentJpaRepository extends JpaRepository<FiscalDocumentEntity, UUID>, JpaSpecificationExecutor<FiscalDocumentEntity> {

    Optional<FiscalDocumentEntity> findByCompanyIdAndSourceServiceAndIdempotencyKey(UUID companyId, String sourceService, String idempotencyKey);

    @EntityGraph(attributePaths = {"lines"})
    Optional<FiscalDocumentEntity> findWithLinesById(UUID id);

    @Override
    @EntityGraph(attributePaths = {"lines"})
    Page<FiscalDocumentEntity> findAll(Specification<FiscalDocumentEntity> spec, Pageable pageable);

    /**
     * Documents in {@code status} that are flagged retryable and whose
     * {@code nextRetryAt} is due. Used by FiscalDocumentRetryScheduler.
     * Limit applied via Pageable to bound per-tick work.
     */
    @Query("""
        SELECT d.id FROM FiscalDocumentEntity d
        WHERE d.status = :status
          AND d.retryableError = true
          AND d.nextRetryAt IS NOT NULL
          AND d.nextRetryAt <= :now
        ORDER BY d.nextRetryAt ASC
    """)
    List<UUID> findRetryableIds(@Param("status") FiscalDocumentStatus status,
                                @Param("now") Instant now,
                                Pageable pageable);
}

