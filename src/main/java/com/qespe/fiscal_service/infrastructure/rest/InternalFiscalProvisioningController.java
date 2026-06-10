package com.qespe.fiscal_service.infrastructure.rest;

import com.qespe.fiscal_service.core.dto.provider.FiscalProviderConfigResponse;
import com.qespe.fiscal_service.core.port.in.FiscalProviderConfigUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Endpoints internos servicio-a-servicio (mismo patron que los
 * {@code /internal} de product-service). NO se exponen por el gateway ni
 * llevan {@code @RequirePermission}: los consume auth-service durante el
 * onboarding, donde no hay JWT de usuario final.
 */
@RestController
@RequestMapping("/internal/fiscal/providers")
@RequiredArgsConstructor
public class InternalFiscalProvisioningController {

    private final FiscalProviderConfigUseCase useCase;

    /**
     * Auto-provisiona el proveedor SUNAT por defecto para una empresa nueva.
     * Idempotente: si ya hay proveedor configurado, devuelve el existente.
     */
    @PostMapping("/ensure-default")
    public FiscalProviderConfigResponse ensureDefault(@RequestParam UUID companyId) {
        return useCase.ensureDefaultSunatConfig(companyId);
    }
}
