package com.qespe.fiscal_service.core.domain.engine;

public record XmlBuildResult(
        String xmlContent,
        String xmlPath,
        String xmlHash,
        String qrData
) {
}
