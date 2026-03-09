package com.qespe.fiscal_service.core.domain.engine;

import java.util.UUID;

public record ProviderContext(
        UUID providerConfigId,
        String providerCode,
        String environment,
        String endpointSubmitUrl,
        Integer timeoutMs,
        Integer maxRetries,
        Integer retryBackoffMs
) {
}
