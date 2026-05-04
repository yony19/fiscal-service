package com.qespe.fiscal_service.infrastructure.rest;

import com.qespe.fiscal_service.core.dto.emitter.EmitterReadinessResponse;
import com.qespe.fiscal_service.core.dto.emitter.FiscalEmitterConfigRequest;
import com.qespe.fiscal_service.core.dto.emitter.FiscalEmitterConfigResponse;
import com.qespe.fiscal_service.core.port.in.EmitterReadinessUseCase;
import com.qespe.fiscal_service.core.port.in.FiscalEmitterConfigUseCase;
import com.qespe.fiscal_service.core.security.annotation.RequirePermission;
import com.qespe.fiscal_service.infrastructure.security.util.SecurityUtils;
import com.qespe.fiscal_service.shared.exception.BadRequestException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/fiscal/emitters")
@RequiredArgsConstructor
public class FiscalEmitterConfigController {

    private static final Pattern RUC_PATTERN = Pattern.compile("\\d{11}");
    private static final Pattern DNI_PATTERN = Pattern.compile("\\d{8}");

    private final FiscalEmitterConfigUseCase useCase;
    private final EmitterReadinessUseCase readinessUseCase;
    private final SecurityUtils security;

    @RequirePermission("fiscal.fiscal.emitters:read")
    @GetMapping
    public List<FiscalEmitterConfigResponse> list(
            @RequestParam(required = false) UUID companyId,
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String taxAuthorityCode,
            @RequestParam(required = false) String providerCode
    ) {
        return useCase.list(enforceCompany(companyId), environment, status, countryCode, taxAuthorityCode, providerCode);
    }

    @RequirePermission("fiscal.fiscal.emitters:read")
    @GetMapping("/{id}")
    public FiscalEmitterConfigResponse getById(@PathVariable UUID id) {
        FiscalEmitterConfigResponse e = useCase.getById(id);
        // Multi-tenant guard.
        if (!security.isSuperadmin() && !Objects.equals(e.companyId(), security.getCurrentCompanyId())) {
            throw new java.util.NoSuchElementException("Emisor no encontrado.");
        }
        return e;
    }

    @RequirePermission("fiscal.fiscal.emitters:read")
    @GetMapping("/default")
    public FiscalEmitterConfigResponse getDefault(
            @RequestParam UUID companyId,
            @RequestParam String environment,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String taxAuthorityCode
    ) {
        return useCase.getDefault(enforceCompany(companyId), environment, countryCode, taxAuthorityCode);
    }

    @RequirePermission("fiscal.fiscal.emitters:create")
    @PostMapping
    public FiscalEmitterConfigResponse create(@Valid @RequestBody FiscalEmitterConfigRequest request) {
        FiscalEmitterConfigRequest scoped = withCompany(request, enforceCompany(request.companyId()));
        validateSunatRules(scoped);
        return useCase.create(scoped);
    }

    @RequirePermission("fiscal.fiscal.emitters:update")
    @PutMapping("/{id}")
    public FiscalEmitterConfigResponse update(@PathVariable UUID id, @Valid @RequestBody FiscalEmitterConfigRequest request) {
        // Verify the existing record belongs to the caller's tenant.
        FiscalEmitterConfigResponse existing = useCase.getById(id);
        if (!security.isSuperadmin() && !Objects.equals(existing.companyId(), security.getCurrentCompanyId())) {
            throw new java.util.NoSuchElementException("Emisor no encontrado.");
        }
        FiscalEmitterConfigRequest scoped = withCompany(request, enforceCompany(request.companyId()));
        validateSunatRules(scoped);
        return useCase.update(id, scoped);
    }

    /**
     * Friendly readiness panel: a single call returns whether the emitter
     * can issue documents right now and, if not, what's missing. The UI
     * uses this to render a green "Listo" badge or a red banner with
     * deeplinks to fix each missing piece.
     */
    @RequirePermission("fiscal.fiscal.emitters:read")
    @GetMapping("/{id}/readiness")
    public EmitterReadinessResponse readiness(@PathVariable UUID id) {
        FiscalEmitterConfigResponse e = useCase.getById(id);
        if (!security.isSuperadmin() && !Objects.equals(e.companyId(), security.getCurrentCompanyId())) {
            throw new java.util.NoSuchElementException("Emisor no encontrado.");
        }
        return readinessUseCase.computeFor(id);
    }

    /** Multi-tenant: regular users always operate against their JWT companyId. */
    private UUID enforceCompany(UUID requested) {
        return security.isSuperadmin() ? requested : security.getCurrentCompanyId();
    }

    /**
     * Server-side SUNAT validation (matches the Angular legacy fiscal-emitters
     * dynamic rules). The frontend ALSO validates, but never trust the client
     * for fiscal compliance: a malformed RUC creates emitters that will be
     * rejected by SUNAT later, polluting the issuance pipeline.
     */
    private void validateSunatRules(FiscalEmitterConfigRequest req) {
        boolean isPeruSunat = "PE".equalsIgnoreCase(req.countryCode())
                && "SUNAT".equalsIgnoreCase(req.taxAuthorityCode());
        if (isPeruSunat) {
            if (!"6".equals(req.documentType())) {
                throw new BadRequestException(
                        "Para Perú/SUNAT el tipo de documento debe ser 6 (RUC). Recibido: " + req.documentType());
            }
            if (!RUC_PATTERN.matcher(req.documentNumber()).matches()) {
                throw new BadRequestException(
                        "El RUC debe tener exactamente 11 dígitos. Recibido: " + req.documentNumber());
            }
        } else if ("1".equals(req.documentType()) && !DNI_PATTERN.matcher(req.documentNumber()).matches()) {
            throw new BadRequestException(
                    "El DNI debe tener exactamente 8 dígitos. Recibido: " + req.documentNumber());
        }
        // Cross-field: a default emitter must be ACTIVE.
        if (Boolean.TRUE.equals(req.isDefault()) && !"ACTIVE".equalsIgnoreCase(req.status())) {
            throw new BadRequestException(
                    "Un emisor marcado como default debe tener estado ACTIVE.");
        }
    }

    /** Returns a copy of the record with companyId overridden. */
    private static FiscalEmitterConfigRequest withCompany(FiscalEmitterConfigRequest r, UUID companyId) {
        return new FiscalEmitterConfigRequest(
                companyId, r.countryCode(), r.taxAuthorityCode(), r.environment(),
                r.providerCode(), r.documentType(), r.documentNumber(), r.legalName(),
                r.tradeName(), r.fiscalAddress(), r.ubigeo(), r.district(), r.city(),
                r.state(), r.countryName(), r.postalCode(), r.email(), r.phone(),
                r.status(), r.isDefault()
        );
    }
}
