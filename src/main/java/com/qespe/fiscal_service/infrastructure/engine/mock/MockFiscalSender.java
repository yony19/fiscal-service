package com.qespe.fiscal_service.infrastructure.engine.mock;

import com.qespe.fiscal_service.core.domain.engine.ProviderContext;
import com.qespe.fiscal_service.core.domain.engine.SendResult;
import com.qespe.fiscal_service.core.domain.engine.SignedArtifactResult;
import com.qespe.fiscal_service.core.domain.enums.FiscalDocumentStatus;
import com.qespe.fiscal_service.core.port.out.FiscalSenderPort;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;
import com.qespe.fiscal_service.shared.exception.BusinessException;
import org.springframework.stereotype.Component;

@Component
public class MockFiscalSender implements FiscalSenderPort {

    @Override
    public SendResult send(FiscalDocumentEntity document, SignedArtifactResult signedArtifactResult, ProviderContext providerContext) {
        if (signedArtifactResult.signedXmlPath() == null || signedArtifactResult.signedXmlPath().isBlank()) {
            throw new BusinessException("Signed XML artifact path is required for send step");
        }
        return new SendResult(
                FiscalDocumentStatus.ACCEPTED,
                "MOCK_ACCEPTED",
                "Mock successful processing",
                "MOCK-TICKET-" + document.getId(),
                "mock://artifacts/zip/" + document.getId() + ".zip",
                "mock-zip-hash",
                "mock://artifacts/responses/" + document.getId() + ".xml",
                "mock-response-hash",
                "mock://artifacts/cdr/" + document.getId() + ".zip",
                "mock-cdr-hash",
                false
        );
    }
}
