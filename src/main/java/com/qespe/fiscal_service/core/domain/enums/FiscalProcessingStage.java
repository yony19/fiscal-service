package com.qespe.fiscal_service.core.domain.enums;

public enum FiscalProcessingStage {
    VALIDATION,
    EMITTER_RESOLUTION,
    PROVIDER_RESOLUTION,
    CERTIFICATE_RESOLUTION,
    XML_BUILD,
    SIGN,
    SEND,
    UNKNOWN
}
