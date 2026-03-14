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
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
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
        return parsePkcs12(p12bytes, password == null ? new char[0] : password.toCharArray(), context.alias());
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

    private CertificateMaterial parsePkcs12(byte[] p12bytes, char[] password, String requestedAlias) {
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(new ByteArrayInputStream(p12bytes), password);
            String alias = resolveAlias(ks, requestedAlias);
            PrivateKey privateKey = (PrivateKey) ks.getKey(alias, password);
            X509Certificate cert = (X509Certificate) ks.getCertificate(alias);

            if (privateKey == null || cert == null) {
                throw new BusinessException("Resolved certificate alias does not contain a usable private key");
            }

            validateCertificate(cert);
            return new CertificateMaterial(privateKey, cert);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("Unable to parse certificate material");
        }
    }

    private String resolveAlias(KeyStore keyStore, String requestedAlias) throws Exception {
        if (requestedAlias != null && !requestedAlias.isBlank()) {
            if (!keyStore.containsAlias(requestedAlias)) {
                throw new BusinessException("Configured certificate alias was not found in keystore");
            }
            if (!keyStore.isKeyEntry(requestedAlias)) {
                throw new BusinessException("Configured certificate alias does not reference a private key entry");
            }
            return requestedAlias;
        }

        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (keyStore.isKeyEntry(alias)) {
                return alias;
            }
        }

        throw new BusinessException("No private key entry found in certificate keystore");
    }

    private void validateCertificate(X509Certificate certificate) {
        try {
            certificate.checkValidity();
        } catch (CertificateExpiredException ex) {
            throw new BusinessException("Certificate has expired");
        } catch (CertificateNotYetValidException ex) {
            throw new BusinessException("Certificate is not yet valid");
        }
    }
}
