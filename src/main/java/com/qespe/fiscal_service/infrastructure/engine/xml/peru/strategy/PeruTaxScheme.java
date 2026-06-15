package com.qespe.fiscal_service.infrastructure.engine.xml.peru.strategy;

/**
 * Mapea el tipo de afectacion del IGV (Catalogo SUNAT 07, que viaja en cada
 * linea como taxAffectationCode) al tributo correspondiente (Catalogo SUNAT 05:
 * id + nombre + codigo UN/ECE 5153) que se escribe en cac:TaxScheme.
 *
 * <p>Antes el TaxScheme estaba cableado a IGV (1000/IGV/VAT) para TODAS las
 * lineas, asi que el motor solo soportaba ventas gravadas. Con este mapeo,
 * exonerado, inafecto, exportacion y gratuitas salen con su tributo correcto.
 */
public final class PeruTaxScheme {

    /** Catalogo 05 id (1000 IGV, 9997 EXO, 9998 INA, 9995 EXP, 9996 GRA, 1016 IVAP). */
    public final String id;
    /** Nombre del tributo. */
    public final String name;
    /** Codigo de tipo de tributo UN/ECE 5153 (VAT, FRE, EXC). */
    public final String typeCode;

    private PeruTaxScheme(String id, String name, String typeCode) {
        this.id = id;
        this.name = name;
        this.typeCode = typeCode;
    }

    private static final PeruTaxScheme IGV  = new PeruTaxScheme("1000", "IGV",  "VAT");
    private static final PeruTaxScheme IVAP = new PeruTaxScheme("1016", "IVAP", "VAT");
    private static final PeruTaxScheme EXO  = new PeruTaxScheme("9997", "EXO",  "VAT");
    private static final PeruTaxScheme INA  = new PeruTaxScheme("9998", "INA",  "FRE");
    private static final PeruTaxScheme EXP  = new PeruTaxScheme("9995", "EXP",  "FRE");
    private static final PeruTaxScheme GRA  = new PeruTaxScheme("9996", "GRA",  "FRE");

    /** Tributo gravado por defecto (caso mas comun y fallback seguro). */
    public static PeruTaxScheme defaultScheme() {
        return IGV;
    }

    /**
     * @param affectationCode codigo del Catalogo 07 (ej. "10" gravado, "20"
     *                        exonerado, "30" inafecto, "40" exportacion, "11"-"16"
     *                        gravado gratuito, "31"-"36" inafecto gratuito).
     */
    public static PeruTaxScheme forAffectation(String affectationCode) {
        if (affectationCode == null || affectationCode.isBlank()) {
            return IGV; // sin dato -> gravado (el caso por defecto del POS)
        }
        String c = affectationCode.trim();
        return switch (c) {
            case "10" -> IGV;
            case "17" -> IVAP;
            case "20" -> EXO;
            case "30" -> INA;
            case "40" -> EXP;
            // Transferencias gratuitas (gravado/exonerado/inafecto gratuito) -> GRA.
            case "11", "12", "13", "14", "15", "16", "21",
                 "31", "32", "33", "34", "35", "36" -> GRA;
            default -> IGV; // codigos no contemplados: gravado por seguridad
        };
    }
}
