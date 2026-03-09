package com.qespe.fiscal_service.infrastructure.rest;

import com.qespe.fiscal_service.core.dto.certificate.CompanyCertificateRequest;
import com.qespe.fiscal_service.core.dto.certificate.CompanyCertificateResponse;
import com.qespe.fiscal_service.core.port.in.CompanyCertificateUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/fiscal/certificates")
@RequiredArgsConstructor
public class CompanyCertificateController {

    private final CompanyCertificateUseCase useCase;

    @GetMapping
    public List<CompanyCertificateResponse> list(@RequestParam UUID companyId) {
        return useCase.list(companyId);
    }

    @GetMapping("/{id}")
    public CompanyCertificateResponse getById(@PathVariable UUID id) {
        return useCase.getById(id);
    }

    @PostMapping
    public CompanyCertificateResponse create(@Valid @RequestBody CompanyCertificateRequest request) {
        return useCase.create(request);
    }

    @PutMapping("/{id}")
    public CompanyCertificateResponse update(@PathVariable UUID id, @Valid @RequestBody CompanyCertificateRequest request) {
        return useCase.update(id, request);
    }
}

