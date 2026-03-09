package com.qespe.fiscal_service.core.usecase;

import com.qespe.fiscal_service.core.domain.enums.FiscalEmitterStatus;
import com.qespe.fiscal_service.core.domain.enums.FiscalEnvironment;
import com.qespe.fiscal_service.core.dto.emitter.FiscalEmitterConfigRequest;
import com.qespe.fiscal_service.core.dto.emitter.FiscalEmitterConfigResponse;
import com.qespe.fiscal_service.core.port.in.FiscalEmitterConfigUseCase;
import com.qespe.fiscal_service.core.port.out.FiscalEmitterConfigRepositoryPort;
import com.qespe.fiscal_service.infrastructure.mapper.FiscalEmitterConfigMapper;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalEmitterConfigEntity;
import com.qespe.fiscal_service.shared.exception.BusinessException;
import com.qespe.fiscal_service.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FiscalEmitterConfigService implements FiscalEmitterConfigUseCase {

    private static final String DEFAULT_COUNTRY = "PE";
    private static final String DEFAULT_AUTHORITY = "SUNAT";

    private final FiscalEmitterConfigRepositoryPort repository;
    private final FiscalEmitterConfigMapper mapper;

    @Override
    @Transactional
    public FiscalEmitterConfigResponse create(FiscalEmitterConfigRequest request) {
        validateRequest(request);
        FiscalEmitterConfigEntity entity = mapper.toEntity(request);
        applyNormalizedValues(entity, request);
        enforceDefaultRules(entity, null);
        return mapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public FiscalEmitterConfigResponse update(UUID id, FiscalEmitterConfigRequest request) {
        validateRequest(request);
        FiscalEmitterConfigEntity entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Fiscal emitter config not found: " + id));
        mapper.updateEntity(request, entity);
        applyNormalizedValues(entity, request);
        enforceDefaultRules(entity, id);
        return mapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public FiscalEmitterConfigResponse getById(UUID id) {
        return mapper.toResponse(repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Fiscal emitter config not found: " + id)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FiscalEmitterConfigResponse> list(UUID companyId, String environment, String status, String countryCode, String taxAuthorityCode, String providerCode) {
        return repository.search(
                        companyId,
                        normalizeFilter(environment),
                        parseStatus(status, true),
                        normalizeFilter(countryCode),
                        normalizeFilter(taxAuthorityCode),
                        normalizeFilter(providerCode)
                ).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FiscalEmitterConfigResponse getDefault(UUID companyId, String environment, String countryCode, String taxAuthorityCode) {
        if (companyId == null) {
            throw new BusinessException("companyId is required");
        }
        if (isBlank(environment)) {
            throw new BusinessException("environment is required");
        }

        String finalCountry = isBlank(countryCode) ? DEFAULT_COUNTRY : countryCode.trim().toUpperCase();
        String finalAuthority = isBlank(taxAuthorityCode) ? DEFAULT_AUTHORITY : taxAuthorityCode.trim().toUpperCase();

        return repository.findDefault(companyId, environment.trim().toUpperCase(), finalCountry, finalAuthority)
                .map(mapper::toResponse)
                .orElseThrow(() -> new NotFoundException("Default ACTIVE emitter config not found for company/environment/country/authority"));
    }

    private void enforceDefaultRules(FiscalEmitterConfigEntity entity, UUID currentId) {
        if (Boolean.TRUE.equals(entity.getIsDefault())) {
            if (entity.getStatus() != FiscalEmitterStatus.ACTIVE) {
                throw new BusinessException("Default emitter config must be ACTIVE");
            }
            repository.clearDefaultForScope(
                    entity.getCompanyId(),
                    entity.getCountryCode(),
                    entity.getTaxAuthorityCode(),
                    entity.getEnvironment().name(),
                    currentId
            );
        }
    }

    private void validateRequest(FiscalEmitterConfigRequest request) {
        if (request.companyId() == null) throw new BusinessException("companyId is required");
        if (isBlank(request.countryCode())) throw new BusinessException("countryCode is required");
        if (isBlank(request.taxAuthorityCode())) throw new BusinessException("taxAuthorityCode is required");
        if (isBlank(request.environment())) throw new BusinessException("environment is required");
        if (isBlank(request.documentType())) throw new BusinessException("documentType is required");
        if (isBlank(request.documentNumber())) throw new BusinessException("documentNumber is required");
        if (isBlank(request.legalName())) throw new BusinessException("legalName is required");
        if (isBlank(request.fiscalAddress())) throw new BusinessException("fiscalAddress is required");
        if (isBlank(request.status())) throw new BusinessException("status is required");
        if (request.isDefault() == null) throw new BusinessException("isDefault is required");

        parseEnvironment(request.environment());
        parseStatus(request.status(), false);
    }

    private void applyNormalizedValues(FiscalEmitterConfigEntity entity, FiscalEmitterConfigRequest request) {
        entity.setCountryCode(request.countryCode().trim().toUpperCase());
        entity.setTaxAuthorityCode(request.taxAuthorityCode().trim().toUpperCase());
        entity.setEnvironment(parseEnvironment(request.environment()));
        entity.setStatus(parseStatus(request.status(), false));

        entity.setDocumentType(request.documentType().trim());
        entity.setDocumentNumber(request.documentNumber().trim());
        entity.setLegalName(request.legalName().trim());
        entity.setFiscalAddress(request.fiscalAddress().trim());

        entity.setProviderCode(trimToNull(request.providerCode()));
        entity.setTradeName(trimToNull(request.tradeName()));
        entity.setUbigeo(trimToNull(request.ubigeo()));
        entity.setDistrict(trimToNull(request.district()));
        entity.setCity(trimToNull(request.city()));
        entity.setState(trimToNull(request.state()));
        entity.setCountryName(trimToNull(request.countryName()));
        entity.setPostalCode(trimToNull(request.postalCode()));
        entity.setEmail(trimToNull(request.email()));
        entity.setPhone(trimToNull(request.phone()));
        entity.setIsDefault(request.isDefault());
    }

    private FiscalEnvironment parseEnvironment(String value) {
        try {
            return FiscalEnvironment.valueOf(value.trim().toUpperCase());
        } catch (Exception ex) {
            throw new BusinessException("Invalid environment: " + value);
        }
    }

    private FiscalEmitterStatus parseStatus(String value, boolean nullable) {
        if (nullable && isBlank(value)) return null;
        try {
            return FiscalEmitterStatus.valueOf(value.trim().toUpperCase());
        } catch (Exception ex) {
            throw new BusinessException("Invalid status: " + value);
        }
    }

    private String normalizeFilter(String value) {
        return isBlank(value) ? null : value.trim().toUpperCase();
    }

    private String trimToNull(String value) {
        if (isBlank(value)) return null;
        return value.trim();
    }

    private boolean isBlank(String value) {
        return Objects.isNull(value) || value.isBlank();
    }
}
