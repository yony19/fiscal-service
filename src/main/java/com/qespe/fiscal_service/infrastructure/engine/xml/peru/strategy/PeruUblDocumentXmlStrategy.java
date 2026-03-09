package com.qespe.fiscal_service.infrastructure.engine.xml.peru.strategy;

import com.qespe.fiscal_service.core.domain.engine.EmitterContext;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;
import org.w3c.dom.Document;

public interface PeruUblDocumentXmlStrategy {
    boolean supports(String documentType);
    Document build(FiscalDocumentEntity document, EmitterContext emitterContext);
}
