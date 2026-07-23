package com.qespe.fiscal_service.core.port.in;

import com.qespe.fiscal_service.core.dto.certificate.CompanyCertificateResponse;

import java.util.UUID;

/**
 * Genera un certificado de prueba autofirmado y lo persiste igual que un
 * .pfx subido a mano — SOLO permitido cuando el Proveedor vinculado al
 * certificado está en entorno TEST. Ver
 * openspec/changes/certificate-test-generation.
 */
public interface GenerateTestCertificateUseCase {
    CompanyCertificateResponse generateTest(UUID certificateId);
}
