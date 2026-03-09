package com.qespe.fiscal_service.core.port.in;

import com.qespe.fiscal_service.core.dto.common.PageResponse;
import com.qespe.fiscal_service.core.dto.document.FiscalDocumentReserveRequest;
import com.qespe.fiscal_service.core.dto.document.FiscalDocumentReserveResponse;
import com.qespe.fiscal_service.core.dto.document.FiscalDocumentResponse;
import com.qespe.fiscal_service.core.dto.event.FiscalEventResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FiscalDocumentUseCase {
    FiscalDocumentReserveResponse reserve(FiscalDocumentReserveRequest request);

    FiscalDocumentResponse getById(UUID id);

    PageResponse<FiscalDocumentResponse> search(
            UUID companyId,
            String documentType,
            String status,
            String environment,
            String series,
            LocalDate issueDateFrom,
            LocalDate issueDateTo,
            String sourceService,
            String sourceId,
            int page,
            int size
    );

    List<FiscalEventResponse> listEvents(UUID fiscalDocumentId);
}

