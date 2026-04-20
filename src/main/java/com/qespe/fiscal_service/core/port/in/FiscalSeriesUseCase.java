package com.qespe.fiscal_service.core.port.in;

import com.qespe.fiscal_service.core.dto.series.FiscalSeriesRequest;
import com.qespe.fiscal_service.core.dto.series.FiscalSeriesResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface FiscalSeriesUseCase {
    FiscalSeriesResponse create(FiscalSeriesRequest request);
    FiscalSeriesResponse update(UUID id, FiscalSeriesRequest request);
    FiscalSeriesResponse getById(UUID id);
    Page<FiscalSeriesResponse> list(UUID companyId, Pageable pageable);
}
