package com.qespe.fiscal_service.core.domain.engine;

import java.util.Map;
import java.util.UUID;

public record ProviderContext(
        UUID providerConfigId,
        String providerCode,
        String environment,
        String endpointSubmitUrl,
        String endpointStatusUrl,
        String endpointCdrUrl,
        String authType,
        String credentialRef,
        Integer timeoutMs,
        Integer maxRetries,
        Integer retryBackoffMs,
        Map<String, Object> config
) {
}
