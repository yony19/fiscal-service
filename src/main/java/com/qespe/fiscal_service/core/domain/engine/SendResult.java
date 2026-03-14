package com.qespe.fiscal_service.core.domain.engine;

import com.qespe.fiscal_service.core.domain.enums.FiscalDocumentStatus;

public record SendResult(
        FiscalDocumentStatus documentStatus,
        String authorityStatusCode,
        String authorityStatusMessage,
        String authorityTicket,
        String cdrPath,
        boolean retryableError
) {
    public boolean accepted() {
        return documentStatus == FiscalDocumentStatus.ACCEPTED;
    }

    public boolean rejected() {
        return documentStatus == FiscalDocumentStatus.REJECTED;
    }

    public boolean failed() {
        return documentStatus == FiscalDocumentStatus.ERROR;
    }
}
