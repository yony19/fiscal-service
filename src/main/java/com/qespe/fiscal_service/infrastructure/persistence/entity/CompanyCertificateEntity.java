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

    @Column(name = "alias", nullable = false, length = 80)
    private String alias;

    @Column(name = "storage_mode", nullable = false, length = 20)
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

    @Column(name = "password_secret_ref", length = 200)
    private String passwordSecretRef;

    @Column(name = "fingerprint_sha256", length = 64)
    private String fingerprintSha256;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_to", nullable = false)
    private Instant validTo;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;
}

