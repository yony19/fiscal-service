package com.qespe.fiscal_service.infrastructure.scheduler;

import com.qespe.fiscal_service.core.domain.enums.FiscalDocumentStatus;
import com.qespe.fiscal_service.core.port.in.FiscalDocumentProcessingUseCase;
import com.qespe.fiscal_service.infrastructure.persistence.repository.FiscalDocumentJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Polls for fiscal documents in ERROR state with {@code retryableError=true}
 * and {@code nextRetryAt <= now}, and re-runs the processing pipeline.
 *
 * <p>Until this scheduler existed, retry-eligible documents would sit in ERROR
 * forever unless someone clicked "retry" in the UI. SUNAT outages routinely
 * last 5–30 minutes, so without automatic retries an outage could leave a
 * day's worth of vouchers stuck without operator awareness.
 *
 * <p>The scheduler is bounded per tick by {@code retryBatchSize} so a backlog
 * doesn't monopolize the executor; each call still goes through
 * {@link FiscalDocumentProcessingUseCase#retry}, which respects the existing
 * state machine and retry-policy backoff.
 *
 * <p>Failures of individual retries are caught locally so one bad document
 * doesn't poison the whole sweep.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FiscalDocumentRetryScheduler {

    private final FiscalDocumentJpaRepository repository;
    private final FiscalDocumentProcessingUseCase processingUseCase;

    @Value("${fiscal.retry.batch-size:50}")
    private int retryBatchSize;

    /** Runs every 60s. Disable by setting fiscal.retry.enabled=false (handled at bean level if needed). */
    @Scheduled(fixedDelayString = "${fiscal.retry.delay-ms:60000}")
    public void retryDueDocuments() {
        Instant now = Instant.now();
        List<UUID> due = repository.findRetryableIds(
                FiscalDocumentStatus.ERROR, now, PageRequest.of(0, retryBatchSize));

        if (due.isEmpty()) return;
        log.info("[fiscal-retry] {} document(s) due for retry", due.size());

        int success = 0;
        int failed = 0;
        for (UUID id : due) {
            try {
                processingUseCase.retry(id);
                success++;
            } catch (Exception e) {
                // Don't let one failure abort the sweep; the use case already
                // handles state transitions / nextRetryAt update internally.
                failed++;
                log.warn("[fiscal-retry] retry failed for documentId={}: {}", id, e.getMessage());
            }
        }
        log.info("[fiscal-retry] sweep done: success={} failed={}", success, failed);
    }
}
