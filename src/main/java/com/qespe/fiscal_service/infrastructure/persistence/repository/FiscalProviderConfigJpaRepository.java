package com.qespe.fiscal_service.infrastructure.persistence.repository;

import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalProviderConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FiscalProviderConfigJpaRepository extends JpaRepository<FiscalProviderConfigEntity, UUID> {
    List<FiscalProviderConfigEntity> findByCompanyId(UUID companyId);

    List<FiscalProviderConfigEntity> findByCompanyIdAndCountryCodeAndTaxAuthorityCodeAndEnvironmentAndActiveTrue(
            UUID companyId,
            String countryCode,
            String taxAuthorityCode,
            String environment
    );
}
