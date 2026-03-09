package com.qespe.fiscal_service.core.domain.enums;

public enum FiscalDocumentStatus {
    RESERVED,
    PENDING_XML,
    XML_GENERATED,
    SIGNED,
    QUEUED_FOR_SEND,
    SENT,
    ACCEPTED,
    OBSERVED,
    REJECTED,
    VOID_PENDING,
    VOIDED,
    ERROR
}

