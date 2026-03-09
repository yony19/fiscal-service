package com.qespe.fiscal_service.infrastructure.persistence.repository;

import com.qespe.fiscal_service.core.domain.enums.FiscalEnvironment;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalSeriesEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FiscalSeriesJpaRepository extends JpaRepository<FiscalSeriesEntity, UUID> {

    List<FiscalSeriesEntity> findByCompanyId(UUID companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select fs from FiscalSeriesEntity fs
            where fs.companyId = :companyId
              and fs.countryCode = :countryCode
              and fs.taxAuthorityCode = :taxAuthorityCode
              and fs.documentTypeCode = :documentTypeCode
              and fs.environment = :environment
              and fs.active = true
            """)
    List<FiscalSeriesEntity> findActiveForUpdate(
            @Param("companyId") UUID companyId,
            @Param("countryCode") String countryCode,
            @Param("taxAuthorityCode") String taxAuthorityCode,
            @Param("documentTypeCode") String documentTypeCode,
            @Param("environment") FiscalEnvironment environment
    );

    Optional<FiscalSeriesEntity> findByIdAndCompanyId(UUID id, UUID companyId);
}

