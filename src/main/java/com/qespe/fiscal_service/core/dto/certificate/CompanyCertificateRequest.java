package com.qespe.fiscal_service.core.dto.certificate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Friendly create/update payload (V11+). The user only fills:
 * {@code alias}, {@code providerCode}, {@code status}, {@code isDefault}.
 * Everything else (storageMode, paths, secretRef, dates, fingerprint) is
 * populated by the .pfx upload pipeline.
 */
public record CompanyCertificateRequest(
        @NotNull UUID companyId,
        @NotBlank String providerCode,
        @NotBlank String alias,
        // Optional: filled automatically as INLINE_ENCRYPTED on .pfx upload.
        // Only set explicitly when an admin uses the legacy paths/secret flow.
        String storageMode,
        String certificatePath,
        String privateKeyPath,
        String secretRef,
        String passwordSecretRef,
        String fingerprintSha256,
        // Optional: extracted from the .pfx automatically. Admin can override
        // for self-signed test certs that lack proper notBefore/notAfter.
        Instant validFrom,
        Instant validTo,
        @NotBlank String status,
        @NotNull Boolean isDefault
) {
}

