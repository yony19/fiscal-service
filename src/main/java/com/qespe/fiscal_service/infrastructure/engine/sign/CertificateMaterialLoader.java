package com.qespe.fiscal_service.infrastructure.engine.sign;

import com.qespe.fiscal_service.core.domain.engine.CertificateContext;
import com.qespe.fiscal_service.infrastructure.persistence.entity.CompanyCertificateEntity;
import com.qespe.fiscal_service.infrastructure.persistence.repository.CompanyCertificateJpaRepository;
import com.qespe.fiscal_service.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Enumeration;

@Component
@RequiredArgsConstructor
public class CertificateMaterialLoader {

    private final CompanyCertificateJpaRepository certificateJpaRepository;
    private final SecretValueResolver secretValueResolver;

    public CertificateMaterial load(CertificateContext context) {
        CompanyCertificateEntity entity = certificateJpaRepository.findById(context.certificateId())
                .orElseThrow(() -> new BusinessException("Certificate material not found"));

        String mode = context.storageMode() == null ? "" : context.storageMode().trim().toUpperCase();
        byte[] p12bytes;

        switch (mode) {
            case "FILE_PATH" -> p12bytes = readFromFilePath(context.certificatePath());
            case "SECRET_REF" -> p12bytes = readFromSecretRef(context.secretRef());
            case "INLINE_ENCRYPTED" -> {
                if (entity.getCertificateData() == null || entity.getCertificateData().length == 0) {
                    throw new BusinessException("Inline certificate data not available");
                }
                p12bytes = entity.getCertificateData();
            }
            default -> throw new BusinessException("Unsupported certificate storage mode");
        }

        String password = secretValueResolver.resolve(context.passwordSecretRef());
        return parsePkcs12(p12bytes, password == null ? new char[0] : password.toCharArray());
    }

    private byte[] readFromFilePath(String certificatePath) {
        if (certificatePath == null || certificatePath.isBlank()) {
            throw new BusinessException("Certificate path is not configured");
        }
        try {
            return Files.readAllBytes(Path.of(certificatePath).normalize().toAbsolutePath());
        } catch (IOException e) {
            throw new BusinessException("Unable to read certificate file path");
        }
    }

    private byte[] readFromSecretRef(String secretRef) {
        String resolved = secretValueResolver.resolve(secretRef);
        if (resolved == null || resolved.isBlank()) {
            throw new BusinessException("Certificate secret reference could not be resolved");
        }

        if (resolved.startsWith("file:")) {
            return readFromFilePath(resolved.substring(5));
        }

        Path maybePath = Path.of(resolved);
        if (Files.exists(maybePath)) {
            return readFromFilePath(resolved);
        }

        try {
            return Base64.getDecoder().decode(resolved);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Unsupported certificate secret reference format");
        }
    }

    private CertificateMaterial parsePkcs12(byte[] p12bytes, char[] password) {
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(new ByteArrayInputStream(p12bytes), password);

            Enumeration<String> aliases = ks.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (!ks.isKeyEntry(alias)) {
                    continue;
                }
                PrivateKey privateKey = (PrivateKey) ks.getKey(alias, password);
                X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
                if (privateKey != null && cert != null) {
                    return new CertificateMaterial(privateKey, cert);
                }
            }
            throw new BusinessException("No private key entry found in certificate keystore");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("Unable to parse certificate material");
        }
    }
}
