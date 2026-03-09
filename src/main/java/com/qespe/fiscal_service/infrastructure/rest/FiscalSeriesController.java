package com.qespe.fiscal_service.infrastructure.rest;

import com.qespe.fiscal_service.core.dto.series.FiscalSeriesRequest;
import com.qespe.fiscal_service.core.dto.series.FiscalSeriesResponse;
import com.qespe.fiscal_service.core.port.in.FiscalSeriesUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/fiscal/series")
@RequiredArgsConstructor
public class FiscalSeriesController {

    private final FiscalSeriesUseCase useCase;

    @GetMapping
    public List<FiscalSeriesResponse> list(@RequestParam UUID companyId) {
        return useCase.list(companyId);
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

