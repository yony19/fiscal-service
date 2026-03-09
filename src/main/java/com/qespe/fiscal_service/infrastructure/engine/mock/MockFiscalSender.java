package com.qespe.fiscal_service.infrastructure.engine.mock;

import com.qespe.fiscal_service.core.domain.engine.ProviderContext;
import com.qespe.fiscal_service.core.domain.engine.SendResult;
import com.qespe.fiscal_service.core.domain.engine.SignedArtifactResult;
import com.qespe.fiscal_service.core.port.out.FiscalSenderPort;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;
import org.springframework.stereotype.Component;

@Component
public class MockFiscalSender implements FiscalSenderPort {

    @Override
    public SendResult send(FiscalDocumentEntity document, SignedArtifactResult signedArtifactResult, ProviderContext providerContext) {
        return new SendResult(
                true,
                "MOCK_ACCEPTED",
                "Mock successful processing",
                "MOCK-TICKET-" + document.getId(),
                "mock://artifacts/cdr/" + document.getId() + ".zip"
        );
    }
}
