package com.qespe.fiscal_service.infrastructure.engine.xml.peru.util;

public final class PeruUblNamespaces {

    private PeruUblNamespaces() {
    }

    public static final String UBL_INVOICE = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
    public static final String UBL_CREDIT_NOTE = "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2";
    public static final String UBL_DEBIT_NOTE = "urn:oasis:names:specification:ubl:schema:xsd:DebitNote-2";

    // Documentos resumen SUNAT (async, basados en ticket): Comunicacion de Baja (RA)
    // y Resumen Diario de Boletas (RC). Usan esquemas UBL propios de SUNAT.
    public static final String UBL_VOIDED = "urn:sunat:names:specification:ubl:peru:schema:xsd:VoidedDocuments-1";
    public static final String UBL_SUMMARY = "urn:sunat:names:specification:ubl:peru:schema:xsd:SummaryDocuments-1";

    public static final String EXT = "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2";
    public static final String CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    public static final String CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";

    /** SUNAT Aggregate Components — usado por RA/RC (sac:VoidedDocumentsLine, sac:SummaryDocumentsLine, etc.). */
    public static final String SAC = "urn:sunat:names:specification:ubl:peru:schema:xsd:SunatAggregateComponents-1";
    /** XML Digital Signature namespace — RA/RC lo declaran en la raiz. */
    public static final String DS = "http://www.w3.org/2000/09/xmldsig#";
}
