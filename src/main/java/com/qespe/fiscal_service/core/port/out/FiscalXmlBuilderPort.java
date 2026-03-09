package com.qespe.fiscal_service.core.port.out;

import com.qespe.fiscal_service.core.domain.engine.CertificateContext;
import com.qespe.fiscal_service.core.domain.engine.EmitterContext;
import com.qespe.fiscal_service.core.domain.engine.ProviderContext;
import com.qespe.fiscal_service.core.domain.engine.SignedArtifactResult;
import com.qespe.fiscal_service.core.domain.engine.SendResult;
import com.qespe.fiscal_service.core.domain.engine.XmlBuildResult;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;

public interface FiscalXmlBuilderPort {
    XmlBuildResult build(FiscalDocumentEntity document, EmitterContext emitterContext);
}
