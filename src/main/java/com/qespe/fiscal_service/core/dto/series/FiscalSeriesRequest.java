package com.qespe.fiscal_service.core.dto.series;

import com.qespe.fiscal_service.core.domain.enums.FiscalEnvironment;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record FiscalSeriesRequest(
        @NotNull UUID companyId,
        @NotBlank String countryCode,
        @NotBlank String taxAuthorityCode,
        @NotBlank String documentTypeCode,
        @NotBlank String series,
        @NotNull FiscalEnvironment environment,
        @NotNull @Min(1) Long nextNumber,
        @NotNull Boolean active,
        LocalDate validFrom,
        LocalDate validTo
) {
}

