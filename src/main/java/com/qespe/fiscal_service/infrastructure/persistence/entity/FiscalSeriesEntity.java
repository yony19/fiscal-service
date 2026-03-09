package com.qespe.fiscal_service.infrastructure.persistence.entity;

import com.qespe.fiscal_service.core.domain.enums.FiscalEnvironment;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "fiscal_series")
public class FiscalSeriesEntity extends AuditableEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "tax_authority_code", nullable = false, length = 20)
    private String taxAuthorityCode;

    @Column(name = "document_type_code", nullable = false, length = 10)
    private String documentTypeCode;

    @Column(name = "series", nullable = false, length = 10)
    private String series;

    @Column(name = "next_number", nullable = false)
    private Long nextNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "environment", nullable = false, length = 10)
    private FiscalEnvironment environment;

    @Column(name = "is_active", nullable = false)
    private Boolean active;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata")
    private Map<String, Object> metadata = new HashMap<>();

    @Column(name = "version", nullable = false)
    private Long version;
}

