package com.qespe.fiscal_service.core.port.in;

import com.qespe.fiscal_service.core.dto.certificate.CompanyCertificateResponse;

import java.util.UUID;

/**
 * Friendly upload port: the user picks a .pfx + password and the service
 * handles validation, metadata extraction, password encryption and storage
 * inline. No paths, no secret refs, no fingerprints exposed to the user.
 */
public interface CertificateUploadUseCase {

    /**
     * Replaces the cert bytes/password of an existing record. The record is
     * usually created first via the metadata-only endpoint (alias, providerCode,
     * companyId) and then this is called to attach the actual .pfx.
     */
    CompanyCertificateResponse uploadPfx(UUID certificateId, byte[] pfxBytes, String password, String filename);
}
