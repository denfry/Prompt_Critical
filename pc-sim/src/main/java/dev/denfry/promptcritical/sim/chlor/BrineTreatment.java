package dev.denfry.promptcritical.sim.chlor;

/**
 * Приготовление и очистка рассола для диафрагменного электролиза (O'Brien et al., «Handbook of Chlor-Alkali
 * Technology», т. II, гл. 7; Якименко Л. М., «Производство хлора, каустической соды и неорганических хлорпродуктов»,
 * 1974). Соль растворяют в воде до насыщения — 310 г/л NaCl при 60 °C. Каменная соль несёт 0,3 % CaSO₄ и 0,05 % MgCl₂:
 * в рассоле это ≈ 270 мг/л Ca и ≈ 40 мг/л Mg. Кальций осаждают содой (CaCO₃), магний — щёлочью (Mg(OH)₂), осадок
 * отстаивают в осветлителе и фильтруют. Соду получают на месте, пропуская дымовые газы (≈ 12 % CO₂) через часть щёлочи:
 * 2 NaOH + CO₂ → Na₂CO₃ + H₂O. Нормы — избыток реагентов 0,3–0,5 г/л соды и 0,1–0,3 г/л NaOH; очищенный рассол
 * диафрагменных ванн — Ca + Mg ≤ 5 мг/л.
 */
public final class BrineTreatment {
    public static final double NACL_KG_PER_L = DiaphragmCellRoom.BRINE_NACL_KG_PER_L;
    /** Вода на литр рассола, кг (1,20 кг/л − 0,31 кг NaCl). */
    public static final double WATER_KG_PER_L = DiaphragmCellRoom.BRINE_KG_PER_L - NACL_KG_PER_L;
    /** Примеси соли, массовые доли: Ca (из CaSO₄) и Mg (из MgCl₂). */
    public static final double SALT_CA = 0.003 * 40.078 / 136.14, SALT_MG = 0.0005 * 24.305 / 95.21;
    /** Жёсткость сырого рассола, мг/л. */
    public static final double RAW_HARDNESS_MG_L = (SALT_CA + SALT_MG) * NACL_KG_PER_L * 1e6;
    /** Жёсткость очищенного рассола при полной дозе реагентов, мг/л. */
    public static final double TREATED_HARDNESS_MG_L = 2;
    /** Щёлочь на литр рассола, кг: на соду для Ca (с избытком 0,4 г/л Na₂CO₃) и на Mg (с избытком 0,2 г/л). */
    public static final double NAOH_KG_PER_L = 2 * 0.039997 * (SALT_CA * NACL_KG_PER_L / 0.040078 + 0.4e-3 / 0.10599)
            + 2 * 0.039997 * SALT_MG * NACL_KG_PER_L / 0.024305 + 0.2e-3;
    /** CO₂ на литр рассола, кг (на соду). */
    public static final double CO2_KG_PER_L = 0.044010 * (SALT_CA * NACL_KG_PER_L / 0.040078 + 0.4e-3 / 0.10599);

    private BrineTreatment() {
    }

    /**
     * Жёсткость очищенного рассола при доле нормы реагентов: недостача соды или щёлочи оставляет Ca и Mg в растворе
     * пропорционально.
     */
    public static double hardnessMgL(double reagentFraction) {
        double f = Math.clamp(reagentFraction, 0, 1);
        return TREATED_HARDNESS_MG_L + (RAW_HARDNESS_MG_L - TREATED_HARDNESS_MG_L) * (1 - f);
    }

    /** Литры рассола из кг соли. */
    public static double brineFromSaltL(double saltKg) {
        return saltKg / NACL_KG_PER_L;
    }
}
