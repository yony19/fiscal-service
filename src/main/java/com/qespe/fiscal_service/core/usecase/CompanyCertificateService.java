package com.qespe.fiscal_service.core.usecase;

import com.qespe.fiscal_service.core.dto.certificate.CompanyCertificateRequest;
import com.qespe.fiscal_service.core.dto.certificate.CompanyCertificateResponse;
import com.qespe.fiscal_service.infrastructure.mapper.CompanyCertificateMapper;
import com.qespe.fiscal_service.infrastructure.persistence.entity.CompanyCertificateEntity;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalProviderConfigEntity;
import com.qespe.fiscal_service.core.port.in.CompanyCertificateUseCase;
import com.qespe.fiscal_service.core.port.out.CompanyCertificateRepositoryPort;
import com.qespe.fiscal_service.core.port.out.FiscalProviderConfigRepositoryPort;
import com.qespe.fiscal_service.shared.exception.BadRequestException;
import com.qespe.fiscal_service.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanyCertificateService implements CompanyCertificateUseCase {

    private final CompanyCertificateRepositoryPort repository;
    private final CompanyCertificateMapper mapper;
    private final FiscalProviderConfigRepositoryPort providerRepository;

    @Override
    @Transactional
    public CompanyCertificateResponse create(CompanyCertificateRequest request) {
        validateProviderBelongsToCompany(request.providerId(), request.companyId());
        CompanyCertificateEntity entity = mapper.toEntity(request);
        CompanyCertificateEntity saved = repository.save(entity);
        enforceSingleDefault(saved);
        return toResponseWithEnvironment(saved);
    }

    @Override
    @Transactional
    public CompanyCertificateResponse update(UUID id, CompanyCertificateRequest request) {
        CompanyCertificateEntity entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Company certificate not found: " + id));
        validateProviderBelongsToCompany(request.providerId(), entity.getCompanyId());
        mapper.updateEntity(request, entity);
        CompanyCertificateEntity saved = repository.save(entity);
        enforceSingleDefault(saved);
        return toResponseWithEnvironment(saved);
    }

    /**
     * Un providerId, si se manda, DEBE referenciar un Proveedor real de la
     * MISMA empresa — sin esto un certificado podria quedar "vinculado" a un
     * Proveedor de otra compania, rompiendo el aislamiento multi-tenant.
     */
    private void validateProviderBelongsToCompany(UUID providerId, UUID companyId) {
        if (providerId == null) {
            return;
        }
        FiscalProviderConfigEntity provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new BadRequestException("El proveedor indicado no existe: " + providerId));
        if (!provider.getCompanyId().equals(companyId)) {
            throw new BadRequestException("El proveedor indicado no pertenece a esta empresa");
        }
    }

    /** Resuelve providerEnvironment desde el Proveedor vinculado; null si no hay vínculo. */
    private CompanyCertificateResponse toResponseWithEnvironment(CompanyCertificateEntity entity) {
        CompanyCertificateResponse base = mapper.toResponse(entity);
        if (entity.getProviderId() == null) {
            return base;
        }
        String environment = providerRepository.findById(entity.getProviderId())
                .map(FiscalProviderConfigEntity::getEnvironment)
                .orElse(null);
        return new CompanyCertificateResponse(
                base.id(), base.companyId(), base.providerCode(), base.providerId(), environment,
                base.alias(), base.uploadFilename(), base.uploadedAt(), base.hasFile(),
                base.validFrom(), base.validTo(), base.status(), base.isDefault(),
                base.createdAt(), base.updatedAt(), base.storageMode(), base.certificatePath(),
                base.privateKeyPath(), base.secretRef(), base.passwordSecretRef(), base.fingerprintSha256()
        );
    }

    /**
     * Un unico certificado por defecto por empresa: si el que se acaba de guardar
     * quedo como default, desmarca los demas de la misma compania. Antes se podian
     * tener varios "por defecto", dejando ambiguo cual se usaba al firmar.
     */
    private void enforceSingleDefault(CompanyCertificateEntity saved) {
        if (!Boolean.TRUE.equals(saved.getIsDefault())) {
            return;
        }
        for (CompanyCertificateEntity other : repository.findByCompanyId(saved.getCompanyId())) {
            if (!other.getId().equals(saved.getId()) && Boolean.TRUE.equals(other.getIsDefault())) {
                other.setIsDefault(false);
                repository.save(other);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyCertificateResponse getById(UUID id) {
        return toResponseWithEnvironment(repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Company certificate not found: " + id)));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CompanyCertificateResponse> list(UUID companyId, Pageable pageable) {
        return repository.findByCompanyId(companyId, pageable).map(this::toResponseWithEnvironment);
    }
}
