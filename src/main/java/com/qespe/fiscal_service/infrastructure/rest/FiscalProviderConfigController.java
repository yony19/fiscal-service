package com.qespe.fiscal_service.infrastructure.rest;

import com.qespe.fiscal_service.core.dto.provider.FiscalProviderConfigRequest;
import com.qespe.fiscal_service.core.dto.provider.FiscalProviderConfigResponse;
import com.qespe.fiscal_service.core.port.in.FiscalProviderConfigUseCase;
import com.qespe.fiscal_service.core.security.annotation.RequirePermission;
import com.qespe.fiscal_service.infrastructure.security.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/fiscal/providers")
@RequiredArgsConstructor
public class FiscalProviderConfigController {

    private final FiscalProviderConfigUseCase useCase;
    private final SecurityUtils security;

    @RequirePermission("fiscal.fiscal.providers:read")
    @GetMapping
    public List<FiscalProviderConfigResponse> list(@RequestParam UUID companyId) {
        return useCase.list(enforceCompany(companyId));
    }

    @RequirePermission("fiscal.fiscal.providers:read")
    @GetMapping("/{id}")
    public FiscalProviderConfigResponse getById(@PathVariable UUID id) {
        FiscalProviderConfigResponse p = useCase.getById(id);
        if (!security.isSuperadmin() && !Objects.equals(p.companyId(), security.getCurrentCompanyId())) {
            throw new java.util.NoSuchElementException("Proveedor no encontrado.");
        }
        return p;
    }

    @RequirePermission("fiscal.fiscal.providers:create")
    @PostMapping
    public FiscalProviderConfigResponse create(@Valid @RequestBody FiscalProviderConfigRequest request) {
        return useCase.create(withCompany(request, enforceCompany(request.companyId())));
    }

    @RequirePermission("fiscal.fiscal.providers:update")
    @PutMapping("/{id}")
    public FiscalProviderConfigResponse update(@PathVariable UUID id, @Valid @RequestBody FiscalProviderConfigRequest request) {
        FiscalProviderConfigResponse existing = useCase.getById(id);
        if (!security.isSuperadmin() && !Objects.equals(existing.companyId(), security.getCurrentCompanyId())) {
            throw new java.util.NoSuchElementException("Proveedor no encontrado.");
        }
        return useCase.update(id, withCompany(request, enforceCompany(request.companyId())));
    }

    private UUID enforceCompany(UUID requested) {
        return security.isSuperadmin() ? requested : security.getCurrentCompanyId();
    }

    private static FiscalProviderConfigRequest withCompany(FiscalProviderConfigRequest r, UUID companyId) {
        return new FiscalProviderConfigRequest(
                companyId, r.countryCode(), r.taxAuthorityCode(), r.providerCode(),
                r.environment(), r.active(), r.priority(), r.endpointSubmitUrl(),
                r.endpointStatusUrl(), r.endpointCdrUrl(), r.authType(), r.credentialRef(),
                r.timeoutMs(), r.maxRetries(), r.retryBackoffMs(), r.configJson()
        );
    }
}

