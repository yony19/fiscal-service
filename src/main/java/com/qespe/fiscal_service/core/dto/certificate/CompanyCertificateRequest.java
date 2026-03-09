package com.qespe.fiscal_service.core.dto.certificate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CompanyCertificateRequest(
        @NotNull UUID companyId,
        @NotBlank String providerCode,
        @NotBlank String alias,
        @NotBlank String storageMode,
        String certificatePath,
        String privateKeyPath,
        String secretRef,
        String passwordSecretRef,
        String fingerprintSha256,
        @NotNull Instant validFrom,
        @NotNull Instant validTo,
        @NotBlank String status,
        @NotNull Boolean isDefault
) {
}

