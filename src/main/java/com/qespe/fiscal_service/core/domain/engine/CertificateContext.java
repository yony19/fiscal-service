package com.qespe.fiscal_service.core.domain.engine;

import java.time.Instant;
import java.util.UUID;

public record CertificateContext(
        UUID certificateId,
        String providerCode,
        String alias,
        String storageMode,
        String secretRef,
        String passwordSecretRef,
        Instant validFrom,
        Instant validTo
) {
}
