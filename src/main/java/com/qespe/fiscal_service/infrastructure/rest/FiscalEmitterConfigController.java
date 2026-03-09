package com.qespe.fiscal_service.infrastructure.rest;

import com.qespe.fiscal_service.core.dto.emitter.FiscalEmitterConfigRequest;
import com.qespe.fiscal_service.core.dto.emitter.FiscalEmitterConfigResponse;
import com.qespe.fiscal_service.core.port.in.FiscalEmitterConfigUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/fiscal/emitters")
@RequiredArgsConstructor
public class FiscalEmitterConfigController {

    private final FiscalEmitterConfigUseCase useCase;

    @GetMapping
    public List<FiscalEmitterConfigResponse> list(
            @RequestParam(required = false) UUID companyId,
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String taxAuthorityCode,
            @RequestParam(required = false) String providerCode
    ) {
        return useCase.list(companyId, environment, status, countryCode, taxAuthorityCode, providerCode);
    }

    @GetMapping("/{id}")
    public FiscalEmitterConfigResponse getById(@PathVariable UUID id) {
        return useCase.getById(id);
    }

    @GetMapping("/default")
    public FiscalEmitterConfigResponse getDefault(
            @RequestParam UUID companyId,
            @RequestParam String environment,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String taxAuthorityCode
    ) {
        return useCase.getDefault(companyId, environment, countryCode, taxAuthorityCode);
    }

    @PostMapping
    public FiscalEmitterConfigResponse create(@Valid @RequestBody FiscalEmitterConfigRequest request) {
        return useCase.create(request);
    }

    @PutMapping("/{id}")
    public FiscalEmitterConfigResponse update(@PathVariable UUID id, @Valid @RequestBody FiscalEmitterConfigRequest request) {
        return useCase.update(id, request);
    }
}
