package com.qespe.fiscal_service.infrastructure.engine.mock;

import com.qespe.fiscal_service.core.domain.engine.CertificateContext;
import com.qespe.fiscal_service.core.domain.engine.SignedArtifactResult;
import com.qespe.fiscal_service.core.domain.engine.XmlBuildResult;
import com.qespe.fiscal_service.core.port.out.FiscalSignerPort;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;
import org.springframework.stereotype.Component;

@Component
public class MockFiscalSigner implements FiscalSignerPort {

    @Override
    public SignedArtifactResult sign(FiscalDocumentEntity document, XmlBuildResult xmlBuildResult, CertificateContext certificateContext) {
        String signedXml = xmlBuildResult.xmlContent() + "\n<!-- MOCK_SIGNED certId=" + certificateContext.certificateId() + " -->";
        return new SignedArtifactResult(
                signedXml,
                "mock://artifacts/signed/" + document.getId() + ".xml",
                "MOCK_SIGNED_HASH_" + document.getId(),
                "MOCK-SIGNATURE-" + document.getId(),
                "MOCK_RSA_SHA256"
        );
    }
}
