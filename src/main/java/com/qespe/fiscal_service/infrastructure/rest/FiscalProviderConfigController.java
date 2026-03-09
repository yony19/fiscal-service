package com.qespe.fiscal_service.infrastructure.rest;

import com.qespe.fiscal_service.core.dto.provider.FiscalProviderConfigRequest;
import com.qespe.fiscal_service.core.dto.provider.FiscalProviderConfigResponse;
import com.qespe.fiscal_service.core.port.in.FiscalProviderConfigUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/fiscal/providers")
@RequiredArgsConstructor
public class FiscalProviderConfigController {

    private final FiscalProviderConfigUseCase useCase;

    @GetMapping
    public List<FiscalProviderConfigResponse> list(@RequestParam UUID companyId) {
        return useCase.list(companyId);
    }

    @GetMapping("/{id}")
    public FiscalProviderConfigResponse getById(@PathVariable UUID id) {
        return useCase.getById(id);
    }

    @PostMapping
    public FiscalProviderConfigResponse create(@Valid @RequestBody FiscalProviderConfigRequest request) {
        return useCase.create(request);
    }

    @PutMapping("/{id}")
    public FiscalProviderConfigResponse update(@PathVariable UUID id, @Valid @RequestBody FiscalProviderConfigRequest request) {
        return useCase.update(id, request);
    }
}

