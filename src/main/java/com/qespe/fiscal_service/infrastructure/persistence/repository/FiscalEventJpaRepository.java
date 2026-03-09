package com.qespe.fiscal_service.infrastructure.persistence.repository;

import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FiscalEventJpaRepository extends JpaRepository<FiscalEventEntity, Long> {

    List<FiscalEventEntity> findByFiscalDocument_IdOrderByCreatedAtDesc(UUID fiscalDocumentId);
}

