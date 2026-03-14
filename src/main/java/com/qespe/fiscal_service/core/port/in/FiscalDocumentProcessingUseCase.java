package com.qespe.fiscal_service.core.port.in;

import com.qespe.fiscal_service.core.dto.engine.FiscalDocumentProcessResponse;

import java.util.UUID;

public interface FiscalDocumentProcessingUseCase {
    FiscalDocumentProcessResponse process(UUID fiscalDocumentId);
    FiscalDocumentProcessResponse retry(UUID fiscalDocumentId);
}
