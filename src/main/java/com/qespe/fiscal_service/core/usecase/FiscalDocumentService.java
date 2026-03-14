package com.qespe.fiscal_service.core.usecase;

import com.qespe.fiscal_service.core.domain.enums.FiscalDocumentStatus;
import com.qespe.fiscal_service.core.domain.enums.FiscalDocumentType;
import com.qespe.fiscal_service.core.domain.enums.FiscalEnvironment;
import com.qespe.fiscal_service.core.domain.engine.EmitterContext;
import com.qespe.fiscal_service.core.dto.common.PageResponse;
import com.qespe.fiscal_service.core.dto.document.FiscalDocumentReserveRequest;
import com.qespe.fiscal_service.core.dto.document.FiscalDocumentReserveResponse;
import com.qespe.fiscal_service.core.dto.document.FiscalDocumentResponse;
import com.qespe.fiscal_service.core.dto.event.FiscalEventResponse;
import com.qespe.fiscal_service.core.domain.event.FiscalDocumentReservedEvent;
import com.qespe.fiscal_service.infrastructure.mapper.FiscalDocumentMapper;
import com.qespe.fiscal_service.infrastructure.mapper.FiscalEventMapper;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentLineEntity;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalEventEntity;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalSeriesEntity;
import com.qespe.fiscal_service.infrastructure.persistence.repository.FiscalDocumentSpecifications;
import com.qespe.fiscal_service.core.port.in.FiscalDocumentUseCase;
import com.qespe.fiscal_service.core.port.out.FiscalDocumentRepositoryPort;
import com.qespe.fiscal_service.core.port.out.FiscalEventRepositoryPort;
import com.qespe.fiscal_service.core.port.out.FiscalSeriesRepositoryPort;
import com.qespe.fiscal_service.core.validation.FiscalDocumentConsistencyValidator;
import com.qespe.fiscal_service.shared.exception.BusinessException;
import com.qespe.fiscal_service.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FiscalDocumentService implements FiscalDocumentUseCase {

    private final FiscalDocumentRepositoryPort documentRepository;
    private final FiscalSeriesRepositoryPort seriesRepository;
    private final FiscalEventRepositoryPort eventRepository;
    private final FiscalDocumentMapper documentMapper;
    private final FiscalEventMapper eventMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final FiscalDocumentConsistencyValidator consistencyValidator;
    private final com.qespe.fiscal_service.core.usecase.engine.EmitterResolutionService emitterResolutionService;

    @Override
    @Transactional
    public FiscalDocumentReserveResponse reserve(FiscalDocumentReserveRequest request) {
        validateRequest(request);
        consistencyValidator.validateForReservation(request);

        var existing = documentRepository.findByIdempotency(request.companyId(), request.sourceService(), request.idempotencyKey());
        if (existing.isPresent()) {
            return documentMapper.toReserveResponse(existing.get());
        }

        FiscalEnvironment environment = parseEnvironment(request.environment());
        List<FiscalSeriesEntity> activeSeries = seriesRepository.findActiveForUpdate(
                request.companyId(),
                request.countryCode(),
                request.taxAuthorityCode(),
                request.documentTypeCode(),
                environment
        );

        if (activeSeries.isEmpty()) {
            throw new BusinessException("No active fiscal series found for company/document/environment");
        }
        if (activeSeries.size() > 1) {
            throw new BusinessException("More than one active fiscal series found for the same company/document/environment");
        }

        FiscalSeriesEntity series = activeSeries.get(0);
        long reservedNumber = series.getNextNumber();
        series.setNextNumber(reservedNumber + 1);
        seriesRepository.save(series);

        EmitterContext emitterContext = emitterResolutionService.resolve(
                request.companyId(),
                environment.name(),
                request.countryCode(),
                request.taxAuthorityCode()
        );
        FiscalDocumentEntity document = buildDocument(request, environment, series, reservedNumber, emitterContext);
        try {
            FiscalDocumentEntity saved = documentRepository.save(document);
            createReservedEvent(saved);
            eventPublisher.publishEvent(new FiscalDocumentReservedEvent(saved.getId()));
            return documentMapper.toReserveResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            return documentRepository.findByIdempotency(request.companyId(), request.sourceService(), request.idempotencyKey())
                    .map(documentMapper::toReserveResponse)
                    .orElseThrow(() -> ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public FiscalDocumentResponse getById(UUID id) {
        FiscalDocumentEntity entity = documentRepository.findWithLinesById(id)
                .orElseThrow(() -> new NotFoundException("Fiscal document not found: " + id));
        return documentMapper.toResponse(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FiscalDocumentResponse> search(
            UUID companyId,
            String documentType,
            String status,
            String environment,
            String series,
            LocalDate issueDateFrom,
            LocalDate issueDateTo,
            String sourceService,
            String sourceId,
            int page,
            int size
    ) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<FiscalDocumentEntity> result = documentRepository.search(
                FiscalDocumentSpecifications.byFilters(
                        companyId, documentType, status, environment, series,
                        issueDateFrom, issueDateTo, sourceService, sourceId
                ),
                pageable
        );

        List<FiscalDocumentResponse> content = result.getContent().stream()
                .map(documentMapper::toResponse)
                .toList();

        return new PageResponse<>(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isLast()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<FiscalEventResponse> listEvents(UUID fiscalDocumentId) {
        return eventMapper.toResponses(eventRepository.findByFiscalDocumentId(fiscalDocumentId));
    }

    private FiscalDocumentEntity buildDocument(
            FiscalDocumentReserveRequest request,
            FiscalEnvironment environment,
            FiscalSeriesEntity series,
            long reservedNumber,
            EmitterContext emitterContext
    ) {
        FiscalDocumentEntity doc = new FiscalDocumentEntity();
        doc.setCompanyId(request.companyId());
        doc.setCountryCode(request.countryCode());
        doc.setTaxAuthorityCode(request.taxAuthorityCode());
        doc.setEnvironment(environment);
        doc.setSourceService(request.sourceService());
        doc.setSourceId(request.sourceId());
        doc.setSourceCode(request.sourceCode());
        doc.setSourceEventType(request.sourceEventType());
        doc.setSourceEventId(request.sourceEventId());
        doc.setIdempotencyKey(request.idempotencyKey());
        doc.setDocumentType(request.documentType());
        doc.setDocumentTypeCode(request.documentTypeCode());
        doc.setIssueDate(request.issueDate());
        doc.setIssueTime(request.issueTime());
        doc.setSeriesRef(series);
        doc.setSeries(series.getSeries());
        doc.setNumber(reservedNumber);
        doc.setFullNumber(series.getSeries() + "-" + reservedNumber);
        doc.setCurrencyCode(request.currencyCode());
        doc.setExchangeRate(request.exchangeRate());

        doc.setEmitterDocumentType(emitterContext.documentType());
        doc.setEmitterDocumentNumber(emitterContext.documentNumber());
        doc.setEmitterLegalName(emitterContext.legalName());
        doc.setEmitterTradeName(emitterContext.tradeName());
        doc.setEmitterAddress(emitterContext.fiscalAddress());

        doc.setCustomerId(request.customerId());
        doc.setCustomerDocumentType(request.customerDocumentType());
        doc.setCustomerDocumentNumber(request.customerDocumentNumber());
        doc.setCustomerName(request.customerName());
        doc.setCustomerAddress(request.customerAddress());
        doc.setCustomerEmail(request.customerEmail());

        doc.setTaxableAmount(request.taxableAmount());
        doc.setExemptAmount(request.exemptAmount());
        doc.setUnaffectedAmount(request.unaffectedAmount());
        doc.setFreeAmount(request.freeAmount());
        doc.setDiscountTotal(request.discountTotal());
        doc.setChargeTotal(request.chargeTotal());
        doc.setTaxAmount(request.taxAmount());
        doc.setIgvAmount(request.igvAmount());
        doc.setIscAmount(request.iscAmount());
        doc.setOtherTaxAmount(request.otherTaxAmount());
        doc.setTotalAmount(request.totalAmount());

        doc.setStatus(FiscalDocumentStatus.RESERVED);
        doc.setSendAttemptCount(0);
        doc.setRetryableError(false);
        doc.setRetryCount(0);

        if (request.relatedDocumentId() != null) {
            FiscalDocumentEntity related = new FiscalDocumentEntity();
            related.setId(request.relatedDocumentId());
            doc.setRelatedDocument(related);
        }
        doc.setRelatedDocumentTypeCode(request.relatedDocumentTypeCode());
        doc.setRelatedDocumentNumber(request.relatedDocumentNumber());

        List<FiscalDocumentLineEntity> lines = request.lines().stream()
                .map(line -> toLine(line, doc))
                .toList();
        doc.getLines().addAll(lines);
        return doc;
    }

    private FiscalDocumentLineEntity toLine(FiscalDocumentReserveRequest.Line line, FiscalDocumentEntity doc) {
        FiscalDocumentLineEntity entity = new FiscalDocumentLineEntity();
        entity.setFiscalDocument(doc);
        entity.setLineNo(line.lineNo());
        entity.setItemId(line.itemId());
        entity.setItemCode(line.itemCode());
        entity.setSku(line.sku());
        entity.setBarcode(line.barcode());
        entity.setItemSunatCode(line.itemSunatCode());
        entity.setDescription(line.description());
        entity.setUnitCode(line.unitCode());
        entity.setUnitName(line.unitName());
        entity.setQuantity(line.quantity());
        entity.setUnitPrice(line.unitPrice());
        entity.setUnitValue(line.unitValue());
        entity.setTaxAffectationCode(line.taxAffectationCode());
        entity.setIgvRate(line.igvRate());
        entity.setIscRate(line.iscRate());
        entity.setDiscountAmount(line.discountAmount());
        entity.setTaxableBaseAmount(line.taxableBaseAmount());
        entity.setTaxAmount(line.taxAmount());
        entity.setLineTotal(line.lineTotal());
        entity.setMetadata(line.metadata() == null ? Map.of() : line.metadata());
        return entity;
    }

    private void createReservedEvent(FiscalDocumentEntity document) {
        FiscalEventEntity event = new FiscalEventEntity();
        event.setFiscalDocument(document);
        event.setEventType("RESERVED");
        event.setMessage("Fiscal document reserved synchronously");
        event.setPayload(Map.of(
                "status", document.getStatus().name(),
                "fullNumber", document.getFullNumber(),
                "sourceService", document.getSourceService(),
                "sourceId", document.getSourceId()
        ));
        eventRepository.save(event);
    }

    private FiscalEnvironment parseEnvironment(String environment) {
        try {
            return FiscalEnvironment.valueOf(environment.toUpperCase());
        } catch (Exception ex) {
            throw new BusinessException("Invalid environment: " + environment);
        }
    }

    private void validateRequest(FiscalDocumentReserveRequest request) {
        if ("TICKET".equalsIgnoreCase(request.documentType())) {
            throw new BusinessException("TICKET is operational and must not be created as fiscal_document");
        }

        try {
            FiscalDocumentType.valueOf(request.documentType().toUpperCase());
        } catch (Exception ex) {
            throw new BusinessException("Unsupported documentType: " + request.documentType());
        }

        validateNonNegative("taxableAmount", request.taxableAmount());
        validateNonNegative("exemptAmount", request.exemptAmount());
        validateNonNegative("unaffectedAmount", request.unaffectedAmount());
        validateNonNegative("freeAmount", request.freeAmount());
        validateNonNegative("discountTotal", request.discountTotal());
        validateNonNegative("chargeTotal", request.chargeTotal());
        validateNonNegative("taxAmount", request.taxAmount());
        validateNonNegative("igvAmount", request.igvAmount());
        validateNonNegative("iscAmount", request.iscAmount());
        validateNonNegative("otherTaxAmount", request.otherTaxAmount());
        validateNonNegative("totalAmount", request.totalAmount());

        Set<Integer> uniqueLineNos = new HashSet<>();
        for (FiscalDocumentReserveRequest.Line line : request.lines()) {
            if (!uniqueLineNos.add(line.lineNo())) {
                throw new BusinessException("Duplicate lineNo: " + line.lineNo());
            }
            validateNonNegative("line.discountAmount", line.discountAmount());
            validateNonNegative("line.taxableBaseAmount", line.taxableBaseAmount());
            validateNonNegative("line.taxAmount", line.taxAmount());
            validateNonNegative("line.lineTotal", line.lineTotal());
            if (line.quantity() == null || line.quantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("line.quantity must be > 0");
            }
            if (line.unitPrice() == null || line.unitPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("line.unitPrice must be >= 0");
            }
        }

        if (request.exchangeRate() != null && request.exchangeRate().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("exchangeRate must be > 0 when provided");
        }

        if (Objects.isNull(request.issueDate())) {
            throw new BusinessException("issueDate is required");
        }
    }

    private void validateNonNegative(String field, BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(field + " must be >= 0");
        }
    }
}

