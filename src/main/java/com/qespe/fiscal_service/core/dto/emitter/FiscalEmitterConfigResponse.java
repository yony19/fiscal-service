package com.qespe.fiscal_service.core.dto.emitter;

import java.time.Instant;
import java.util.UUID;

public record FiscalEmitterConfigResponse(
        UUID id,
        UUID companyId,
        String countryCode,
        String taxAuthorityCode,
        String environment,
        String providerCode,
        String documentType,
        String documentNumber,
        String legalName,
        String tradeName,
        String fiscalAddress,
        String ubigeo,
        String district,
        String city,
        String state,
        String countryName,
        String postalCode,
        String email,
        String phone,
        String status,
        Boolean isDefault,
        Instant createdAt,
        Instant updatedAt
) {
}
