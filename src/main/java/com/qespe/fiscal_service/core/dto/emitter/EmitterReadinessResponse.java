package com.qespe.fiscal_service.core.dto.emitter;

import java.util.List;
import java.util.UUID;

/**
 * Friendly readiness check for an emitter. Cross-references provider config
 * + active certificate + emitter status, returning a single boolean
 * {@link #ready()} plus a human-readable list of {@link #missing()} steps.
 *
 * <p>The UI shows a green check + "Listo para emitir" when ready, or a red
 * banner with the missing items as bullet points + deeplinks to fix each.
 */
public record EmitterReadinessResponse(
        UUID emitterId,
        boolean ready,
        boolean emitterActive,
        // Provider info — null when none matches scope.
        String effectiveProviderCode,
        Integer providerPriority,
        boolean providerAmbiguous,
        // Certificate info — null when none matches.
        UUID effectiveCertificateId,
        String effectiveCertificateAlias,
        java.time.Instant effectiveCertificateValidTo,
        Long certificateDaysUntilExpiry,
        boolean certificateAmbiguous,
        /** Friendly Spanish messages: "No hay proveedor activo", etc. */
        List<String> missing,
        /** Friendly Spanish warnings (non-blocking): "El certificado vence en 5 días", etc. */
        List<String> warnings
) {
}
