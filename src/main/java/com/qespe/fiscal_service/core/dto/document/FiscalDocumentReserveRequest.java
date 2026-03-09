package com.qespe.fiscal_service.core.dto.document;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record FiscalDocumentReserveRequest(
        @NotNull UUID companyId,
        @NotBlank String countryCode,
        @NotBlank String taxAuthorityCode,
        @NotBlank String environment,
        @NotBlank String sourceService,
        @NotBlank String sourceId,
        String sourceCode,
        String sourceEventType,
        String sourceEventId,
        @NotBlank String idempotencyKey,
        @NotBlank String documentType,
        @NotBlank String documentTypeCode,
        @NotNull LocalDate issueDate,
        LocalTime issueTime,
        @NotBlank String currencyCode,
        @DecimalMin(value = "0.0", inclusive = false) BigDecimal exchangeRate,

        @NotBlank String emitterDocumentType,
        @NotBlank String emitterDocumentNumber,
        @NotBlank String emitterLegalName,
        String emitterTradeName,
        @NotBlank String emitterAddress,

        UUID customerId,
        String customerDocumentType,
        String customerDocumentNumber,
        @NotBlank String customerName,
        String customerAddress,
        String customerEmail,

        @NotNull @DecimalMin("0.0") BigDecimal taxableAmount,
        @NotNull @DecimalMin("0.0") BigDecimal exemptAmount,
        @NotNull @DecimalMin("0.0") BigDecimal unaffectedAmount,
        @NotNull @DecimalMin("0.0") BigDecimal freeAmount,
        @NotNull @DecimalMin("0.0") BigDecimal discountTotal,
        @NotNull @DecimalMin("0.0") BigDecimal chargeTotal,
        @NotNull @DecimalMin("0.0") BigDecimal taxAmount,
        @NotNull @DecimalMin("0.0") BigDecimal igvAmount,
        @NotNull @DecimalMin("0.0") BigDecimal iscAmount,
        @NotNull @DecimalMin("0.0") BigDecimal otherTaxAmount,
        @NotNull @DecimalMin("0.0") BigDecimal totalAmount,

        UUID relatedDocumentId,
        String relatedDocumentTypeCode,
        String relatedDocumentNumber,

        @NotEmpty List<@Valid Line> lines
) {

    public record Line(
            @NotNull Integer lineNo,
            String itemId,
            String itemCode,
            String sku,
            String barcode,
            String itemSunatCode,
            @NotBlank String description,
            String unitCode,
            String unitName,
            @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal quantity,
            @NotNull @DecimalMin("0.0") BigDecimal unitPrice,
            @DecimalMin("0.0") BigDecimal unitValue,
            String taxAffectationCode,
            @DecimalMin("0.0") BigDecimal igvRate,
            @DecimalMin("0.0") BigDecimal iscRate,
            @NotNull @DecimalMin("0.0") BigDecimal discountAmount,
            @NotNull @DecimalMin("0.0") BigDecimal taxableBaseAmount,
            @NotNull @DecimalMin("0.0") BigDecimal taxAmount,
            @NotNull @DecimalMin("0.0") BigDecimal lineTotal,
            Map<String, Object> metadata
    ) {
    }
}

