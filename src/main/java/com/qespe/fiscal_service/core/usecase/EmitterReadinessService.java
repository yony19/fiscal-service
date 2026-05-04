package com.qespe.fiscal_service.core.usecase;

import com.qespe.fiscal_service.core.dto.emitter.EmitterReadinessResponse;
import com.qespe.fiscal_service.core.port.in.EmitterReadinessUseCase;
import com.qespe.fiscal_service.core.port.out.CompanyCertificateRepositoryPort;
import com.qespe.fiscal_service.core.port.out.FiscalEmitterConfigRepositoryPort;
import com.qespe.fiscal_service.core.port.out.FiscalProviderConfigRepositoryPort;
import com.qespe.fiscal_service.infrastructure.persistence.entity.CompanyCertificateEntity;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalEmitterConfigEntity;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalProviderConfigEntity;
import com.qespe.fiscal_service.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Computes "is this emitter ready to emit?" by cross-referencing emitter
 * status, providers in the same scope, and active certificates assigned to
 * the chosen provider. Ported from the Angular legacy
 * (apps/fiscal/.../fiscal-emitters.ts:511-594) but moved to the backend so
 * the UI doesn't need three separate queries to compute it.
 *
 * <p>Heuristics (mirror Angular):
 * <ul>
 *   <li>Effective provider = lowest {@code priority} active provider matching
 *       the emitter's {@code companyId+countryCode+taxAuthority+environment}.
 *       Ties (same priority) flagged as ambiguous.</li>
 *   <li>Effective certificate = ACTIVE cert assigned to the effective
 *       provider, with {@code validTo > now}. Default cert wins ties.</li>
 *   <li>Warnings (non-blocking): cert expires in &lt;30 days.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class EmitterReadinessService implements EmitterReadinessUseCase {

    private static final long EXPIRY_WARN_DAYS = 30;

    private final FiscalEmitterConfigRepositoryPort emitterRepo;
    private final FiscalProviderConfigRepositoryPort providerRepo;
    private final CompanyCertificateRepositoryPort certRepo;

    @Override
    @Transactional(readOnly = true)
    public EmitterReadinessResponse computeFor(UUID emitterId) {
        FiscalEmitterConfigEntity emitter = emitterRepo.findById(emitterId)
                .orElseThrow(() -> new NotFoundException("Emisor no encontrado: " + emitterId));

        boolean emitterActive = "ACTIVE".equalsIgnoreCase(
                emitter.getStatus() == null ? "" : emitter.getStatus().name());

        // ── Provider lookup ───────────────────────────────────────────
        // Defensive: a half-configured emitter (missing scope) can't have a
        // matching provider. Bail to "no provider" instead of throwing NPE
        // on emitter.getEnvironment().name().
        List<FiscalProviderConfigEntity> activeProviders;
        if (emitter.getEnvironment() == null || emitter.getCountryCode() == null
                || emitter.getTaxAuthorityCode() == null || emitter.getCompanyId() == null) {
            activeProviders = new ArrayList<>();
        } else {
            activeProviders = providerRepo.findActiveByScope(
                    emitter.getCompanyId(),
                    emitter.getCountryCode(),
                    emitter.getTaxAuthorityCode(),
                    emitter.getEnvironment().name()
            );
        }
        // If the emitter pins a specific providerCode, prefer that one.
        if (emitter.getProviderCode() != null && !emitter.getProviderCode().isBlank()) {
            activeProviders = activeProviders.stream()
                    .filter(p -> emitter.getProviderCode().equalsIgnoreCase(p.getProviderCode()))
                    .toList();
        }
        activeProviders = new ArrayList<>(activeProviders); // make sortable
        activeProviders.sort(Comparator.comparingInt(p ->
                p.getPriority() == null ? Integer.MAX_VALUE : p.getPriority()));
        FiscalProviderConfigEntity effectiveProvider = activeProviders.isEmpty() ? null : activeProviders.get(0);
        boolean providerAmbiguous = activeProviders.size() > 1
                && activeProviders.get(0).getPriority() != null
                && activeProviders.get(0).getPriority().equals(activeProviders.get(1).getPriority());

        // ── Certificate lookup ────────────────────────────────────────
        Instant now = Instant.now();
        List<CompanyCertificateEntity> certCandidates = certRepo.findActiveByCompany(emitter.getCompanyId()).stream()
                .filter(c -> c.getValidTo() != null && c.getValidTo().isAfter(now))
                .filter(c -> effectiveProvider == null
                        || (c.getProviderCode() != null
                            && c.getProviderCode().equalsIgnoreCase(effectiveProvider.getProviderCode())))
                .toList();
        certCandidates = new ArrayList<>(certCandidates);
        // Default cert wins; otherwise the longest-valid one.
        certCandidates.sort((a, b) -> {
            int aRank = Boolean.TRUE.equals(a.getIsDefault()) ? 1 : 0;
            int bRank = Boolean.TRUE.equals(b.getIsDefault()) ? 1 : 0;
            if (aRank != bRank) return Integer.compare(bRank, aRank);
            return b.getValidTo().compareTo(a.getValidTo());
        });
        CompanyCertificateEntity effectiveCert = certCandidates.isEmpty() ? null : certCandidates.get(0);
        boolean certAmbiguous = certCandidates.size() > 1
                && Boolean.TRUE.equals(certCandidates.get(0).getIsDefault())
                && Boolean.TRUE.equals(certCandidates.get(1).getIsDefault());

        // ── Build messages ────────────────────────────────────────────
        List<String> missing = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (!emitterActive) missing.add("El emisor no está activo.");
        if (effectiveProvider == null) missing.add("No hay un proveedor de envío activo para este emisor.");
        if (effectiveCert == null) missing.add("No hay un certificado vigente asignado al proveedor.");

        Long daysUntilExpiry = null;
        if (effectiveCert != null && effectiveCert.getValidTo() != null) {
            daysUntilExpiry = ChronoUnit.DAYS.between(now, effectiveCert.getValidTo());
            if (daysUntilExpiry <= EXPIRY_WARN_DAYS) {
                warnings.add("El certificado vence en " + daysUntilExpiry + " día(s). Renuévalo pronto.");
            }
        }
        if (providerAmbiguous) {
            warnings.add("Hay varios proveedores con la misma prioridad. Define un orden claro.");
        }
        if (certAmbiguous) {
            warnings.add("Hay varios certificados marcados como default. Deja sólo uno.");
        }

        boolean ready = emitterActive && effectiveProvider != null && effectiveCert != null;

        return new EmitterReadinessResponse(
                emitterId,
                ready,
                emitterActive,
                effectiveProvider == null ? null : effectiveProvider.getProviderCode(),
                effectiveProvider == null ? null : effectiveProvider.getPriority(),
                providerAmbiguous,
                effectiveCert == null ? null : effectiveCert.getId(),
                effectiveCert == null ? null : effectiveCert.getAlias(),
                effectiveCert == null ? null : effectiveCert.getValidTo(),
                daysUntilExpiry,
                certAmbiguous,
                missing,
                warnings
        );
    }
}
