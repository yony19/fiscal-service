package com.qespe.fiscal_service.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "fiscal_event")
public class FiscalEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiscal_document_id", nullable = false)
    private FiscalDocumentEntity fiscalDocument;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload")
    private Map<String, Object> payload = new HashMap<>();

    @Column(name = "message")
    private String message;

    @Column(name = "created_by", nullable = false)
    private String createdBy = "system";

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private java.time.Instant createdAt;
}

