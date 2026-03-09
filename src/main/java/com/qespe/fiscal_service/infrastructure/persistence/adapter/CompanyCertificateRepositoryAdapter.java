package com.qespe.fiscal_service.infrastructure.persistence.adapter;

import com.qespe.fiscal_service.infrastructure.persistence.entity.CompanyCertificateEntity;
import com.qespe.fiscal_service.infrastructure.persistence.repository.CompanyCertificateJpaRepository;
import com.qespe.fiscal_service.core.port.out.CompanyCertificateRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CompanyCertificateRepositoryAdapter implements CompanyCertificateRepositoryPort {

    private final CompanyCertificateJpaRepository repository;

    @Override
    public CompanyCertificateEntity save(CompanyCertificateEntity entity) {
        return repository.save(entity);
    }

    @Override
    public Optional<CompanyCertificateEntity> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public List<CompanyCertificateEntity> findByCompanyId(UUID companyId) {
        return repository.findByCompanyId(companyId);
    }
}

