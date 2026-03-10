package com.qespe.fiscal_service.infrastructure.engine.sign;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public record CertificateMaterial(
        PrivateKey privateKey,
        X509Certificate certificate
) {
}
