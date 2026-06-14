package com.qespe.fiscal_service.core.usecase.engine;

import com.qespe.fiscal_service.core.domain.enums.FiscalDocumentStatus;
import com.qespe.fiscal_service.core.port.out.FiscalDocumentRepositoryPort;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;
import com.qespe.fiscal_service.shared.exception.BusinessException;
import com.qespe.fiscal_service.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Anulacion de un comprobante aceptado: crea la <strong>Comunicacion de Baja
 * (RA)</strong> que referencia al documento original y la deja lista para el
 * pipeline async ({@code sendSummary} -> ticket -> {@code getStatus}). Cuando
 * SUNAT acepta la RA, el original pasa a VOIDED (ver el hook en
 * {@code FiscalDocumentProcessingService.queryStatus}).
 *
 * <p>Reglas SUNAT que aplica:
 * <ul>
 *   <li>Solo se anula un comprobante ACCEPTED (un REJECTED no existe ante
 *       SUNAT: se corrige y reemite, no se anula).</li>
 *   <li>El plazo legal de la RA es de 7 dias desde la emision; se valida
 *       aqui para fallar temprano con un mensaje claro en vez de un rechazo
 *       criptico de SUNAT.</li>
 *   <li>Nota: la baja de BOLETAS via RA es tolerada en beta/homologacion;
 *       en produccion el camino estricto es el Resumen Diario con estado 3.
 *       Se documenta y se permite aqui para no bloquear la operacion.</li>
 * </ul>
 *
 * <p>Idempotente: si el comprobante ya tiene una RA en curso o aceptada, se
 * devuelve esa en lugar de crear otra (clave {@code VOID:<documentId>}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoidDocumentService {

    private static final DateTimeFormatter RA_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int SUNAT_VOID_DEADLINE_DAYS = 7;

    private final FiscalDocumentRepositoryPort repository;

    @Transactional
    public FiscalDocumentEntity createVoid(UUID documentId, String reason) {
        FiscalDocumentEntity original = repository.findById(documentId)
                .orElseThrow(() -> new NotFoundException("Fiscal document not found: " + documentId));

        if (original.getStatus() != FiscalDocumentStatus.ACCEPTED) {
            throw new BusinessException(
                    "Solo se puede anular un comprobante ACEPTADO por SUNAT (estado actual: "
                            + original.getStatus() + "). Un rechazado se corrige y reemite.");
        }
        if ("VOID".equalsIgnoreCase(original.getDocumentType())
                || "DAILY_SUMMARY".equalsIgnoreCase(original.getDocumentType())) {
            throw new BusinessException("Una comunicacion de baja o un resumen no se anulan con otra RA.");
        }
        if (original.getIssueDate() != null
                && original.getIssueDate().plusDays(SUNAT_VOID_DEADLINE_DAYS).isBefore(LocalDate.now())) {
            throw new BusinessException(
                    "Fuera del plazo SUNAT: la Comunicacion de Baja debe enviarse dentro de los "
                            + SUNAT_VOID_DEADLINE_DAYS + " dias de la emision (" + original.getIssueDate() + ").");
        }

        String idempotencyKey = "VOID:" + original.getId();
        var existing = repository.findByIdempotency(original.getCompanyId(), "fiscal-service", idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        LocalDate today = LocalDate.now();
        long correlativo = repository.countByDocumentTypeAndIssueDate(original.getCompanyId(), "VOID", today) + 1;
        String fullNumber = "RA-" + today.format(RA_DATE) + "-" + correlativo;

        FiscalDocumentEntity ra = new FiscalDocumentEntity();
        ra.setCompanyId(original.getCompanyId());
        ra.setCountryCode(original.getCountryCode());
        ra.setTaxAuthorityCode(original.getTaxAuthorityCode());
        ra.setCurrencyCode(original.getCurrencyCode());
        ra.setDocumentType("VOID");
        ra.setDocumentTypeCode("RA");
        ra.setSeries("RA-" + today.format(RA_DATE));
        ra.setNumber(correlativo);
        ra.setFullNumber(fullNumber);
        ra.setIssueDate(today);
        ra.setSourceService("fiscal-service");
        ra.setSourceId(original.getId().toString());
        ra.setIdempotencyKey(idempotencyKey);
        ra.setStatus(FiscalDocumentStatus.RESERVED);
        // Campos NOT NULL que la RA hereda del comprobante anulado: mismo emisor
        // y entorno. (Los montos van en ZERO: una Comunicacion de Baja no lleva
        // importes.) Sin esto, el insert violaba las restricciones NOT NULL.
        ra.setEnvironment(original.getEnvironment());
        ra.setEmitterDocumentType(original.getEmitterDocumentType());
        ra.setEmitterDocumentNumber(original.getEmitterDocumentNumber());
        ra.setEmitterLegalName(original.getEmitterLegalName());
        ra.setEmitterTradeName(original.getEmitterTradeName());
        ra.setEmitterAddress(original.getEmitterAddress());
        ra.setCustomerName(original.getCustomerName());
        // Referencia al comprobante anulado — la estrategia XML navega estos
        // campos para armar la VoidedDocumentsLine y el ReferenceDate.
        ra.setRelatedDocument(original);
        ra.setRelatedDocumentTypeCode(original.getDocumentTypeCode());
        ra.setRelatedDocumentNumber(original.getFullNumber());
        // Motivo de anulacion: se persiste y viaja a SUNAT como
        // sac:VoidReasonDescription (max 100 chars). Si el operador no lo indica,
        // el XML cae al generico 'ANULACION'.
        if (reason != null && !reason.isBlank()) {
            String r = reason.trim();
            ra.setVoidReason(r.length() > 100 ? r.substring(0, 100) : r);
        }

        FiscalDocumentEntity saved = repository.save(ra);
        log.info("Comunicacion de Baja creada: {} anula {} ({})",
                fullNumber, original.getFullNumber(), reason);
        return saved;
    }
}
