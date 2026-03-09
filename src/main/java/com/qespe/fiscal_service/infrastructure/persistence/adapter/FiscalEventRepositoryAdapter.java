package com.qespe.fiscal_service.infrastructure.persistence.adapter;

import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalEventEntity;
import com.qespe.fiscal_service.infrastructure.persistence.repository.FiscalEventJpaRepository;
import com.qespe.fiscal_service.core.port.out.FiscalEventRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FiscalEventRepositoryAdapter implements FiscalEventRepositoryPort {

    private final FiscalEventJpaRepository repository;

    @Override
    public FiscalEventEntity save(FiscalEventEntity entity) {
        return repository.save(entity);
    }

    @Override
    public List<FiscalEventEntity> findByFiscalDocumentId(UUID fiscalDocumentId) {
        return repository.findByFiscalDocument_IdOrderByCreatedAtDesc(fiscalDocumentId);
    }
}

