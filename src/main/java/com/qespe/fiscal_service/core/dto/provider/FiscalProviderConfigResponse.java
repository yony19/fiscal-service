package com.qespe.fiscal_service.core.dto.provider;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record FiscalProviderConfigResponse(
        UUID id,
        UUID companyId,
        String countryCode,
        String taxAuthorityCode,
        String providerCode,
        String environment,
        Boolean active,
        Integer priority,
        String endpointSubmitUrl,
        String endpointStatusUrl,
        String endpointCdrUrl,
        String authType,
        String credentialRef,
        Integer timeoutMs,
        Integer maxRetries,
        Integer retryBackoffMs,
        Map<String, Object> configJson,
        Instant createdAt,
        Instant updatedAt
) {
}

