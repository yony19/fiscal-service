package com.qespe.fiscal_service.infrastructure.persistence.entity;

import com.qespe.fiscal_service.core.domain.enums.FiscalDocumentStatus;
import com.qespe.fiscal_service.core.domain.enums.FiscalEnvironment;
import com.qespe.fiscal_service.core.domain.enums.FiscalProcessingStage;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "fiscal_document")
public class FiscalDocumentEntity extends AuditableEntity {

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

    @Column(name = "source_service", nullable = false, length = 60)
    private String sourceService;

    @Column(name = "source_id", nullable = false, length = 100)
    private String sourceId;

    @Column(name = "source_code", length = 100)
    private String sourceCode;

    @Column(name = "source_event_type", length = 40)
    private String sourceEventType;

    @Column(name = "source_event_id", length = 120)
    private String sourceEventId;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "request_fingerprint_sha256", length = 64)
    private String requestFingerprintSha256;

    @Column(name = "document_type", nullable = false, length = 30)
    private String documentType;

    @Column(name = "document_type_code", nullable = false, length = 10)
    private String documentTypeCode;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "issue_time")
    private LocalTime issueTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id")
    private FiscalSeriesEntity seriesRef;

    @Column(name = "series", nullable = false, length = 10)
    private String series;

    @Column(name = "number", nullable = false)
    private Long number;

    @Column(name = "full_number", nullable = false, length = 30)
    private String fullNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_document_id")
    private FiscalDocumentEntity relatedDocument;

    @Column(name = "related_document_type_code", length = 10)
    private String relatedDocumentTypeCode;

    @Column(name = "related_document_number", length = 30)
    private String relatedDocumentNumber;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "exchange_rate", precision = 18, scale = 6)
    private BigDecimal exchangeRate;

    @Column(name = "emitter_document_type", nullable = false, length = 10)
    private String emitterDocumentType;

    @Column(name = "emitter_document_number", nullable = false, length = 30)
    private String emitterDocumentNumber;

    @Column(name = "emitter_legal_name", nullable = false, length = 255)
    private String emitterLegalName;

    @Column(name = "emitter_trade_name", length = 255)
    private String emitterTradeName;

    @Column(name = "emitter_address", nullable = false, length = 500)
    private String emitterAddress;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "customer_document_type", length = 10)
    private String customerDocumentType;

    @Column(name = "customer_document_number", length = 30)
    private String customerDocumentNumber;

    @Column(name = "customer_name", nullable = false, length = 255)
    private String customerName;

    @Column(name = "customer_address", length = 500)
    private String customerAddress;

    @Column(name = "customer_email", length = 255)
    private String customerEmail;

    @Column(name = "taxable_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal taxableAmount;

    @Column(name = "exempt_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal exemptAmount;

    @Column(name = "unaffected_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal unaffectedAmount;

    @Column(name = "free_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal freeAmount;

    @Column(name = "discount_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal discountTotal;

    @Column(name = "charge_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal chargeTotal;

    @Column(name = "tax_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "igv_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal igvAmount;

    @Column(name = "isc_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal iscAmount;

    @Column(name = "other_tax_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal otherTaxAmount;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 25)
    private FiscalDocumentStatus status;

    @Column(name = "xml_path")
    private String xmlPath;

    @Column(name = "cdr_path")
    private String cdrPath;

    @Column(name = "zip_path")
    private String zipPath;

    @Column(name = "response_path")
    private String responsePath;

    @Column(name = "xml_hash", length = 128)
    private String xmlHash;

    @Column(name = "signed_xml_path")
    private String signedXmlPath;

    @Column(name = "signed_xml_hash", length = 128)
    private String signedXmlHash;

    @Column(name = "zip_hash", length = 128)
    private String zipHash;

    @Column(name = "cdr_hash", length = 128)
    private String cdrHash;

    @Column(name = "response_hash", length = 128)
    private String responseHash;

    @Column(name = "qr_data")
    private String qrData;

    @Column(name = "provider_code", length = 40)
    private String providerCode;

    @Column(name = "send_attempt_count", nullable = false)
    private Integer sendAttemptCount;

    @Column(name = "error_code", length = 60)
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "retryable_error", nullable = false)
    private Boolean retryableError;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_failed_stage", length = 40)
    private FiscalProcessingStage lastFailedStage;

    @Column(name = "last_error_at")
    private Instant lastErrorAt;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "authority_ticket", length = 120)
    private String authorityTicket;

    @Column(name = "authority_status_code", length = 40)
    private String authorityStatusCode;

    @Column(name = "authority_status_message")
    private String authorityStatusMessage;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @OneToMany(mappedBy = "fiscalDocument", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FiscalDocumentLineEntity> lines = new ArrayList<>();
}

