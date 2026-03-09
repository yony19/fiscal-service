package com.qespe.fiscal_service.infrastructure.rest;

import com.qespe.fiscal_service.core.dto.common.PageResponse;
import com.qespe.fiscal_service.core.dto.document.FiscalDocumentReserveRequest;
import com.qespe.fiscal_service.core.dto.document.FiscalDocumentReserveResponse;
import com.qespe.fiscal_service.core.dto.document.FiscalDocumentResponse;
import com.qespe.fiscal_service.core.dto.event.FiscalEventResponse;
import com.qespe.fiscal_service.core.port.in.FiscalDocumentUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/fiscal/documents")
@RequiredArgsConstructor
public class FiscalDocumentController {

    private final FiscalDocumentUseCase useCase;

    @PostMapping("/reserve")
    public FiscalDocumentReserveResponse reserve(@Valid @RequestBody FiscalDocumentReserveRequest request) {
        return useCase.reserve(request);
    }

    @GetMapping("/{id}")
    public FiscalDocumentResponse getById(@PathVariable UUID id) {
        return useCase.getById(id);
    }

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
        return useCase.search(
                companyId,
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

    @GetMapping("/{id}/events")
    public List<FiscalEventResponse> listEvents(@PathVariable UUID id) {
        return useCase.listEvents(id);
    }
}

