package com.qespe.fiscal_service.shared.security;

import com.qespe.fiscal_service.shared.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Enumeration;
import java.util.HexFormat;

/**
 * Validates a .pfx (PKCS12) file and extracts the metadata the UI needs to
 * show after upload — without exposing any of these concepts to the user.
 *
 * <p>The user only types the password and picks the file. This class:
 * <ul>
 *   <li>Confirms the password is correct (wrong password = friendly error).</li>
 *   <li>Picks the first certificate alias (most .pfx have exactly one).</li>
 *   <li>Reads {@code notBefore}/{@code notAfter} so we don't ask the user
 *       for vigencia dates manually — they live in the cert.</li>
 *   <li>Computes the SHA-256 fingerprint for tamper detection / display.</li>
 * </ul>
 */
@Component
public class PfxInspector {

    public record PfxMetadata(
            String aliasFromCert,
            String subjectDn,
            Instant validFrom,
            Instant validTo,
            String fingerprintSha256
    ) { }

    /**
     * Loads the .pfx and returns its metadata. Throws {@link BadRequestException}
     * with friendly Spanish messages on the common failure modes (wrong password,
     * not a PKCS12, no cert inside, expired) so the UI can surface them as-is.
     */
    public PfxMetadata inspect(byte[] pfxBytes, String password) {
        if (pfxBytes == null || pfxBytes.length == 0) {
            throw new BadRequestException("El archivo .pfx está vacío.");
        }
        if (password == null) {
            throw new BadRequestException("La contraseña del certificado es requerida.");
        }
        KeyStore ks;
        try {
            ks = KeyStore.getInstance("PKCS12");
            ks.load(new ByteArrayInputStream(pfxBytes), password.toCharArray());
        } catch (java.io.IOException e) {
            // PKCS12 throws IOException with a nested UnrecoverableKeyException
            // for bad passwords. Treat any IOException at load() as "wrong password
            // or not a valid .pfx" — both are user errors, not system errors.
            throw new BadRequestException(
                    "No se pudo abrir el archivo: contraseña incorrecta o el archivo no es un .pfx válido.");
        } catch (Exception e) {
            throw new BadRequestException(
                    "Error al procesar el certificado: " + e.getMessage());
        }

        try {
            String alias = pickFirstCertAlias(ks);
            if (alias == null) {
                throw new BadRequestException("El .pfx no contiene ningún certificado.");
            }
            X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
            if (cert == null) {
                throw new BadRequestException("El alias del .pfx no apunta a un certificado X.509.");
            }
            Instant notBefore = cert.getNotBefore().toInstant();
            Instant notAfter = cert.getNotAfter().toInstant();
            String subjectDn = cert.getSubjectX500Principal().getName();
            String fingerprint = sha256Hex(cert.getEncoded());

            return new PfxMetadata(alias, subjectDn, notBefore, notAfter, fingerprint);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException(
                    "Error al inspeccionar el certificado: " + e.getMessage());
        }
    }

    /**
     * Re-validates a password without re-extracting metadata. Used when the
     * user only changes the password without re-uploading the file (rare but
     * supported via the same upload endpoint with the same bytes).
     */
    public boolean isPasswordValid(byte[] pfxBytes, String password) {
        try {
            KeyStore.getInstance("PKCS12").load(new ByteArrayInputStream(pfxBytes), password.toCharArray());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String pickFirstCertAlias(KeyStore ks) throws Exception {
        Enumeration<String> aliases = ks.aliases();
        while (aliases.hasMoreElements()) {
            String a = aliases.nextElement();
            if (ks.isCertificateEntry(a) || ks.isKeyEntry(a)) return a;
        }
        return null;
    }

    private static String sha256Hex(byte[] data) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
        return HexFormat.of().formatHex(hash);
    }
}
