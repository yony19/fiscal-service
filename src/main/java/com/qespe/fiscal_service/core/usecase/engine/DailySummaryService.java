package com.qespe.fiscal_service.core.usecase.engine;

import com.qespe.fiscal_service.core.domain.enums.FiscalDocumentStatus;
import com.qespe.fiscal_service.core.port.out.FiscalDocumentRepositoryPort;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;
import com.qespe.fiscal_service.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Creacion del <strong>Resumen Diario de boletas (RC)</strong> — E1.2 / PRD #2.
 *
 * <p>El RC informa a SUNAT, en un solo documento async, las boletas emitidas en
 * una fecha. Decision de modelo: las "lineas" del RC NO se persisten como
 * {@code fiscal_document_line} (ese modelo es de productos); se derivan al
 * construir el XML consultando las boletas de la fecha resumida. La fecha
 * resumida viaja en {@code relatedDocumentNumber} (ISO-8601) — mismo campo de
 * referencia que usa la Comunicacion de Baja, sin migracion de esquema.
 *
 * <p>Idempotencia: un RC por compania y fecha resumida (clave
 * {@code DAILY_SUMMARY:yyyy-MM-dd}); pedirlo de nuevo devuelve el existente en
 * vez de duplicar correlativo ante SUNAT.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailySummaryService {

    private static final DateTimeFormatter RC_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final FiscalDocumentRepositoryPort repository;

    @Transactional
    public FiscalDocumentEntity createDailySummary(UUID companyId, LocalDate summaryDate) {
        if (companyId == null || summaryDate == null) {
            throw new BusinessException("companyId y la fecha del resumen son requeridos");
        }
        if (summaryDate.isAfter(LocalDate.now())) {
            throw new BusinessException("No se puede resumir una fecha futura");
        }

        String idempotencyKey = "DAILY_SUMMARY:" + summaryDate;
        var existing = repository.findByIdempotency(companyId, "fiscal-service", idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        List<FiscalDocumentEntity> boletas = repository.findBoletasByIssueDate(companyId, summaryDate).stream()
                .filter(b -> b.getStatus() != FiscalDocumentStatus.ERROR
                        && b.getStatus() != FiscalDocumentStatus.VOIDED
                        && b.getStatus() != FiscalDocumentStatus.REJECTED)
                .toList();
        if (boletas.isEmpty()) {
            throw new BusinessException("No hay boletas emitidas el " + summaryDate + " para resumir");
        }

        LocalDate today = LocalDate.now();
        long correlativo = repository.countByDocumentTypeAndIssueDate(companyId, "DAILY_SUMMARY", today) + 1;
        String fullNumber = "RC-" + today.format(RC_DATE) + "-" + correlativo;

        FiscalDocumentEntity template = boletas.get(0);
        FiscalDocumentEntity rc = new FiscalDocumentEntity();
        rc.setCompanyId(companyId);
        rc.setCountryCode(template.getCountryCode());
        rc.setTaxAuthorityCode(template.getTaxAuthorityCode());
        rc.setCurrencyCode(template.getCurrencyCode());
        rc.setDocumentType("DAILY_SUMMARY");
        rc.setDocumentTypeCode("RC");
        rc.setSeries("RC-" + today.format(RC_DATE));
        rc.setNumber(correlativo);
        rc.setFullNumber(fullNumber);
        rc.setIssueDate(today);
        // La fecha RESUMIDA (ReferenceDate del XML). Ver JavaDoc de la clase.
        rc.setRelatedDocumentNumber(summaryDate.toString());
        rc.setSourceService("fiscal-service");
        rc.setIdempotencyKey(idempotencyKey);
        rc.setStatus(FiscalDocumentStatus.RESERVED);

        BigDecimal taxable = BigDecimal.ZERO;
        BigDecimal exempt = BigDecimal.ZERO;
        BigDecimal unaffected = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (FiscalDocumentEntity b : boletas) {
            taxable = taxable.add(nvl(b.getTaxableAmount()));
            exempt = exempt.add(nvl(b.getExemptAmount()));
            unaffected = unaffected.add(nvl(b.getUnaffectedAmount()));
            tax = tax.add(nvl(b.getTaxAmount()));
            total = total.add(nvl(b.getTotalAmount()));
        }
        rc.setTaxableAmount(taxable);
        rc.setExemptAmount(exempt);
        rc.setUnaffectedAmount(unaffected);
        rc.setTaxAmount(tax);
        rc.setTotalAmount(total);

        FiscalDocumentEntity saved = repository.save(rc);
        log.info("Resumen Diario creado: {} ({} boletas del {}, total {})",
                fullNumber, boletas.size(), summaryDate, total);
        return saved;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
