package com.qespe.fiscal_service.core.port.out;

import com.qespe.fiscal_service.core.domain.engine.CertificateContext;
import com.qespe.fiscal_service.core.domain.engine.SignedArtifactResult;
import com.qespe.fiscal_service.core.domain.engine.XmlBuildResult;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;

public interface FiscalSignerPort {
    SignedArtifactResult sign(FiscalDocumentEntity document, XmlBuildResult xmlBuildResult, CertificateContext certificateContext);
}
