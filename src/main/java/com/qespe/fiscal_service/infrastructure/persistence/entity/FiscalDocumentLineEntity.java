package com.qespe.fiscal_service.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "fiscal_document_line")
public class FiscalDocumentLineEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiscal_document_id", nullable = false)
    private FiscalDocumentEntity fiscalDocument;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "item_id", length = 100)
    private String itemId;

    @Column(name = "item_code", length = 100)
    private String itemCode;

    @Column(name = "sku", length = 100)
    private String sku;

    @Column(name = "barcode", length = 100)
    private String barcode;

    @Column(name = "item_sunat_code", length = 30)
    private String itemSunatCode;

    @Column(name = "description", nullable = false, length = 1000)
    private String description;

    @Column(name = "unit_code", length = 10)
    private String unitCode;

    @Column(name = "unit_name", length = 50)
    private String unitName;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 6)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 6)
    private BigDecimal unitPrice;

    @Column(name = "unit_value", precision = 18, scale = 6)
    private BigDecimal unitValue;

    @Column(name = "tax_affectation_code", length = 10)
    private String taxAffectationCode;

    @Column(name = "igv_rate", precision = 7, scale = 4)
    private BigDecimal igvRate;

    @Column(name = "isc_rate", precision = 7, scale = 4)
    private BigDecimal iscRate;

    @Column(name = "discount_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "taxable_base_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal taxableBaseAmount;

    @Column(name = "tax_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "line_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal lineTotal;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata")
    private Map<String, Object> metadata = new HashMap<>();

    @Column(name = "created_by", nullable = false)
    private String createdBy = "system";
}

