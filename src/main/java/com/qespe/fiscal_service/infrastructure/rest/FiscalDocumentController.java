package com.qespe.fiscal_service.infrastructure.rest;

import com.qespe.fiscal_service.core.dto.common.PageResponse;
import com.qespe.fiscal_service.core.dto.document.FiscalDocumentReserveRequest;
import com.qespe.fiscal_service.core.dto.document.FiscalDocumentReserveResponse;
import com.qespe.fiscal_service.core.dto.document.FiscalDocumentResponse;
import com.qespe.fiscal_service.core.dto.engine.FiscalDocumentProcessResponse;
import com.qespe.fiscal_service.core.dto.event.FiscalEventResponse;
import com.qespe.fiscal_service.core.port.in.FiscalDocumentProcessingUseCase;
import com.qespe.fiscal_service.core.port.in.FiscalDocumentUseCase;
import com.qespe.fiscal_service.core.port.out.FiscalArtifactStoragePort;
import com.qespe.fiscal_service.core.security.annotation.RequirePermission;
import com.qespe.fiscal_service.infrastructure.security.util.SecurityUtils;
import com.qespe.fiscal_service.shared.exception.AccessDeniedException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/fiscal/documents")
@RequiredArgsConstructor
public class FiscalDocumentController {

    private final FiscalDocumentUseCase useCase;
    private final FiscalDocumentProcessingUseCase processingUseCase;
    private final FiscalArtifactStoragePort artifactStorage;
    private final SecurityUtils security;
    private final com.qespe.fiscal_service.core.usecase.engine.DailySummaryService dailySummaryService;
    private final com.qespe.fiscal_service.core.usecase.engine.VoidDocumentService voidDocumentService;

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

    /**
     * Crea el Resumen Diario de boletas (RC) de una fecha y dispara su
     * procesamiento (XML -> firma -> sendSummary -> ticket). Idempotente por
     * compania+fecha: repetir la llamada devuelve el RC ya creado. Si el
     * procesamiento falla (p. ej. sin certificado), el RC queda RESERVED y se
     * puede reprocesar con POST /{id}/process.
     */
    @RequirePermission("fiscal.fiscal.documents:process")
    @PostMapping("/daily-summary")
    public FiscalDocumentResponse createDailySummary(
            @RequestParam UUID companyId,
            @RequestParam("date") @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate date) {
        if (!security.isSuperadmin() && !Objects.equals(companyId, security.getCurrentCompanyId())) {
            throw new AccessDeniedException("El companyId no coincide con el de la sesión.");
        }
        var rc = dailySummaryService.createDailySummary(companyId, date);
        try {
            processingUseCase.process(rc.getId());
        } catch (Exception ex) {
            // El RC ya existe (RESERVED); el operador puede reprocesarlo. No
            // perdemos la creacion por un fallo de pipeline (cert, red, etc.).
        }
        return useCase.getById(rc.getId());
    }

    /**
     * Anula un comprobante ACEPTADO: crea la Comunicacion de Baja (RA) que lo
     * referencia y dispara el envio async a SUNAT. Cuando SUNAT acepta la RA,
     * el comprobante original pasa a VOIDED automaticamente. Idempotente por
     * comprobante. Devuelve la RA creada (o la existente).
     */
    @RequirePermission("fiscal.fiscal.documents:process")
    @PostMapping("/{id}/void")
    public FiscalDocumentResponse voidDocument(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason) {
        assertDocumentTenant(id);
        var ra = voidDocumentService.createVoid(id, reason);
        try {
            processingUseCase.process(ra.getId());
        } catch (Exception ex) {
            // La RA ya existe (RESERVED); puede reprocesarse con /{id}/process.
        }
        return useCase.getById(ra.getId());
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

    /**
     * Descarga del CDR (Constancia de Recepcion de SUNAT) ya almacenado.
     * Requisito de respaldo legal: el contador necesita el CDR del comprobante
     * aceptado. El CDR se persiste durante el procesamiento; aqui solo se sirve
     * el ZIP guardado en {@code document.cdrPath}.
     */
    @RequirePermission("fiscal.fiscal.documents:read")
    @GetMapping("/{id}/cdr")
    public ResponseEntity<byte[]> downloadCdr(@PathVariable UUID id) {
        FiscalDocumentResponse d = useCase.getById(id);
        if (!security.isSuperadmin()
                && !Objects.equals(d.companyId(), security.getCurrentCompanyId())) {
            throw new NoSuchElementException("Comprobante no encontrado.");
        }
        if (d.cdrPath() == null || d.cdrPath().isBlank()) {
            throw new NoSuchElementException("Este comprobante aun no tiene CDR disponible.");
        }
        byte[] cdr = artifactStorage.readArtifact(d.cdrPath());
        String filename = "cdr-" + id + ".zip";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(cdr);
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
