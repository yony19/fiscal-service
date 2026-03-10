package com.qespe.fiscal_service.core.domain.engine;

public record SignedArtifactResult(
        String signedXmlContent,
        String signedXmlPath,
        String signedXmlHash,
        String signatureReference,
        String signatureAlgorithm
) {
}
