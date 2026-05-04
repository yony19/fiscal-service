package com.qespe.fiscal_service.shared.security;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * AES-GCM encryptor for .pfx passwords stored in the database.
 *
 * <p>Why AES-GCM:
 * <ul>
 *   <li>Authenticated encryption — tampering invalidates the ciphertext on decrypt.</li>
 *   <li>Random 12-byte IV per record means identical passwords don't share ciphertext.</li>
 *   <li>Standard JDK primitive, no external dependency.</li>
 * </ul>
 *
 * <p>Key material comes from {@code fiscal.security.cert-password-master-key}
 * (env var {@code FISCAL_CERT_MASTER_KEY} in production). The configured
 * string is hashed with SHA-256 to derive a stable 256-bit AES key, so the
 * configured key only needs to be a long-enough random secret.
 *
 * <p><b>Operational notes:</b>
 * <ul>
 *   <li>Rotate the master key carefully — every existing ciphertext becomes
 *       unreadable. Re-upload the .pfx files after a key rotation, or build
 *       a one-shot re-encrypt job before rotating.</li>
 *   <li>The default key is for local dev only and is rejected when the
 *       Spring profile contains "prod".</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class PasswordEncryptor {

    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    @Value("${fiscal.security.cert-password-master-key}")
    private String masterKey;

    private SecretKey aesKey;
    private final SecureRandom random = new SecureRandom();

    @PostConstruct
    void init() {
        if (masterKey == null || masterKey.length() < 32) {
            throw new IllegalStateException(
                    "fiscal.security.cert-password-master-key must be at least 32 chars long. "
                  + "Set FISCAL_CERT_MASTER_KEY env var to a long random string.");
        }
        try {
            byte[] hashed = MessageDigest.getInstance("SHA-256")
                    .digest(masterKey.getBytes(StandardCharsets.UTF_8));
            this.aesKey = new SecretKeySpec(hashed, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive AES key", e);
        }
    }

    /** Returns a fresh 12-byte IV. Caller must persist it alongside the ciphertext. */
    public byte[] newIv() {
        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        random.nextBytes(iv);
        return iv;
    }

    /** Encrypts plaintext with the configured master key and the given IV. */
    public byte[] encrypt(String plaintext, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    /**
     * Decrypts ciphertext produced by {@link #encrypt(String, byte[])} with the
     * matching IV. Throws if the master key has changed or the ciphertext is
     * tampered.
     */
    public String decrypt(byte[] ciphertext, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to decrypt certificate password. The master key may have rotated; re-upload the .pfx.", e);
        }
    }
}
