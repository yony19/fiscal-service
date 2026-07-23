package com.qespe.fiscal_service.infrastructure.rest;

import com.qespe.fiscal_service.core.dto.certificate.CompanyCertificateRequest;
import com.qespe.fiscal_service.core.dto.certificate.CompanyCertificateResponse;
import com.qespe.fiscal_service.core.port.in.CertificateUploadUseCase;
import com.qespe.fiscal_service.core.port.in.CompanyCertificateUseCase;
import com.qespe.fiscal_service.core.port.in.GenerateTestCertificateUseCase;
import com.qespe.fiscal_service.core.security.annotation.RequirePermission;
import com.qespe.fiscal_service.infrastructure.security.util.SecurityUtils;
import com.qespe.fiscal_service.shared.exception.BadRequestException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/fiscal/certificates")
@RequiredArgsConstructor
public class CompanyCertificateController {

    private final CompanyCertificateUseCase useCase;
    private final CertificateUploadUseCase uploadUseCase;
    private final GenerateTestCertificateUseCase generateTestUseCase;
    private final SecurityUtils security;

    @RequirePermission("fiscal.fiscal.certificates:read")
    @GetMapping
    public Page<CompanyCertificateResponse> list(
            @RequestParam UUID companyId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return useCase.list(enforceCompany(companyId), pageable);
    }

    @RequirePermission("fiscal.fiscal.certificates:read")
    @GetMapping("/{id}")
    public CompanyCertificateResponse getById(@PathVariable UUID id) {
        CompanyCertificateResponse c = useCase.getById(id);
        if (!security.isSuperadmin() && !Objects.equals(c.companyId(), security.getCurrentCompanyId())) {
            throw new java.util.NoSuchElementException("Certificado no encontrado.");
        }
        return c;
    }

    @RequirePermission("fiscal.fiscal.certificates:create")
    @PostMapping
    public CompanyCertificateResponse create(@Valid @RequestBody CompanyCertificateRequest request) {
        CompanyCertificateRequest scoped = withCompany(request, enforceCompany(request.companyId()));
        validateCertificateRules(scoped);
        return useCase.create(scoped);
    }

    @RequirePermission("fiscal.fiscal.certificates:update")
    @PutMapping("/{id}")
    public CompanyCertificateResponse update(@PathVariable UUID id, @Valid @RequestBody CompanyCertificateRequest request) {
        CompanyCertificateResponse existing = useCase.getById(id);
        if (!security.isSuperadmin() && !Objects.equals(existing.companyId(), security.getCurrentCompanyId())) {
            throw new java.util.NoSuchElementException("Certificado no encontrado.");
        }
        CompanyCertificateRequest scoped = withCompany(request, enforceCompany(request.companyId()));
        validateCertificateRules(scoped);
        return useCase.update(id, scoped);
    }

    /**
     * Friendly upload endpoint. The user picks a .pfx file and types the
     * password — that's it. The backend validates the password by loading
     * the PKCS12, extracts validFrom/validTo/alias/fingerprint from the
     * cert, encrypts the password with AES-GCM, and stores everything inline.
     *
     * <p>Cross-tenant guard: the certificate must belong to the caller's
     * company (or the caller is superadmin).
     *
     * <p>The .pfx is sent as {@code multipart/form-data} with parts:
     * <ul>
     *   <li>{@code file}: the .pfx binary (max 10 MB).</li>
     *   <li>{@code password}: the password string.</li>
     * </ul>
     */
    @RequirePermission("fiscal.fiscal.certificates:update")
    @PostMapping(value = "/{id}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompanyCertificateResponse uploadPfx(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("password") String password
    ) {
        // Tenant guard before doing any expensive PKCS12 work.
        CompanyCertificateResponse existing = useCase.getById(id);
        if (!security.isSuperadmin() && !Objects.equals(existing.companyId(), security.getCurrentCompanyId())) {
            throw new java.util.NoSuchElementException("Certificado no encontrado.");
        }
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Debes seleccionar un archivo .pfx.");
        }
        if (password == null || password.isBlank()) {
            throw new BadRequestException("La contraseña del certificado es requerida.");
        }
        String filename = file.getOriginalFilename();
        if (filename != null && !filename.toLowerCase().endsWith(".pfx") && !filename.toLowerCase().endsWith(".p12")) {
            throw new BadRequestException("El archivo debe ser .pfx o .p12.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("No se pudo leer el archivo subido.");
        }
        return uploadUseCase.uploadPfx(id, bytes, password, filename);
    }

    /**
     * Genera, enteramente en el servidor, un certificado autofirmado listo
     * para usar en el entorno TEST de SUNAT — sin que el usuario necesite
     * herramientas externas ni OpenSSL. El servicio rechaza la operación
     * (400) si el Proveedor vinculado al certificado no está en TEST,
     * independiente de que el botón esté oculto en la UI para Proveedores
     * PROD (ver openspec/changes/certificate-test-generation).
     */
    @RequirePermission("fiscal.fiscal.certificates:update")
    @PostMapping("/{id}/generate-test")
    public CompanyCertificateResponse generateTest(@PathVariable UUID id) {
        CompanyCertificateResponse existing = useCase.getById(id);
        if (!security.isSuperadmin() && !Objects.equals(existing.companyId(), security.getCurrentCompanyId())) {
            throw new java.util.NoSuchElementException("Certificado no encontrado.");
        }
        return generateTestUseCase.generateTest(id);
    }

    private UUID enforceCompany(UUID requested) {
        return security.isSuperadmin() ? requested : security.getCurrentCompanyId();
    }

    /**
     * Server-side certificate rules. Friendly model (V11+):
     *
     * <ul>
     *   <li>The user creates a metadata row (alias + provider) and uploads
     *       the .pfx separately. So {@code storageMode}, paths and secretRef
     *       are ALL optional during create/update.</li>
     *   <li>If the user provides paths or a secretRef explicitly, we still
     *       enforce the XOR rule (legacy admin path).</li>
     *   <li>{@code validTo > validFrom} is enforced when both are present.</li>
     * </ul>
     *
     * Once the .pfx is uploaded, {@link CertificateUploadService} sets
     * {@code storageMode = INLINE_ENCRYPTED} and populates dates+fingerprint
     * from the cert itself.
     */
    private void validateCertificateRules(CompanyCertificateRequest req) {
        if (req.validTo() != null && req.validFrom() != null
                && !req.validTo().isAfter(req.validFrom())) {
            throw new BadRequestException("La fecha 'vigente hasta' debe ser posterior a 'vigente desde'.");
        }
        boolean hasSecretRef = req.secretRef() != null && !req.secretRef().isBlank();
        boolean hasPathPair = (req.certificatePath() != null && !req.certificatePath().isBlank())
                || (req.privateKeyPath() != null && !req.privateKeyPath().isBlank());
        // Only enforce the XOR rule if the admin explicitly used the legacy
        // paths/secretRef path. The friendly upload flow sets neither.
        if (hasSecretRef && hasPathPair) {
            throw new BadRequestException(
                    "No mezcles rutas de archivo con referencias a secretos. Usa solo una fuente.");
        }
    }

    private static CompanyCertificateRequest withCompany(CompanyCertificateRequest r, UUID companyId) {
        return new CompanyCertificateRequest(
                companyId, r.providerCode(), r.providerId(), r.alias(), r.storageMode(),
                r.certificatePath(), r.privateKeyPath(), r.secretRef(), r.passwordSecretRef(),
                r.fingerprintSha256(), r.validFrom(), r.validTo(), r.status(), r.isDefault()
        );
    }
}
