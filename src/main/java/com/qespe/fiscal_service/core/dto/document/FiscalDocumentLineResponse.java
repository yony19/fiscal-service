package com.qespe.fiscal_service.core.dto.document;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record FiscalDocumentLineResponse(
        UUID id,
        Integer lineNo,
        String itemId,
        String itemCode,
        String sku,
        String barcode,
        String itemSunatCode,
        String description,
        String unitCode,
        String unitName,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal unitValue,
        String taxAffectationCode,
        BigDecimal igvRate,
        BigDecimal iscRate,
        BigDecimal discountAmount,
        BigDecimal taxableBaseAmount,
        BigDecimal taxAmount,
        BigDecimal lineTotal,
        Map<String, Object> metadata
) {
}

