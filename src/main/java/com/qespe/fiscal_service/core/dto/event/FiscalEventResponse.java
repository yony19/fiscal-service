package com.qespe.fiscal_service.core.dto.event;

import java.time.Instant;

public record FiscalEventResponse(
        Long id,
        String eventType,
        String message,
        Object payload,
        Instant createdAt
) {
}

