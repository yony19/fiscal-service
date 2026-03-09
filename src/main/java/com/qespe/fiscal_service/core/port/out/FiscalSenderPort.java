package com.qespe.fiscal_service.core.port.out;

import com.qespe.fiscal_service.core.domain.engine.ProviderContext;
import com.qespe.fiscal_service.core.domain.engine.SendResult;
import com.qespe.fiscal_service.core.domain.engine.SignedArtifactResult;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;

public interface FiscalSenderPort {
    SendResult send(FiscalDocumentEntity document, SignedArtifactResult signedArtifactResult, ProviderContext providerContext);
}
