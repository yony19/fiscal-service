package com.qespe.fiscal_service.infrastructure.persistence.entity;

import com.qespe.fiscal_service.core.domain.enums.FiscalEmitterStatus;
import com.qespe.fiscal_service.core.domain.enums.FiscalEnvironment;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "fiscal_emitter_config")
public class FiscalEmitterConfigEntity extends AuditableEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "tax_authority_code", nullable = false, length = 20)
    private String taxAuthorityCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "environment", nullable = false, length = 10)
    private FiscalEnvironment environment;

    @Column(name = "provider_code", length = 40)
    private String providerCode;

    @Column(name = "document_type", nullable = false, length = 10)
    private String documentType;

    @Column(name = "document_number", nullable = false, length = 30)
    private String documentNumber;

    @Column(name = "legal_name", nullable = false, length = 255)
    private String legalName;

    @Column(name = "trade_name", length = 255)
    private String tradeName;

    @Column(name = "fiscal_address", nullable = false, length = 500)
    private String fiscalAddress;

    @Column(name = "ubigeo", length = 20)
    private String ubigeo;

    @Column(name = "district", length = 120)
    private String district;

    @Column(name = "city", length = 120)
    private String city;

    @Column(name = "state", length = 120)
    private String state;

    @Column(name = "country_name", length = 120)
    private String countryName;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "phone", length = 60)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FiscalEmitterStatus status;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;
}
