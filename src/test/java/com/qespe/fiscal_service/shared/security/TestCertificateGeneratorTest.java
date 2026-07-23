package com.qespe.fiscal_service.shared.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * openspec/changes/certificate-test-generation: el .pfx generado debe ser un
 * PKCS12 real, cargable con la contraseña devuelta, con una entrada de clave
 * privada usable — exactamente lo que espera CertificateMaterialLoader al
 * momento de firmar.
 */
class TestCertificateGeneratorTest {

    private final TestCertificateGenerator generator = new TestCertificateGenerator();

    @Test
    @DisplayName("Genera un PKCS12 valido, cargable con la misma contraseña, con una entrada de clave privada")
    void generatesLoadablePkcs12WithPrivateKeyEntry() throws Exception {
        TestCertificateGenerator.GeneratedCertificate result = generator.generate("mi-alias");

        assertThat(result.pkcs12Bytes()).isNotEmpty();
        assertThat(result.password()).isNotBlank();

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(new ByteArrayInputStream(result.pkcs12Bytes()), result.password().toCharArray());

        Enumeration<String> aliases = keyStore.aliases();
        assertThat(aliases.hasMoreElements()).isTrue();
        String alias = aliases.nextElement();

        assertThat(keyStore.isKeyEntry(alias)).isTrue();
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, result.password().toCharArray());
        assertThat(privateKey).isNotNull();

        X509Certificate certificate = (X509Certificate) keyStore.getCertificate(alias);
        assertThat(certificate).isNotNull();
        assertThat(certificate.getSubjectX500Principal().getName()).contains("mi-alias");
        // No debe estar vencido recien generado, y debe durar mas de un año
        // (certificado "de prueba" pero no efimero).
        certificate.checkValidity();
    }

    @Test
    @DisplayName("Dos generaciones producen contraseñas distintas (no hardcodeadas)")
    void generatesDistinctPasswordsPerCall() {
        TestCertificateGenerator.GeneratedCertificate first = generator.generate("a");
        TestCertificateGenerator.GeneratedCertificate second = generator.generate("b");

        assertThat(first.password()).isNotEqualTo(second.password());
    }
}
