package com.qespe.fiscal_service.core.port.in;

import com.qespe.fiscal_service.core.dto.provider.FiscalProviderConfigRequest;
import com.qespe.fiscal_service.core.dto.provider.FiscalProviderConfigResponse;

import java.util.List;
import java.util.UUID;

public interface FiscalProviderConfigUseCase {
    FiscalProviderConfigResponse create(FiscalProviderConfigRequest request);
    FiscalProviderConfigResponse update(UUID id, FiscalProviderConfigRequest request);
    FiscalProviderConfigResponse getById(UUID id);
    List<FiscalProviderConfigResponse> list(UUID companyId);

    /** Auto-provision idempotente del proveedor SUNAT por defecto (onboarding). */
    FiscalProviderConfigResponse ensureDefaultSunatConfig(UUID companyId);
}

