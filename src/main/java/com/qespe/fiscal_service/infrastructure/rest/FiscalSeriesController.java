package com.qespe.fiscal_service.infrastructure.rest;

import com.qespe.fiscal_service.core.dto.series.FiscalSeriesRequest;
import com.qespe.fiscal_service.core.dto.series.FiscalSeriesResponse;
import com.qespe.fiscal_service.core.port.in.FiscalSeriesUseCase;
import com.qespe.fiscal_service.core.security.annotation.RequirePermission;
import com.qespe.fiscal_service.infrastructure.security.util.SecurityUtils;
import com.qespe.fiscal_service.shared.exception.BadRequestException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/fiscal/series")
@RequiredArgsConstructor
public class FiscalSeriesController {

    private final FiscalSeriesUseCase useCase;
    private final SecurityUtils security;

    @RequirePermission("fiscal.fiscal.series:read")
    @GetMapping
    public Page<FiscalSeriesResponse> list(
            @RequestParam UUID companyId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return useCase.list(enforceCompany(companyId), pageable);
    }

    @RequirePermission("fiscal.fiscal.series:read")
    @GetMapping("/{id}")
    public FiscalSeriesResponse getById(@PathVariable UUID id) {
        FiscalSeriesResponse s = useCase.getById(id);
        if (!security.isSuperadmin() && !Objects.equals(s.companyId(), security.getCurrentCompanyId())) {
            throw new java.util.NoSuchElementException("Serie no encontrada.");
        }
        return s;
    }

    @RequirePermission("fiscal.fiscal.series:create")
    @PostMapping
    public FiscalSeriesResponse create(@Valid @RequestBody FiscalSeriesRequest request) {
        FiscalSeriesRequest scoped = withCompany(request, enforceCompany(request.companyId()));
        validateSeriesRules(scoped);
        return useCase.create(scoped);
    }

    @RequirePermission("fiscal.fiscal.series:update")
    @PutMapping("/{id}")
    public FiscalSeriesResponse update(@PathVariable UUID id, @Valid @RequestBody FiscalSeriesRequest request) {
        FiscalSeriesResponse existing = useCase.getById(id);
        if (!security.isSuperadmin() && !Objects.equals(existing.companyId(), security.getCurrentCompanyId())) {
            throw new java.util.NoSuchElementException("Serie no encontrada.");
        }
        FiscalSeriesRequest scoped = withCompany(request, enforceCompany(request.companyId()));
        validateSeriesRules(scoped);
        return useCase.update(id, scoped);
    }

    private UUID enforceCompany(UUID requested) {
        return security.isSuperadmin() ? requested : security.getCurrentCompanyId();
    }

    private void validateSeriesRules(FiscalSeriesRequest req) {
        if (req.validFrom() != null && req.validTo() != null
                && !req.validTo().isAfter(req.validFrom())) {
            throw new BadRequestException("validTo debe ser posterior a validFrom.");
        }
    }

    private static FiscalSeriesRequest withCompany(FiscalSeriesRequest r, UUID companyId) {
        return new FiscalSeriesRequest(
                companyId, r.countryCode(), r.taxAuthorityCode(), r.documentTypeCode(),
                r.series(), r.environment(), r.nextNumber(), r.active(),
                r.validFrom(), r.validTo()
        );
    }
}
