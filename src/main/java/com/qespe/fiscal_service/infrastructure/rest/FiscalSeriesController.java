package com.qespe.fiscal_service.infrastructure.rest;

import com.qespe.fiscal_service.core.dto.series.FiscalSeriesRequest;
import com.qespe.fiscal_service.core.dto.series.FiscalSeriesResponse;
import com.qespe.fiscal_service.core.port.in.FiscalSeriesUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/fiscal/series")
@RequiredArgsConstructor
public class FiscalSeriesController {

    private final FiscalSeriesUseCase useCase;

    @GetMapping
    public Page<FiscalSeriesResponse> list(
            @RequestParam UUID companyId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return useCase.list(companyId, pageable);
    }

    @GetMapping("/{id}")
    public FiscalSeriesResponse getById(@PathVariable UUID id) {
        return useCase.getById(id);
    }

    @PostMapping
    public FiscalSeriesResponse create(@Valid @RequestBody FiscalSeriesRequest request) {
        return useCase.create(request);
    }

    @PutMapping("/{id}")
    public FiscalSeriesResponse update(@PathVariable UUID id, @Valid @RequestBody FiscalSeriesRequest request) {
        return useCase.update(id, request);
    }
}
