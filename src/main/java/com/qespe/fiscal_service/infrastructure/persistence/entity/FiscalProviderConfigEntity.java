package com.qespe.fiscal_service.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "fiscal_provider_config")
public class FiscalProviderConfigEntity extends AuditableEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "tax_authority_code", nullable = false, length = 20)
    private String taxAuthorityCode;

    @Column(name = "provider_code", nullable = false, length = 40)
    private String providerCode;

    @Column(name = "environment", nullable = false, length = 10)
    private String environment;

    @Column(name = "is_active", nullable = false)
    private Boolean active;

    @Column(name = "priority", nullable = false)
    private Integer priority;

    @Column(name = "endpoint_submit_url")
    private String endpointSubmitUrl;

    @Column(name = "endpoint_status_url")
    private String endpointStatusUrl;

    @Column(name = "endpoint_cdr_url")
    private String endpointCdrUrl;

    @Column(name = "auth_type", nullable = false, length = 20)
    private String authType;

    @Column(name = "credential_ref", length = 200)
    private String credentialRef;

    @Column(name = "timeout_ms", nullable = false)
    private Integer timeoutMs;

    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries;

    @Column(name = "retry_backoff_ms", nullable = false)
    private Integer retryBackoffMs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_json")
    private Map<String, Object> configJson = new HashMap<>();
}

