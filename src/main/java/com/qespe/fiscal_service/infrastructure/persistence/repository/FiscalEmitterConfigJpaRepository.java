package com.qespe.fiscal_service.infrastructure.persistence.repository;

import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalEmitterConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface FiscalEmitterConfigJpaRepository extends JpaRepository<FiscalEmitterConfigEntity, UUID>, JpaSpecificationExecutor<FiscalEmitterConfigEntity> {

    Optional<FiscalEmitterConfigEntity> findFirstByCompanyIdAndEnvironmentAndCountryCodeAndTaxAuthorityCodeAndStatusAndIsDefaultTrue(
            UUID companyId,
            com.qespe.fiscal_service.core.domain.enums.FiscalEnvironment environment,
            String countryCode,
            String taxAuthorityCode,
            com.qespe.fiscal_service.core.domain.enums.FiscalEmitterStatus status
    );

    @Modifying
    @Query("""
            update FiscalEmitterConfigEntity e
            set e.isDefault = false
            where e.companyId = :companyId
              and e.countryCode = :countryCode
              and e.taxAuthorityCode = :taxAuthorityCode
              and e.environment = :environment
              and e.isDefault = true
              and (:excludeId is null or e.id <> :excludeId)
            """)
    int clearDefaultForScope(
            @Param("companyId") UUID companyId,
            @Param("countryCode") String countryCode,
            @Param("taxAuthorityCode") String taxAuthorityCode,
            @Param("environment") com.qespe.fiscal_service.core.domain.enums.FiscalEnvironment environment,
            @Param("excludeId") UUID excludeId
    );
}
