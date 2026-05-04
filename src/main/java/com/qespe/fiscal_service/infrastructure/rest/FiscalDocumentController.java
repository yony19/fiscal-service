package com.qespe.fiscal_service.infrastructure.rest;

import com.qespe.fiscal_service.core.dto.common.PageResponse;
import com.qespe.fiscal_service.core.dto.document.FiscalDocumentReserveRequest;
import com.qespe.fiscal_service.core.dto.document.FiscalDocumentReserveResponse;
import com.qespe.fiscal_service.core.dto.document.FiscalDocumentResponse;
import com.qespe.fiscal_service.core.dto.engine.FiscalDocumentProcessResponse;
import com.qespe.fiscal_service.core.dto.event.FiscalEventResponse;
import com.qespe.fiscal_service.core.port.in.FiscalDocumentProcessingUseCase;
import com.qespe.fiscal_service.core.port.in.FiscalDocumentUseCase;
import com.qespe.fiscal_service.core.security.annotation.RequirePermission;
import com.qespe.fiscal_service.infrastructure.security.util.SecurityUtils;
import com.qespe.fiscal_service.shared.exception.AccessDeniedException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/fiscal/documents")
@RequiredArgsConstructor
public class FiscalDocumentController {

    private final FiscalDocumentUseCase useCase;
    private final FiscalDocumentProcessingUseCase processingUseCase;
    private final SecurityUtils security;

    @RequirePermission("fiscal.fiscal.documents:process")
    @PostMapping("/reserve")
    public FiscalDocumentReserveResponse reserve(@Valid @RequestBody FiscalDocumentReserveRequest request) {
        // Multi-tenant: the body's companyId MUST match JWT for non-superadmin.
        // Reserve is the entry point of fiscal emission and rebuilding the
        // record (40+ fields) is fragile, so we reject mismatches outright.
        if (!security.isSuperadmin()
                && !Objects.equals(request.companyId(), security.getCurrentCompanyId())) {
            throw new AccessDeniedException(
                    "El companyId del comprobante no coincide con el de la sesión.");
        }
        return useCase.reserve(request);
    }

    @RequirePermission("fiscal.fiscal.documents:process")
    @PostMapping("/{id}/process")
    public FiscalDocumentProcessResponse process(@PathVariable UUID id) {
        assertDocumentTenant(id);
        return processingUseCase.process(id);
    }

    @RequirePermission("fiscal.fiscal.documents:retry")
    @PostMapping("/{id}/retry")
    public FiscalDocumentProcessResponse retry(@PathVariable UUID id) {
        assertDocumentTenant(id);
        return processingUseCase.retry(id);
    }

    @RequirePermission("fiscal.fiscal.documents:query-status")
    @PostMapping("/{id}/status")
    public FiscalDocumentProcessResponse queryStatus(@PathVariable UUID id) {
        assertDocumentTenant(id);
        return processingUseCase.queryStatus(id);
    }

    @RequirePermission("fiscal.fiscal.documents:read")
    @GetMapping("/{id}")
    public FiscalDocumentResponse getById(@PathVariable UUID id) {
        FiscalDocumentResponse d = useCase.getById(id);
        if (!security.isSuperadmin() && !Objects.equals(d.companyId(), security.getCurrentCompanyId())) {
            throw new java.util.NoSuchElementException("Comprobante no encontrado.");
        }
        return d;
    }

    @RequirePermission("fiscal.fiscal.documents:read")
    @GetMapping
    public PageResponse<FiscalDocumentResponse> search(
            @RequestParam(required = false) UUID companyId,
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) String series,
            @RequestParam(required = false) LocalDate issueDateFrom,
            @RequestParam(required = false) LocalDate issueDateTo,
            @RequestParam(required = false) String sourceService,
            @RequestParam(required = false) String sourceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UUID effective = security.isSuperadmin() ? companyId : security.getCurrentCompanyId();
        return useCase.search(
                effective,
                documentType,
                status,
                environment,
                series,
                issueDateFrom,
                issueDateTo,
                sourceService,
                sourceId,
                page,
                size
        );
    }

    @RequirePermission("fiscal.fiscal.documents:read")
    @GetMapping("/{id}/events")
    public List<FiscalEventResponse> listEvents(@PathVariable UUID id) {
        assertDocumentTenant(id);
        return useCase.listEvents(id);
    }

    /** Multi-tenant guard for document-id-bound operations. */
    private void assertDocumentTenant(UUID documentId) {
        if (security.isSuperadmin()) return;
        FiscalDocumentResponse d = useCase.getById(documentId);
        if (!Objects.equals(d.companyId(), security.getCurrentCompanyId())) {
            throw new java.util.NoSuchElementException("Comprobante no encontrado.");
        }
    }
}
