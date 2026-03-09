package com.qespe.fiscal_service.infrastructure.engine.async;

import com.qespe.fiscal_service.core.domain.event.FiscalDocumentReservedEvent;
import com.qespe.fiscal_service.core.port.in.FiscalDocumentProcessingUseCase;
import com.qespe.fiscal_service.core.port.out.FiscalDocumentRepositoryPort;
import com.qespe.fiscal_service.core.port.out.FiscalEventRepositoryPort;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalEventEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class AutoFiscalDocumentProcessingListener {

    private final FiscalDocumentRepositoryPort documentRepository;
    private final FiscalEventRepositoryPort eventRepository;
    private final FiscalDocumentProcessingUseCase processingUseCase;

    @Async("fiscalProcessingExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFiscalDocumentReserved(FiscalDocumentReservedEvent event) {
        documentRepository.findById(event.fiscalDocumentId()).ifPresent(document -> {
            appendEvent(document.getId(), "AUTO_PROCESSING_TRIGGERED", "Automatic post-reservation processing triggered", Map.of());
            try {
                processingUseCase.process(document.getId());
            } catch (Exception ex) {
                appendEvent(document.getId(), "AUTO_PROCESSING_TRIGGER_FAILED", "Automatic processing failed", Map.of("errorCode", "AUTO_PROCESSING_ERROR"));
                log.warn("Auto processing trigger failed for fiscalDocumentId={}", document.getId());
            }
        });
    }

    private void appendEvent(java.util.UUID fiscalDocumentId, String type, String message, Map<String, Object> payload) {
        documentRepository.findById(fiscalDocumentId).ifPresent(document -> {
            FiscalEventEntity event = new FiscalEventEntity();
            event.setFiscalDocument(document);
            event.setEventType(type);
            event.setMessage(message);
            event.setPayload(payload);
            eventRepository.save(event);
        });
    }
}
