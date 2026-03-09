package com.qespe.fiscal_service.core.dto.provider;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

public record FiscalProviderConfigRequest(
        @NotNull UUID companyId,
        @NotBlank String countryCode,
        @NotBlank String taxAuthorityCode,
        @NotBlank String providerCode,
        @NotBlank String environment,
        @NotNull Boolean active,
        @NotNull @Min(0) Integer priority,
        String endpointSubmitUrl,
        String endpointStatusUrl,
        String endpointCdrUrl,
        @NotBlank String authType,
        String credentialRef,
        @NotNull @Min(1) Integer timeoutMs,
        @NotNull @Min(0) Integer maxRetries,
        @NotNull @Min(0) Integer retryBackoffMs,
        Map<String, Object> configJson
) {
}

