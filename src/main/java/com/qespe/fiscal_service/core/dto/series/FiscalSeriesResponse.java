package com.qespe.fiscal_service.core.dto.series;

import com.qespe.fiscal_service.core.domain.enums.FiscalEnvironment;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FiscalSeriesResponse(
        UUID id,
        UUID companyId,
        String countryCode,
        String taxAuthorityCode,
        String documentTypeCode,
        String series,
        Long nextNumber,
        FiscalEnvironment environment,
        Boolean active,
        LocalDate validFrom,
        LocalDate validTo,
        Instant createdAt,
        Instant updatedAt
) {
}

