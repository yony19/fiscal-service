package com.qespe.fiscal_service.core.port.out;

import com.qespe.fiscal_service.core.domain.engine.StoredArtifactResult;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;

public interface FiscalArtifactStoragePort {
    StoredArtifactResult storeXml(FiscalDocumentEntity document, String xmlContent);
    StoredArtifactResult storeSignedXml(FiscalDocumentEntity document, String signedXmlContent);
}
