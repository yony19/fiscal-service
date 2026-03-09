package com.qespe.fiscal_service.core.port.out;

import com.qespe.fiscal_service.infrastructure.persistence.entity.CompanyCertificateEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyCertificateRepositoryPort {
    CompanyCertificateEntity save(CompanyCertificateEntity entity);
    Optional<CompanyCertificateEntity> findById(UUID id);
    List<CompanyCertificateEntity> findByCompanyId(UUID companyId);
}

