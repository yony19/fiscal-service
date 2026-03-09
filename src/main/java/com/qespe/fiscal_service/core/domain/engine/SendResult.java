package com.qespe.fiscal_service.core.domain.engine;

public record SendResult(
        boolean accepted,
        String authorityStatusCode,
        String authorityStatusMessage,
        String authorityTicket,
        String cdrPath
) {
}
