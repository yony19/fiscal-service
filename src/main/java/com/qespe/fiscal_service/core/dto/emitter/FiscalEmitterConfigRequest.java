package com.qespe.fiscal_service.core.dto.emitter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record FiscalEmitterConfigRequest(
        @NotNull UUID companyId,
        @NotBlank String countryCode,
        @NotBlank String taxAuthorityCode,
        @NotBlank String environment,
        String providerCode,
        @NotBlank String documentType,
        @NotBlank String documentNumber,
        @NotBlank String legalName,
        String tradeName,
        @NotBlank String fiscalAddress,
        String ubigeo,
        String district,
        String city,
        String state,
        String countryName,
        String postalCode,
        String email,
        String phone,
        @NotBlank String status,
        @NotNull Boolean isDefault
) {
}
