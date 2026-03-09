package com.qespe.fiscal_service.core.port.in;

import com.qespe.fiscal_service.core.dto.emitter.FiscalEmitterConfigRequest;
import com.qespe.fiscal_service.core.dto.emitter.FiscalEmitterConfigResponse;

import java.util.List;
import java.util.UUID;

public interface FiscalEmitterConfigUseCase {

    FiscalEmitterConfigResponse create(FiscalEmitterConfigRequest request);

    FiscalEmitterConfigResponse update(UUID id, FiscalEmitterConfigRequest request);

    FiscalEmitterConfigResponse getById(UUID id);

    List<FiscalEmitterConfigResponse> list(
            UUID companyId,
            String environment,
            String status,
            String countryCode,
            String taxAuthorityCode,
            String providerCode
    );

    FiscalEmitterConfigResponse getDefault(
            UUID companyId,
            String environment,
            String countryCode,
            String taxAuthorityCode
    );
}
