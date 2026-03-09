package com.qespe.fiscal_service.core.domain.engine;

public record StoredArtifactResult(
        String path,
        String sha256,
        long sizeBytes
) {
}
