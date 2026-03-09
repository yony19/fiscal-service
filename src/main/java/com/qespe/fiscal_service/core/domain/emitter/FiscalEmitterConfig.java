package com.qespe.fiscal_service.core.domain.emitter;

import com.qespe.fiscal_service.core.domain.enums.FiscalEmitterStatus;
import com.qespe.fiscal_service.core.domain.enums.FiscalEnvironment;

import java.util.UUID;

public record FiscalEmitterConfig(
        UUID id,
        UUID companyId,
        String countryCode,
        String taxAuthorityCode,
        FiscalEnvironment environment,
        String providerCode,
        String documentType,
        String documentNumber,
        String legalName,
        String tradeName,
        String fiscalAddress,
        FiscalEmitterStatus status,
        Boolean isDefault
) {
}
