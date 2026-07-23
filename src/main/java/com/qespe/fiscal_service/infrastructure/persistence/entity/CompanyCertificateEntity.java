package com.qespe.fiscal_service.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "company_certificate")
public class CompanyCertificateEntity extends AuditableEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "provider_code", nullable = false, length = 40)
    private String providerCode;

    /**
     * FK real al Proveedor específico (fiscal_provider_config) al que
     * pertenece este certificado — resuelve la ambigüedad TEST/PROD que
     * {@link #providerCode} (texto) no puede distinguir por sí solo. Null
     * = certificado sin vincular (no elegible para firmar, ver
     * CertificateResolutionService). Ver openspec/changes/certificate-provider-binding.
     */
    @Column(name = "provider_id")
    private UUID providerId;

    @Column(name = "alias", nullable = false, length = 80)
    private String alias;

    /**
     * Nullable since V11: a certificate row can exist as metadata-only before
     * the user uploads the .pfx. Once uploaded, this is set to
     * {@code INLINE_ENCRYPTED} automatically and never goes back to null.
     */
    @Column(name = "storage_mode", length = 20)
    private String storageMode;

    @Column(name = "certificate_path")
    private String certificatePath;

    @Column(name = "certificate_data")
    private byte[] certificateData;

    @Column(name = "private_key_path")
    private String privateKeyPath;

    @Column(name = "private_key_data")
    private byte[] privateKeyData;

    @Column(name = "secret_ref", length = 200)
    private String secretRef;

    /** Legacy reference. New uploads use {@link #passwordEncrypted} instead. */
    @Column(name = "password_secret_ref", length = 200)
    private String passwordSecretRef;

    /** AES-GCM ciphertext of the .pfx password. Paired with {@link #passwordIv}. */
    @Column(name = "password_encrypted")
    private byte[] passwordEncrypted;

    /** GCM nonce / IV for {@link #passwordEncrypted}. 12 bytes. */
    @Column(name = "password_iv")
    private byte[] passwordIv;

    @Column(name = "fingerprint_sha256", length = 64)
    private String fingerprintSha256;

    /** Original filename of the uploaded .pfx, for friendly UI display. */
    @Column(name = "upload_filename", length = 255)
    private String uploadFilename;

    /** When the .pfx was last uploaded. Null = never uploaded yet. */
    @Column(name = "uploaded_at")
    private Instant uploadedAt;

    /** Nullable since V11: extracted from cert on upload, null when metadata-only. */
    @Column(name = "valid_from")
    private Instant validFrom;

    /** Nullable since V11: extracted from cert on upload, null when metadata-only. */
    @Column(name = "valid_to")
    private Instant validTo;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;
}

