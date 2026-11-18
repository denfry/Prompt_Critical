package dev.denfry.promptcritical.sim.nonferrous;

/**
 * Флотация сульфидных руд {@code flotation_cell} (08 §8.8.3): собиратель и вспениватель (крезолы из каменноугольной
 * смолы 08), извлечение {@value #RECOVERY} (§8.8.3: 85–92 %). Вспениватель — 50 г/т руды (Wills, «Mineral Processing
 * Technology», гл. 12: 5–100 г/т), его носит смола с {@value #TAR_CRESOLS} фенолов [BALANCE]; подпиточная вода —
 * {@value #WATER_L_PER_KG} л/кг руды [BALANCE: оборот воды 80–90 %]. Термохимии нет, шаг — физические секунды SLOW.
 */
public final class Flotation {
    public static final double RECOVERY = 0.88;
    public static final double FROTHER_KG_PER_KG = 50e-6, TAR_CRESOLS = 0.03, TAR_KG_L = 1.17;
    public static final double WATER_L_PER_KG = 0.5;
    /** Переработка руды, кг/с физ. [BALANCE]. */
    public static final double ORE_KG_S = 0.5;
    /** Платиноиды, кг на кг Ni в руде пентландитовой зоны: 7,5 г/т (§8.8.4: 5–10 г/т) при Ni 1,7 % (жила, 08 §8.1.2). */
    public static final double PGM_PER_NI = 7.5e-6 / 0.017;
    /** Кобальт на кг Ni: Co = 0,035·g_Ni (08 §8.1.3). */
    public static final double CO_PER_NI = 0.035;

    private Flotation() {
    }

    /** Смола на кг руды, л. */
    public static double tarLitresPerKg() {
        return FROTHER_KG_PER_KG / TAR_CRESOLS / TAR_KG_L;
    }

    /** Металл в концентрат с кг руды содержания {@code grade}, кг. */
    public static double metalToConcentrate(double grade) {
        return Math.max(0, grade) * RECOVERY;
    }
}
