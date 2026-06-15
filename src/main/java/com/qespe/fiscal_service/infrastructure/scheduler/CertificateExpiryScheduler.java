package com.qespe.fiscal_service.infrastructure.scheduler;

import com.qespe.fiscal_service.core.port.out.CompanyCertificateRepositoryPort;
import com.qespe.fiscal_service.infrastructure.persistence.entity.CompanyCertificateEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Revision diaria del vencimiento de los certificados digitales (.pfx):
 *
 * <ul>
 *   <li>Marca como EXPIRED los certificados ACTIVE cuya vigencia ya paso (antes
 *       seguian figurando ACTIVE con un badge rojo; el estado nunca se
 *       actualizaba solo).</li>
 *   <li>Registra un WARN por cada certificado que vence dentro de los proximos
 *       {@code WARN_DAYS} dias, para que se vea en logs/monitoreo y se renueve a
 *       tiempo (la firma falla recien al emitir si vence sin avisar).</li>
 * </ul>
 *
 * <p>Cron por defecto 02:15. Apagable con
 * {@code fiscal.certificate-expiry.auto-enabled=false}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CertificateExpiryScheduler {

    private static final int WARN_DAYS = 30;

    private final CompanyCertificateRepositoryPort repository;

    @Value("${fiscal.certificate-expiry.auto-enabled:true}")
    private boolean autoEnabled;

    @Scheduled(cron = "${fiscal.certificate-expiry.cron:0 15 2 * * *}")
    @Transactional
    public void reviewExpiries() {
        if (!autoEnabled) {
            return;
        }
        Instant now = Instant.now();
        Instant warnThreshold = now.plus(WARN_DAYS, ChronoUnit.DAYS);
        int expired = 0;
        int expiringSoon = 0;

        for (CompanyCertificateEntity cert : repository.findByStatus("ACTIVE")) {
            Instant validTo = cert.getValidTo();
            if (validTo == null) {
                continue;
            }
            if (validTo.isBefore(now)) {
                cert.setStatus("EXPIRED");
                repository.save(cert);
                expired++;
                log.warn("Certificado VENCIDO marcado EXPIRED: company={} alias={} vencio={}",
                        cert.getCompanyId(), cert.getAlias(), validTo);
            } else if (validTo.isBefore(warnThreshold)) {
                expiringSoon++;
                long days = ChronoUnit.DAYS.between(now, validTo);
                log.warn("Certificado por VENCER en {} dia(s): company={} alias={} vence={}",
                        days, cert.getCompanyId(), cert.getAlias(), validTo);
            }
        }

        if (expired > 0 || expiringSoon > 0) {
            log.info("Revision de certificados: {} marcado(s) EXPIRED, {} por vencer (<= {} dias).",
                    expired, expiringSoon, WARN_DAYS);
        }
    }
}
