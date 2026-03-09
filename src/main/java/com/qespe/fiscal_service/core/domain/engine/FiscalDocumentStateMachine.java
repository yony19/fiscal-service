package com.qespe.fiscal_service.core.domain.engine;

import com.qespe.fiscal_service.core.domain.enums.FiscalDocumentStatus;
import com.qespe.fiscal_service.shared.exception.BusinessException;

import java.util.Map;
import java.util.Set;

public final class FiscalDocumentStateMachine {

    private static final Map<FiscalDocumentStatus, Set<FiscalDocumentStatus>> ALLOWED = Map.of(
            FiscalDocumentStatus.RESERVED, Set.of(FiscalDocumentStatus.PENDING_XML, FiscalDocumentStatus.ERROR),
            FiscalDocumentStatus.PENDING_XML, Set.of(FiscalDocumentStatus.XML_GENERATED, FiscalDocumentStatus.ERROR),
            FiscalDocumentStatus.XML_GENERATED, Set.of(FiscalDocumentStatus.SIGNED, FiscalDocumentStatus.ERROR),
            FiscalDocumentStatus.SIGNED, Set.of(FiscalDocumentStatus.QUEUED_FOR_SEND, FiscalDocumentStatus.ERROR),
            FiscalDocumentStatus.QUEUED_FOR_SEND, Set.of(FiscalDocumentStatus.SENT, FiscalDocumentStatus.ERROR),
            FiscalDocumentStatus.SENT, Set.of(FiscalDocumentStatus.ACCEPTED, FiscalDocumentStatus.REJECTED, FiscalDocumentStatus.OBSERVED, FiscalDocumentStatus.ERROR)
    );

    private FiscalDocumentStateMachine() {
    }

    public static void assertTransition(FiscalDocumentStatus current, FiscalDocumentStatus next) {
        if (!ALLOWED.containsKey(current) || !ALLOWED.get(current).contains(next)) {
            throw new BusinessException("Invalid fiscal status transition: " + current + " -> " + next);
        }
    }

    public static boolean isTerminalOrAdvanced(FiscalDocumentStatus status) {
        return status == FiscalDocumentStatus.ACCEPTED
                || status == FiscalDocumentStatus.REJECTED
                || status == FiscalDocumentStatus.VOIDED;
    }
}
