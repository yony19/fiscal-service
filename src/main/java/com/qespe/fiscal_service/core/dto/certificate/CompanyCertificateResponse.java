package com.qespe.fiscal_service.core.dto.certificate;

import java.time.Instant;
import java.util.UUID;

public record CompanyCertificateResponse(
        UUID id,
        UUID companyId,
        String providerCode,
        String alias,
        String storageMode,
        String certificatePath,
        String privateKeyPath,
        String secretRef,
        String passwordSecretRef,
        String fingerprintSha256,
        Instant validFrom,
        Instant validTo,
        String status,
        Boolean isDefault,
        Instant createdAt,
        Instant updatedAt
) {
}

