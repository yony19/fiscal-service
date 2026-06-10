package com.qespe.fiscal_service.infrastructure.scheduler;

import com.qespe.fiscal_service.core.port.in.FiscalDocumentProcessingUseCase;
import com.qespe.fiscal_service.core.port.out.FiscalDocumentRepositoryPort;
import com.qespe.fiscal_service.core.usecase.engine.DailySummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Resumen Diario automatico (RC): cada madrugada genera y envia el RC de las
 * boletas del dia ANTERIOR para cada compania que emitio alguna. Cierra la
 * obligacion SUNAT sin que el operador tenga que acordarse (el boton manual
 * de la UI queda para regenerar o fechas puntuales).
 *
 * <p>Idempotente de punta a punta: {@code DailySummaryService} crea UN RC por
 * compania+fecha (clave de idempotencia), asi que si el cron corre dos veces
 * o el operador ya lo genero a mano, no se duplica el correlativo ante SUNAT.
 * Un fallo en una compania no frena a las demas.
 *
 * <p>Cron por defecto 02:30 (hora del servidor; en despliegue Peru el
 * contenedor corre en America/Lima). Apagable con
 * {@code fiscal.daily-summary.auto-enabled=false}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailySummaryScheduler {

    private final DailySummaryService dailySummaryService;
    private final FiscalDocumentRepositoryPort repository;
    private final FiscalDocumentProcessingUseCase processingUseCase;

    @Value("${fiscal.daily-summary.auto-enabled:true}")
    private boolean autoEnabled;

    @Scheduled(cron = "${fiscal.daily-summary.cron:0 30 2 * * *}")
    public void generateYesterdaySummaries() {
        if (!autoEnabled) {
            return;
        }
        LocalDate yesterday = LocalDate.now().minusDays(1);
        List<UUID> companies = repository.findCompanyIdsWithBoletasOn(yesterday);
        if (companies.isEmpty()) {
            return;
        }
        log.info("RC automatico: {} compania(s) con boletas el {}", companies.size(), yesterday);

        int ok = 0;
        for (UUID companyId : companies) {
            try {
                var rc = dailySummaryService.createDailySummary(companyId, yesterday);
                try {
                    processingUseCase.process(rc.getId());
                } catch (Exception ex) {
                    // El RC quedo RESERVED; el scheduler de retry o el boton
                    // de la UI lo reprocesan. No es un fallo del cron.
                    log.warn("RC {} creado pero el pipeline fallo (reprocesable): {}", rc.getFullNumber(), ex.getMessage());
                }
                ok++;
            } catch (Exception ex) {
                log.error("RC automatico fallo para company {} ({}): {}", companyId, yesterday, ex.getMessage());
            }
        }
        log.info("RC automatico completado: {}/{} companias", ok, companies.size());
    }
}
