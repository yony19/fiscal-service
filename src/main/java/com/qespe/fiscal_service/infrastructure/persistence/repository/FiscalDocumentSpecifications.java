package com.qespe.fiscal_service.infrastructure.persistence.repository;

import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.UUID;

public final class FiscalDocumentSpecifications {

    private FiscalDocumentSpecifications() {
    }

    public static Specification<FiscalDocumentEntity> byFilters(
            UUID companyId,
            String documentType,
            String status,
            String environment,
            String series,
            LocalDate issueDateFrom,
            LocalDate issueDateTo,
            String sourceService,
            String sourceId
    ) {
        return Specification.where(eqCompany(companyId))
                .and(eq("documentType", documentType))
                .and(eq("status", status))
                .and(eq("environment", environment))
                .and(eq("series", series))
                .and(eq("sourceService", sourceService))
                .and(eq("sourceId", sourceId))
                .and(gteIssueDate(issueDateFrom))
                .and(lteIssueDate(issueDateTo));
    }

    private static Specification<FiscalDocumentEntity> eqCompany(UUID companyId) {
        return (root, query, cb) -> companyId == null ? cb.conjunction() : cb.equal(root.get("companyId"), companyId);
    }

    private static Specification<FiscalDocumentEntity> eq(String field, String value) {
        return (root, query, cb) -> value == null || value.isBlank() ? cb.conjunction() : cb.equal(root.get(field), value);
    }

    private static Specification<FiscalDocumentEntity> gteIssueDate(LocalDate from) {
        return (root, query, cb) -> from == null ? cb.conjunction() : cb.greaterThanOrEqualTo(root.get("issueDate"), from);
    }

    private static Specification<FiscalDocumentEntity> lteIssueDate(LocalDate to) {
        return (root, query, cb) -> to == null ? cb.conjunction() : cb.lessThanOrEqualTo(root.get("issueDate"), to);
    }
}

