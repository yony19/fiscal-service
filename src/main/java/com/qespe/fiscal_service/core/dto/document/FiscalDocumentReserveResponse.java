package com.qespe.fiscal_service.core.dto.document;

import java.time.LocalDate;
import java.util.UUID;

public record FiscalDocumentReserveResponse(
        UUID fiscalDocumentId,
        String documentType,
        String documentTypeCode,
        String series,
        Long number,
        String fullNumber,
        String status,
        LocalDate issueDate
) {
}

