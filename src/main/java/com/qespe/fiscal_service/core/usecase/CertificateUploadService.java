package com.qespe.fiscal_service.core.usecase;

import com.qespe.fiscal_service.core.dto.certificate.CompanyCertificateResponse;
import com.qespe.fiscal_service.core.port.in.CertificateUploadUseCase;
import com.qespe.fiscal_service.core.port.out.CompanyCertificateRepositoryPort;
import com.qespe.fiscal_service.infrastructure.mapper.CompanyCertificateMapper;
import com.qespe.fiscal_service.infrastructure.persistence.entity.CompanyCertificateEntity;
import com.qespe.fiscal_service.shared.exception.NotFoundException;
import com.qespe.fiscal_service.shared.security.PasswordEncryptor;
import com.qespe.fiscal_service.shared.security.PfxInspector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Friendly upload pipeline. The user picks a .pfx + types the password and
 * we do the rest:
 *
 * <ol>
 *   <li>Validate the password by attempting to load the PKCS12 keystore.</li>
 *   <li>Extract validFrom / validTo / fingerprint / alias from the X.509 cert
 *       so the user never types vigencia dates by hand.</li>
 *   <li>Encrypt the password with AES-GCM (per-record IV) so it's never at
 *       rest in plaintext.</li>
 *   <li>Persist the .pfx bytes inline (storage_mode = INLINE_ENCRYPTED).</li>
 *   <li>Audit columns (uploaded_at, upload_filename) so the UI can show
 *       "Subido: cert.pfx, hace 2 días" without exposing internals.</li>
 * </ol>
 *
 * <p>Why this is a separate service from {@link CompanyCertificateService}:
 * the upload flow handles binary + crypto and is invoked from a multipart
 * endpoint. Keeping it apart from the metadata CRUD makes the JSON path
 * trivially "no bytes ever".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateUploadService implements CertificateUploadUseCase {

    private static final String INLINE_ENCRYPTED = "INLINE_ENCRYPTED";

    private final CompanyCertificateRepositoryPort repository;
    private final CompanyCertificateMapper mapper;
    private final PfxInspector pfxInspector;
    private final PasswordEncryptor passwordEncryptor;

    @Override
    @Transactional
    public CompanyCertificateResponse uploadPfx(UUID certificateId, byte[] pfxBytes, String password, String filename) {
        CompanyCertificateEntity entity = repository.findById(certificateId)
                .orElseThrow(() -> new NotFoundException("Certificado no encontrado: " + certificateId));

        // Step 1+2: validate + extract metadata. Throws BadRequestException with
        // friendly Spanish messages if the password is wrong or the file isn't
        // a real .pfx — those bubble up to the @ControllerAdvice as 400s.
        PfxInspector.PfxMetadata meta = pfxInspector.inspect(pfxBytes, password);

        // Step 3: encrypt the password.
        byte[] iv = passwordEncryptor.newIv();
        byte[] cipher = passwordEncryptor.encrypt(password, iv);

        // Step 4+5: persist bytes + extracted metadata.
        entity.setCertificateData(pfxBytes);
        // PKCS12 keeps cert + private key in the same blob, so we don't need
        // a separate privateKeyData. Null it out so RealXmlDigitalSigner
        // knows to read both from certificateData.
        entity.setPrivateKeyData(null);
        entity.setPrivateKeyPath(null);
        entity.setCertificatePath(null);
        entity.setSecretRef(null);
        entity.setStorageMode(INLINE_ENCRYPTED);
        entity.setPasswordEncrypted(cipher);
        entity.setPasswordIv(iv);
        // Clear legacy field if it was set previously.
        entity.setPasswordSecretRef(null);

        entity.setFingerprintSha256(meta.fingerprintSha256());
        entity.setValidFrom(meta.validFrom());
        entity.setValidTo(meta.validTo());

        entity.setUploadFilename(filename);
        entity.setUploadedAt(Instant.now());

        // If the user hadn't set a status yet, default to ACTIVE since we now
        // have a usable cert. Don't override an explicit REVOKED.
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            entity.setStatus("ACTIVE");
        }

        log.info("[cert-upload] certId={} alias={} validTo={} fingerprint={}",
                certificateId, meta.aliasFromCert(), meta.validTo(), meta.fingerprintSha256());

        return mapper.toResponse(repository.save(entity));
    }
}
