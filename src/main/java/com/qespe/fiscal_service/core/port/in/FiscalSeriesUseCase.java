package com.qespe.fiscal_service.core.port.in;

import com.qespe.fiscal_service.core.dto.common.PageResponse;
import com.qespe.fiscal_service.core.dto.series.FiscalSeriesRequest;
import com.qespe.fiscal_service.core.dto.series.FiscalSeriesResponse;

import java.util.List;
import java.util.UUID;

public interface FiscalSeriesUseCase {
    FiscalSeriesResponse create(FiscalSeriesRequest request);
    FiscalSeriesResponse update(UUID id, FiscalSeriesRequest request);
    FiscalSeriesResponse getById(UUID id);
    List<FiscalSeriesResponse> list(UUID companyId);
}

