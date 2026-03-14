package com.qespe.fiscal_service.core.dto.engine;

import java.time.Instant;
import java.util.UUID;

public record FiscalDocumentProcessResponse(
        UUID fiscalDocumentId,
        String status,
        String authorityStatusCode,
        String authorityStatusMessage,
        String xmlPath,
        String signedXmlPath,
        String cdrPath,
        Integer sendAttemptCount,
        Boolean retryableError,
        String lastFailedStage,
        Integer retryCount,
        Instant nextRetryAt,
        Instant processedAt
) {
}
