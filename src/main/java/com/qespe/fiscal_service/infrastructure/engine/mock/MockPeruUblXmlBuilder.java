package com.qespe.fiscal_service.infrastructure.engine.mock;

import com.qespe.fiscal_service.core.domain.engine.EmitterContext;
import com.qespe.fiscal_service.core.domain.engine.XmlBuildResult;
import com.qespe.fiscal_service.core.port.out.FiscalXmlBuilderPort;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
public class MockPeruUblXmlBuilder implements FiscalXmlBuilderPort {

    @Override
    public XmlBuildResult build(FiscalDocumentEntity document, EmitterContext emitterContext) {
        String xml = """
                <MockFiscalDocument>
                  <DocumentId>%s</DocumentId>
                  <FullNumber>%s</FullNumber>
                  <IssueDate>%s</IssueDate>
                  <EmitterDocument>%s-%s</EmitterDocument>
                  <TotalAmount>%s</TotalAmount>
                </MockFiscalDocument>
                """.formatted(
                document.getId(),
                document.getFullNumber(),
                document.getIssueDate(),
                emitterContext.documentType(),
                emitterContext.documentNumber(),
                document.getTotalAmount()
        );

        return new XmlBuildResult(
                xml,
                "mock://artifacts/xml/" + document.getId() + ".xml",
                sha256(xml),
                "MOCK_QR:" + document.getFullNumber()
        );
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }
}
