package com.qespe.fiscal_service.core.port.out;

import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FiscalDocumentRepositoryPort {
    FiscalDocumentEntity save(FiscalDocumentEntity entity);
    Optional<FiscalDocumentEntity> findById(UUID id);
    Optional<FiscalDocumentEntity> findWithLinesById(UUID id);

    /** Boletas emitidas en la fecha dada (para el Resumen Diario RC). */
    List<FiscalDocumentEntity> findBoletasByIssueDate(UUID companyId, LocalDate issueDate);

    /** Cuantos documentos de un tipo se generaron en la fecha (correlativo RC/RA). */
    long countByDocumentTypeAndIssueDate(UUID companyId, String documentType, LocalDate issueDate);
    Optional<FiscalDocumentEntity> findByIdempotency(UUID companyId, String sourceService, String idempotencyKey);
    Page<FiscalDocumentEntity> search(Specification<FiscalDocumentEntity> specification, Pageable pageable);
}

