package com.qespe.fiscal_service.infrastructure.engine.mock;

import com.qespe.fiscal_service.core.domain.engine.ProviderContext;
import com.qespe.fiscal_service.core.domain.engine.SendResult;
import com.qespe.fiscal_service.core.domain.engine.SignedArtifactResult;
import com.qespe.fiscal_service.core.domain.engine.StatusResult;
import com.qespe.fiscal_service.core.domain.enums.FiscalDocumentStatus;
import com.qespe.fiscal_service.core.port.out.FiscalSenderPort;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;
import com.qespe.fiscal_service.shared.exception.BusinessException;
import org.springframework.stereotype.Component;

@Component
public class MockFiscalSender implements FiscalSenderPort {

    /** Se selecciona solo si el provider_config declara provider_code = MOCK (util en TEST). */
    @Override
    public boolean supports(String providerCode) {
        return "MOCK".equalsIgnoreCase(providerCode);
    }

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
                "mock://artifacts/cdr/" + document.getId() + ".xml",
                "mock-cdr-xml-hash",
                false
        );
    }

    @Override
    public SendResult sendSummary(FiscalDocumentEntity document, SignedArtifactResult signedArtifactResult, ProviderContext providerContext) {
        if (signedArtifactResult.signedXmlPath() == null || signedArtifactResult.signedXmlPath().isBlank()) {
            throw new BusinessException("Signed XML artifact path is required for sendSummary step");
        }
        // Los resumenes son async: el mock simula que SUNAT lo encolo y devolvio un ticket.
        return new SendResult(
                FiscalDocumentStatus.TICKETED,
                "98",
                "Mock summary ticketed",
                "MOCK-SUMMARY-TICKET-" + document.getId(),
                "mock://artifacts/zip/" + document.getId() + ".zip",
                "mock-zip-hash",
                "mock://artifacts/responses/" + document.getId() + ".xml",
                "mock-response-hash",
                null,
                null,
                null,
                null,
                false
        );
    }

    @Override
    public StatusResult queryStatus(FiscalDocumentEntity document, ProviderContext providerContext) {
        return new StatusResult(
                FiscalDocumentStatus.ACCEPTED,
                "0",
                "Mock status accepted",
                document.getAuthorityTicket(),
                "mock://artifacts/responses/" + document.getId() + "-status-response.xml",
                "mock-status-response-hash",
                "mock://artifacts/cdr/" + document.getId() + ".zip",
                "mock-cdr-hash",
                "mock://artifacts/cdr/" + document.getId() + ".xml",
                "mock-cdr-xml-hash",
                false
        );
    }
}
