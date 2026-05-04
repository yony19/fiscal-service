package com.qespe.fiscal_service.core.dto.certificate;

import java.time.Instant;
import java.util.UUID;

/**
 * Certificate read model.
 *
 * <p>The "friendly" fields ({@code uploadFilename}, {@code uploadedAt},
 * {@code hasFile}) are what the user sees. The "system" fields
 * ({@code storageMode}, {@code certificatePath}, {@code secretRef},
 * {@code passwordSecretRef}, {@code fingerprintSha256}) are kept for
 * backwards compatibility but should NOT be displayed in the UI — they're
 * implementation details.
 */
public record CompanyCertificateResponse(
        UUID id,
        UUID companyId,
        String providerCode,
        String alias,
        // ── User-facing ────────────────────────────────────────────
        /** Original filename of the uploaded .pfx, e.g. "cert-2024.pfx". */
        String uploadFilename,
        /** When the .pfx was uploaded. Null = never uploaded yet. */
        Instant uploadedAt,
        /** True if a .pfx is attached. The UI uses this to show "Subir certificado" vs "Reemplazar". */
        boolean hasFile,
        Instant validFrom,
        Instant validTo,
        String status,
        Boolean isDefault,
        Instant createdAt,
        Instant updatedAt,
        // ── System / legacy ────────────────────────────────────────
        String storageMode,
        String certificatePath,
        String privateKeyPath,
        String secretRef,
        String passwordSecretRef,
        String fingerprintSha256
) {
}

