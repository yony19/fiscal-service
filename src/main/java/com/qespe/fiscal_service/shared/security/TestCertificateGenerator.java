package com.qespe.fiscal_service.shared.security;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Genera, enteramente en el servidor, un certificado autofirmado (keypair
 * RSA + X.509) empaquetado en PKCS12 — SOLO para uso en el entorno TEST de
 * SUNAT (ver openspec/changes/certificate-test-generation). SUNAT Beta no
 * valida que el certificado pertenezca realmente al RUC emisor, así que un
 * autofirmado es aceptado ahí; nunca debe usarse para PROD (esa validación
 * vive en {@code GenerateTestCertificateService}, no acá).
 *
 * <p>La llave privada nunca sale de este método como archivo — se genera y
 * empaqueta directo en memoria; el caller la persiste igual que un .pfx
 * subido a mano (mismo pipeline, ver {@code CertificateUploadService}).
 */
@Component
public class TestCertificateGenerator {

    private static final int KEY_SIZE_BITS = 2048;
    private static final long VALIDITY_DAYS = 730; // ~2 años; certificado "de prueba", no eterno.

    static {
        // Registrado una sola vez por JVM; BC no rompe nada si ya estaba registrado.
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public record GeneratedCertificate(byte[] pkcs12Bytes, String password) {
    }

    /**
     * Genera el keypair, el certificado autofirmado y lo empaqueta en PKCS12.
     *
     * @param subjectLabel usado como CN del certificado (solo informativo,
     *                     SUNAT Beta no lo valida contra el RUC emisor).
     */
    public GeneratedCertificate generate(String subjectLabel) {
        try {
            KeyPair keyPair = generateKeyPair();
            X509Certificate certificate = buildSelfSignedCertificate(keyPair, subjectLabel);
            String password = randomPassword();
            byte[] pkcs12Bytes = toPkcs12(keyPair, certificate, password);
            return new GeneratedCertificate(pkcs12Bytes, password);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el certificado de prueba", e);
        }
    }

    private KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(KEY_SIZE_BITS, new SecureRandom());
        return generator.generateKeyPair();
    }

    private X509Certificate buildSelfSignedCertificate(KeyPair keyPair, String subjectLabel) throws Exception {
        Instant now = Instant.now();
        Date notBefore = Date.from(now);
        Date notAfter = Date.from(now.plus(VALIDITY_DAYS, ChronoUnit.DAYS));

        String safeLabel = (subjectLabel == null || subjectLabel.isBlank()) ? "certificado-prueba" : subjectLabel;
        X500Name subject = new X500Name("CN=Certificado de prueba " + safeLabel + ", O=Qespe TEST, C=PE");

        BigInteger serial = new BigInteger(64, new SecureRandom());

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                subject, serial, notBefore, notAfter, subject, keyPair.getPublic());

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(keyPair.getPrivate());

        return new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(certBuilder.build(signer));
    }

    private byte[] toPkcs12(KeyPair keyPair, X509Certificate certificate, String password) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry(
                "test-cert",
                keyPair.getPrivate(),
                password.toCharArray(),
                new java.security.cert.Certificate[]{certificate}
        );
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        keyStore.store(out, password.toCharArray());
        return out.toByteArray();
    }

    private String randomPassword() {
        byte[] raw = new byte[18];
        new SecureRandom().nextBytes(raw);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }
}
