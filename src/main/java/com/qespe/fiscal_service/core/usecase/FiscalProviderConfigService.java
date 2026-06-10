package com.qespe.fiscal_service.core.usecase;

import com.qespe.fiscal_service.core.dto.provider.FiscalProviderConfigRequest;
import com.qespe.fiscal_service.core.dto.provider.FiscalProviderConfigResponse;
import com.qespe.fiscal_service.infrastructure.mapper.FiscalProviderConfigMapper;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalProviderConfigEntity;
import com.qespe.fiscal_service.core.port.in.FiscalProviderConfigUseCase;
import com.qespe.fiscal_service.core.port.out.FiscalProviderConfigRepositoryPort;
import com.qespe.fiscal_service.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FiscalProviderConfigService implements FiscalProviderConfigUseCase {

    // Endpoints CPE de SUNAT (kit de homologacion). El de beta acepta el
    // usuario estandar MODDATOS; el de produccion exige el SOL real.
    private static final String SUNAT_BETA_URL =
            "https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService";
    private static final String SUNAT_PROD_URL =
            "https://e-factura.sunat.gob.pe/ol-ti-itcpfegem/billService";

    private final FiscalProviderConfigRepositoryPort repository;
    private final FiscalProviderConfigMapper mapper;

    /**
     * Ambiente con el que se auto-provisiona el proveedor SUNAT de una empresa
     * nueva (TEST para homologar; PROD cuando el despliegue ya esta certificado).
     */
    @Value("${fiscal.provider.default-environment:TEST}")
    private String defaultEnvironment;

    @Override
    @Transactional
    public FiscalProviderConfigResponse create(FiscalProviderConfigRequest request) {
        FiscalProviderConfigEntity entity = mapper.toEntity(request);
        return mapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public FiscalProviderConfigResponse update(UUID id, FiscalProviderConfigRequest request) {
        FiscalProviderConfigEntity entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Fiscal provider config not found: " + id));
        mapper.updateEntity(request, entity);
        return mapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public FiscalProviderConfigResponse getById(UUID id) {
        return mapper.toResponse(repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Fiscal provider config not found: " + id)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FiscalProviderConfigResponse> list(UUID companyId) {
        return repository.findByCompanyId(companyId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    /**
     * Auto-provision del proveedor fiscal al onboarding (cierra el "pendiente"
     * del kit de homologacion): una empresa nueva queda lista para emitir via
     * SUNAT directo apenas suba su certificado, sin tocar la configuracion a
     * mano. Idempotente: si la empresa ya tiene ALGUN proveedor configurado,
     * se respeta y se devuelve el existente (no pisamos un OSE elegido por el
     * operador). Las credenciales van por referencia de entorno — nunca se
     * persiste un secreto.
     */
    @Override
    @Transactional
    public FiscalProviderConfigResponse ensureDefaultSunatConfig(UUID companyId) {
        List<FiscalProviderConfigEntity> existing = repository.findByCompanyId(companyId);
        if (!existing.isEmpty()) {
            return mapper.toResponse(existing.get(0));
        }

        boolean isProd = "PROD".equalsIgnoreCase(defaultEnvironment);
        String submitUrl = isProd ? SUNAT_PROD_URL : SUNAT_BETA_URL;
        String credentialRef = isProd ? "env:SUNAT_PROD_CREDENTIALS" : "env:SUNAT_BETA_CREDENTIALS";

        FiscalProviderConfigRequest request = new FiscalProviderConfigRequest(
                companyId,
                "PE",
                "SUNAT",
                "SUNAT",
                isProd ? "PROD" : "TEST",
                true,
                0,
                submitUrl,
                submitUrl,
                null,
                "WSSE",
                credentialRef,
                30_000,
                3,
                30_000,
                null
        );
        FiscalProviderConfigResponse created = create(request);
        log.info("Proveedor fiscal SUNAT auto-provisionado para company {} (env {})", companyId, defaultEnvironment);
        return created;
    }
}

