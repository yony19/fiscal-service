package com.qespe.fiscal_service.core.validation;

import com.qespe.fiscal_service.core.dto.document.FiscalDocumentReserveRequest;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentLineEntity;
import com.qespe.fiscal_service.shared.exception.FiscalValidationException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class FiscalDocumentConsistencyValidator {

    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");
    private static final BigDecimal DEFAULT_IGV_RATE = new BigDecimal("18");

    public void validateForReservation(FiscalDocumentReserveRequest request) {
        BigDecimal lineTotalSum = request.lines().stream()
                .map(FiscalDocumentReserveRequest.Line::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal lineTaxSum = request.lines().stream()
                .map(FiscalDocumentReserveRequest.Line::taxAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal lineTaxableBaseSum = request.lines().stream()
                .map(FiscalDocumentReserveRequest.Line::taxableBaseAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertClose("totalAmount", request.totalAmount(), lineTotalSum);
        assertClose("taxAmount", request.taxAmount(), lineTaxSum);
        assertClose("taxableAmount", request.taxableAmount(), lineTaxableBaseSum);

        request.lines().forEach(this::validateLineConsistency);
    }

    public void validateForProcessing(FiscalDocumentEntity document) {
        List<FiscalDocumentLineEntity> lines = document.getLines();
        if (lines == null || lines.isEmpty()) {
            throw new FiscalValidationException("Fiscal document has no lines");
        }

        BigDecimal lineTotalSum = lines.stream()
                .map(FiscalDocumentLineEntity::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal lineTaxSum = lines.stream()
                .map(FiscalDocumentLineEntity::getTaxAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal lineTaxableBaseSum = lines.stream()
                .map(FiscalDocumentLineEntity::getTaxableBaseAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertClose("totalAmount", document.getTotalAmount(), lineTotalSum);
        assertClose("taxAmount", document.getTaxAmount(), lineTaxSum);
        assertClose("taxableAmount", document.getTaxableAmount(), lineTaxableBaseSum);

        lines.forEach(this::validateLineConsistency);
    }

    private void validateLineConsistency(FiscalDocumentReserveRequest.Line line) {
        if (line.taxAffectationCode() == null) {
            validateLineTaxMath(line.taxableBaseAmount(), line.taxAmount(), line.igvRate(), null, "Line " + line.lineNo());
            return;
        }
        String code = line.taxAffectationCode().trim();
        if (isExemptOrUnaffected(code) && line.taxAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new FiscalValidationException("Line " + line.lineNo() + " has non-zero taxAmount for taxAffectationCode=" + code);
        }
        validateLineTaxMath(line.taxableBaseAmount(), line.taxAmount(), line.igvRate(), code, "Line " + line.lineNo());
    }

    private void validateLineConsistency(FiscalDocumentLineEntity line) {
        if (line.getTaxAffectationCode() == null) {
            validateLineTaxMath(line.getTaxableBaseAmount(), line.getTaxAmount(), line.getIgvRate(), null, "Line " + line.getLineNo());
            return;
        }
        String code = line.getTaxAffectationCode().trim();
        if (isExemptOrUnaffected(code) && line.getTaxAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new FiscalValidationException("Line " + line.getLineNo() + " has non-zero taxAmount for taxAffectationCode=" + code);
        }
        validateLineTaxMath(line.getTaxableBaseAmount(), line.getTaxAmount(), line.getIgvRate(), code, "Line " + line.getLineNo());
    }

    private boolean isExemptOrUnaffected(String code) {
        return code.startsWith("20") || code.startsWith("30");
    }

    private void validateLineTaxMath(
            BigDecimal taxableBaseAmount,
            BigDecimal taxAmount,
            BigDecimal igvRate,
            String taxAffectationCode,
            String lineLabel
    ) {
        if (!isTaxableAffectation(taxAffectationCode)) {
            return;
        }

        BigDecimal effectiveRate = igvRate != null ? igvRate : DEFAULT_IGV_RATE;
        BigDecimal expectedTax = taxableBaseAmount
                .multiply(effectiveRate)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        BigDecimal diff = expectedTax.subtract(taxAmount).abs();
        if (diff.compareTo(TOLERANCE) > 0) {
            throw new FiscalValidationException(
                    lineLabel + " has inconsistent IGV taxAmount: base=" + taxableBaseAmount +
                            ", rate=" + effectiveRate +
                            ", expectedTax=" + expectedTax +
                            ", actualTax=" + taxAmount
            );
        }
    }

    private boolean isTaxableAffectation(String code) {
        if (code == null || code.isBlank()) {
            return true;
        }
        return code.startsWith("10");
    }

    private void assertClose(String field, BigDecimal headerValue, BigDecimal lineAggregate) {
        BigDecimal diff = headerValue.subtract(lineAggregate).abs();
        if (diff.compareTo(TOLERANCE) > 0) {
            throw new FiscalValidationException(
                    "Inconsistent " + field + ": header=" + headerValue + ", lines=" + lineAggregate
            );
        }
    }
}
