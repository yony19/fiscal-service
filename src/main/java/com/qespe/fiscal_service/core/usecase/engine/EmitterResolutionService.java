package com.qespe.fiscal_service.core.usecase.engine;

import com.qespe.fiscal_service.core.domain.engine.EmitterContext;
import com.qespe.fiscal_service.core.domain.enums.FiscalEmitterStatus;
import com.qespe.fiscal_service.core.port.out.FiscalEmitterConfigRepositoryPort;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalEmitterConfigEntity;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;
import com.qespe.fiscal_service.shared.exception.BusinessException;
import com.qespe.fiscal_service.shared.exception.EmitterValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class EmitterResolutionService {

    private final FiscalEmitterConfigRepositoryPort emitterRepository;

    public EmitterContext resolve(FiscalDocumentEntity document) {
        FiscalEmitterConfigEntity emitter = findDefault(
                document.getCompanyId(),
                document.getEnvironment().name(),
                document.getCountryCode(),
                document.getTaxAuthorityCode()
        );
        validatePeruSunatEmitter(document.getCountryCode(), document.getTaxAuthorityCode(), emitter);
        return toContext(emitter);
    }

    public EmitterContext resolve(UUID companyId, String environment, String countryCode, String taxAuthorityCode) {
        FiscalEmitterConfigEntity emitter = findDefault(companyId, environment, countryCode, taxAuthorityCode);
        validatePeruSunatEmitter(countryCode, taxAuthorityCode, emitter);
        return toContext(emitter);
    }

    private FiscalEmitterConfigEntity findDefault(UUID companyId, String environment, String countryCode, String taxAuthorityCode) {
        FiscalEmitterConfigEntity emitter = emitterRepository.findDefault(
                        companyId,
                        environment,
                        countryCode,
                        taxAuthorityCode
                )
                .orElseThrow(() -> new BusinessException("Active default emitter config not found for document scope"));
        if (emitter.getStatus() != FiscalEmitterStatus.ACTIVE) {
            throw new BusinessException("Default emitter config is not ACTIVE");
        }
        return emitter;
    }

    private EmitterContext toContext(FiscalEmitterConfigEntity emitter) {
        return new EmitterContext(
                emitter.getId(),
                emitter.getDocumentType(),
                emitter.getDocumentNumber(),
                emitter.getLegalName(),
                emitter.getTradeName(),
                emitter.getFiscalAddress()
        );
    }

    private void validatePeruSunatEmitter(String countryCode, String taxAuthorityCode, FiscalEmitterConfigEntity emitter) {
        boolean peruSunat = "PE".equalsIgnoreCase(trim(countryCode))
                && "SUNAT".equalsIgnoreCase(trim(taxAuthorityCode));

        if (!peruSunat) {
            return;
        }

        String documentType = trim(emitter.getDocumentType());
        String documentNumber = trim(emitter.getDocumentNumber());

        if (!"6".equals(documentType)) {
            throw new EmitterValidationException("Peru/SUNAT emitter must use RUC document type (6)");
        }
        if (documentNumber == null || !documentNumber.matches("\\d{11}")) {
            throw new EmitterValidationException("Peru/SUNAT emitter RUC must be 11 digits");
        }
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
