package com.qespe.fiscal_service.core.domain.enums;

public enum FiscalDocumentType {
    INVOICE,
    RECEIPT,
    CREDIT_NOTE,
    DEBIT_NOTE,
    VOID,
    DAILY_SUMMARY,
    // Comprobante de Retencion (catalogo 01 = "20"). Comprobante sincrono (sendBill -> CDR),
    // raiz UBL propia sunat:Retention.
    RETENTION,
    // Comprobante de Percepcion (catalogo 01 = "40"). Comprobante sincrono (sendBill -> CDR),
    // raiz UBL propia sunat:Perception.
    PERCEPTION
}

