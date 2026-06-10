package com.qespe.fiscal_service.core.port.out;

import com.qespe.fiscal_service.core.domain.engine.ProviderContext;
import com.qespe.fiscal_service.core.domain.engine.SendResult;
import com.qespe.fiscal_service.core.domain.engine.SignedArtifactResult;
import com.qespe.fiscal_service.core.domain.engine.StatusResult;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;

public interface FiscalSenderPort {
    SendResult send(FiscalDocumentEntity document, SignedArtifactResult signedArtifactResult, ProviderContext providerContext);
    StatusResult queryStatus(FiscalDocumentEntity document, ProviderContext providerContext);

    /**
     * Envia un documento RESUMEN (Comunicacion de Baja / Resumen Diario) a SUNAT.
     * A diferencia de {@link #send} (sincrono, devuelve CDR), estos documentos son
     * ASYNC: SUNAT responde con un TICKET y el resultado real se obtiene luego con
     * {@link #queryStatus}. El {@link SendResult} retornado lleva estado TICKETED y
     * el {@code authorityTicket}; el resto del pipeline lo procesa igual que un
     * ticket de boleta.
     */
    SendResult sendSummary(FiscalDocumentEntity document, SignedArtifactResult signedArtifactResult, ProviderContext providerContext);

    /**
     * Indica si este adaptador maneja el {@code providerCode} dado. Permite que el
     * proveedor fiscal sea PLUGGABLE por configuracion (no hardcodeado): SUNAT
     * directo, mock, o cualquier OSE futuro se selecciona por el {@code provider_code}
     * de {@code fiscal_provider_config} (gestionable desde el admin), sin tocar el ruteo.
     */
    boolean supports(String providerCode);

    /**
     * Adaptador por defecto cuando ningun {@link #supports} matchea (ej. un
     * {@code provider_code} legacy/no mapeado). Solo uno deberia retornar true.
     */
    default boolean isDefault() {
        return false;
    }
}
