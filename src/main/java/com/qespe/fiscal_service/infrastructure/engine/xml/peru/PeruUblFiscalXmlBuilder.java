package com.qespe.fiscal_service.infrastructure.engine.xml.peru;

import com.qespe.fiscal_service.core.domain.engine.EmitterContext;
import com.qespe.fiscal_service.core.domain.engine.StoredArtifactResult;
import com.qespe.fiscal_service.core.domain.engine.XmlBuildResult;
import com.qespe.fiscal_service.core.port.out.FiscalArtifactStoragePort;
import com.qespe.fiscal_service.core.port.out.FiscalXmlBuilderPort;
import com.qespe.fiscal_service.infrastructure.engine.xml.peru.strategy.PeruUblDocumentXmlStrategy;
import com.qespe.fiscal_service.infrastructure.engine.xml.peru.util.XmlSerializer;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;
import com.qespe.fiscal_service.shared.exception.BusinessException;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;

import java.util.List;

@Component
@Primary
public class PeruUblFiscalXmlBuilder implements FiscalXmlBuilderPort {

    private final List<PeruUblDocumentXmlStrategy> strategies;
    private final FiscalArtifactStoragePort artifactStoragePort;

    public PeruUblFiscalXmlBuilder(List<PeruUblDocumentXmlStrategy> strategies, FiscalArtifactStoragePort artifactStoragePort) {
        this.strategies = strategies;
        this.artifactStoragePort = artifactStoragePort;
    }

    @Override
    public XmlBuildResult build(FiscalDocumentEntity document, EmitterContext emitterContext) {
        PeruUblDocumentXmlStrategy strategy = strategies.stream()
                .filter(s -> s.supports(document.getDocumentType()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("No Peru UBL XML strategy found for documentType=" + document.getDocumentType()));

        Document xmlDoc = strategy.build(document, emitterContext);
        String xmlContent = XmlSerializer.toPrettyXml(xmlDoc);
        StoredArtifactResult stored = artifactStoragePort.storeXml(document, xmlContent);

        return new XmlBuildResult(
                xmlContent,
                stored.path(),
                stored.sha256(),
                "QR:" + document.getFullNumber() + "|HASH:" + stored.sha256()
        );
    }
}
