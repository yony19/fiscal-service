package com.qespe.fiscal_service.shared.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class FiscalTaxRateUtils {

    public static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    public static final BigDecimal DEFAULT_IGV_RATE_RATIO = new BigDecimal("0.1800");

    private FiscalTaxRateUtils() {
    }

    public static BigDecimal normalizeRatio(BigDecimal rate) {
        if (rate == null) {
            return null;
        }

        BigDecimal normalized = rate.stripTrailingZeros();
        if (normalized.compareTo(BigDecimal.ONE) > 0) {
            // Temporary compatibility for legacy payloads that still send percentages such as 18.
            return rate.divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP);
        }
        return rate.setScale(4, RoundingMode.HALF_UP);
    }

    public static BigDecimal resolveIgvRatio(BigDecimal rate) {
        BigDecimal normalized = normalizeRatio(rate);
        return normalized != null ? normalized : DEFAULT_IGV_RATE_RATIO;
    }

    public static BigDecimal toPercent(BigDecimal ratio) {
        if (ratio == null) {
            return null;
        }
        return normalizeRatio(ratio)
                .multiply(ONE_HUNDRED)
                .stripTrailingZeros();
    }
}
