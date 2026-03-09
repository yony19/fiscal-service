package com.qespe.fiscal_service.infrastructure.persistence.repository;

import com.qespe.fiscal_service.infrastructure.persistence.entity.CompanyCertificateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CompanyCertificateJpaRepository extends JpaRepository<CompanyCertificateEntity, UUID> {
    List<CompanyCertificateEntity> findByCompanyId(UUID companyId);
    List<CompanyCertificateEntity> findByCompanyIdAndStatus(UUID companyId, String status);
}
