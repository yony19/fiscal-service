package com.qespe.fiscal_service.infrastructure.persistence.repository;

import com.qespe.fiscal_service.core.domain.enums.FiscalEmitterStatus;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalEmitterConfigEntity;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public final class FiscalEmitterConfigSpecifications {

    private FiscalEmitterConfigSpecifications() {
    }

    public static Specification<FiscalEmitterConfigEntity> byFilters(
            UUID companyId,
            String environment,
            FiscalEmitterStatus status,
            String countryCode,
            String taxAuthorityCode,
            String providerCode
    ) {
        return Specification.where(eqCompany(companyId))
                .and(eq("environment", environment))
                .and(eq("status", status))
                .and(eq("countryCode", countryCode))
                .and(eq("taxAuthorityCode", taxAuthorityCode))
                .and(eq("providerCode", providerCode));
    }

    private static Specification<FiscalEmitterConfigEntity> eqCompany(UUID companyId) {
        return (root, query, cb) -> companyId == null ? cb.conjunction() : cb.equal(root.get("companyId"), companyId);
    }

    private static Specification<FiscalEmitterConfigEntity> eq(String field, Object value) {
        return (root, query, cb) -> {
            if (value == null) return cb.conjunction();
            if (value instanceof String str && str.isBlank()) return cb.conjunction();
            return cb.equal(root.get(field), value);
        };
    }
}
