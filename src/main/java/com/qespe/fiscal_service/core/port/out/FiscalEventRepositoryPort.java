package com.qespe.fiscal_service.core.port.out;

import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalEventEntity;

import java.util.List;
import java.util.UUID;

public interface FiscalEventRepositoryPort {
    FiscalEventEntity save(FiscalEventEntity entity);
    List<FiscalEventEntity> findByFiscalDocumentId(UUID fiscalDocumentId);
}

