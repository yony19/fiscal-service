package com.qespe.fiscal_service.core.usecase.engine;

import com.qespe.fiscal_service.core.domain.engine.EmitterContext;
import com.qespe.fiscal_service.core.domain.enums.FiscalEmitterStatus;
import com.qespe.fiscal_service.core.port.out.FiscalEmitterConfigRepositoryPort;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalEmitterConfigEntity;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;
import com.qespe.fiscal_service.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmitterResolutionService {

    private final FiscalEmitterConfigRepositoryPort emitterRepository;

    public EmitterContext resolve(FiscalDocumentEntity document) {
        FiscalEmitterConfigEntity emitter = emitterRepository.findDefault(
                        document.getCompanyId(),
                        document.getEnvironment().name(),
                        document.getCountryCode(),
                        document.getTaxAuthorityCode()
                )
                .orElseThrow(() -> new BusinessException("Active default emitter config not found for document scope"));

        if (emitter.getStatus() != FiscalEmitterStatus.ACTIVE) {
            throw new BusinessException("Default emitter config is not ACTIVE");
        }

        return new EmitterContext(
                emitter.getId(),
                emitter.getDocumentType(),
                emitter.getDocumentNumber(),
                emitter.getLegalName(),
                emitter.getTradeName(),
                emitter.getFiscalAddress()
        );
    }
}
