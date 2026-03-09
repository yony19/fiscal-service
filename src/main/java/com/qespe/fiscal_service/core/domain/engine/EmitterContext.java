package com.qespe.fiscal_service.core.domain.engine;

import java.util.UUID;

public record EmitterContext(
        UUID emitterConfigId,
        String documentType,
        String documentNumber,
        String legalName,
        String tradeName,
        String fiscalAddress
) {
}
