package dev.denfry.promptcritical.sim.refining;

/**
 * Гидроочистка (гидрообессеривание) дизельной фракции и керосина на CoMo/Al₂O₃ в реакторе с неподвижным слоем:
 * R–SH + H₂ → R–H + H₂S, тиофены + 4 H₂ → C₄H₁₀ + H₂S (Gary, Handwerk, «Petroleum Refining», гл. 9; Ахметов,
 * гл. 9; прототип — ЛЧ-24-2000).
 *
 * <p><b>Кинетика.</b> Порядок 1,5 по сере — смесь «лёгких» меркаптанов и сульфидов с «трудными» дибензотиофенами
 * (Ancheyta et al., Catal. Today 98, 2004 — n = 1,4–1,7 для прямогонного газойля):
 * <pre>1/√C_S − 1/√C_S,0 = (k(T)/2)·τ·a·(p_H₂/p_ref)^0,5,  k = k₆₃₃·exp(−E/R·(1/T − 1/633)),</pre>
 * C_S — массовая доля серы, τ = 1/LHSV, k₆₃₃ = 0,1103 (мас.доля)^−0,5·с⁻¹ при p_ref = 6 МПа, E = 110 кДж/моль
 * (GDD 09 §9.9.6 [BALANCE]: дизель 1,2 % S → 50 ppm при LHSV 1,5 ч⁻¹, 360 °C, 6 МПа; LHSV 1 — 23 ppm, LHSV 2 — 85
 * ppm; −20 К — k вдвое меньше, что согласуется с E = 100–130 кДж/моль у Ancheyta).
 *
 * <p><b>Торможение H₂S.</b> Сероводород в циркулирующем газе адсорбируется на тех же центрах: a_H₂S = 1/(1 + 0,5·x),
 * x — H₂S в рецикле, % об. Без аминовой очистки рецикла x растёт, глубина очистки падает.
 *
 * <p><b>Тепло.</b> Насыщение олефинов и ароматики ≈ 120 кДж на моль H₂; для прямогонного дизеля это 10–25 К по слою.
 * Теплота самого гидрогенолиза C–S в ΔT не входит [допущение: она в 3–5 раз меньше при 1 % серы]. Выше
 * {@link #HYDROCRACKING_K} (420 °C) начинается гидрокрекинг — разгон температуры (ПАЗ {@code HDT_HIGH_BED_TEMP}).
 *
 * <p><b>Водород.</b> Химический расход — 3 моль H₂ на моль удалённой серы (средняя стехиометрия меркаптанов (1) и
 * тиофенов (4)) плюс насыщение {@code saturationMolPerKg}; расход на растворение не считается.
 *
 * <p><b>Закоксовывание.</b> dC/dt = k_c·exp(−E_c/R·(1/T − 1/633))·(p_ref/p_H₂), активность a_c = exp(−α·C). При 360 °C
 * и 6 МПа активность падает до 0,5 за {@link #HALF_LIFE_YEARS} года физ. (срок службы CoMo 2–4 года, Gary гл. 9).
 */
public final class Hydrotreating {
    public static final double K633 = 0.1103, E_J_MOL = 110e3, R = 8.314, P_REF_MPA = 6, T_REF_K = 633;
    public static final double H2S_INHIBITION_PER_PCT = 0.5, H2_PER_S = 3, SAT_J_PER_MOL_H2 = 120e3, CP_J_KG_K = 2_800;
    public static final double HYDROCRACKING_K = 420 + 273.15;
    public static final double M_S = 32.06, M_H2S = 34.08, M_H2 = 2.016;
    /** Кокс: α = 1 на единицу условного кокса, полжизни при 633 К и 6 МПа — 3 года. */
    public static final double HALF_LIFE_YEARS = 3, COKE_E_J_MOL = 140e3;
    static final double COKE_RATE_REF = Math.log(2) / (HALF_LIFE_YEARS * 365.25 * 86400);

    /**
     * @param productSulfur  сера в продукте, мас. доля
     * @param conversion     степень обессеривания
     * @param h2sKgPerKg     сероводород на кг сырья
     * @param h2KgPerKg      химический расход водорода на кг сырья
     * @param bedRiseK       подъём температуры по слою
     * @param hydrocracking  слой горячее 420 °C
     */
    public record Result(double productSulfur, double conversion, double h2sKgPerKg, double h2KgPerKg, double bedRiseK, boolean hydrocracking) {
        public double productSulfurPpm() {
            return productSulfur * 1e6;
        }
    }

    private double coke;

    /** Константа скорости HDS, (мас.доля)^−0,5·с⁻¹. */
    public static double rateConstant(double tK) {
        return K633 * Math.exp(-E_J_MOL / R * (1 / tK - 1 / T_REF_K));
    }

    /** Множитель торможения сероводородом в рецикле, x — % об. */
    public static double h2sInhibition(double h2sPct) {
        return 1 / (1 + H2S_INHIBITION_PER_PCT * Math.max(0, h2sPct));
    }

    /**
     * Сера на выходе по уравнению порядка 1,5.
     *
     * @param feedSulfur массовая доля серы в сырье
     * @param lhsvPerH   объёмная скорость, ч⁻¹
     * @param tK         средняя температура слоя
     * @param ph2MPa     парциальное давление водорода
     * @param activity   активность катализатора 0…1 (включая торможение H₂S)
     */
    public static double outletSulfur(double feedSulfur, double lhsvPerH, double tK, double ph2MPa, double activity) {
        if (feedSulfur <= 0) return 0;
        double tau = 3600 / Math.max(1e-6, lhsvPerH);
        double rhs = rateConstant(tK) / 2 * tau * Math.max(0, activity) * Math.sqrt(Math.max(0, ph2MPa) / P_REF_MPA);
        double inv = 1 / Math.sqrt(feedSulfur) + rhs;
        return 1 / (inv * inv);
    }

    /**
     * Один проход сырья.
     *
     * @param inletK            температура на входе в слой
     * @param saturationMolPerKg водород на насыщение олефинов и ароматики, моль/кг (прямогонный дизель ≈ 0,3)
     * @param h2sPct            H₂S в циркулирующем газе, % об.
     */
    public Result treat(double feedSulfur, double lhsvPerH, double inletK, double ph2MPa, double saturationMolPerKg, double h2sPct) {
        double rise = saturationMolPerKg * SAT_J_PER_MOL_H2 / CP_J_KG_K;
        double meanK = inletK + rise / 2;
        double a = activity() * h2sInhibition(h2sPct);
        double out = outletSulfur(feedSulfur, lhsvPerH, meanK, ph2MPa, a);
        double removedMol = (feedSulfur - out) * 1000 / M_S;
        double h2Mol = removedMol * H2_PER_S + saturationMolPerKg;
        return new Result(out, feedSulfur > 0 ? 1 - out / feedSulfur : 0, removedMol * M_H2S / 1000, h2Mol * M_H2 / 1000, rise,
                inletK + rise > HYDROCRACKING_K);
    }

    /** Закоксовывание за {@code dtS} физических секунд при температуре слоя и давлении водорода. */
    public void age(double dtS, double bedK, double ph2MPa) {
        double rate = COKE_RATE_REF * Math.exp(-COKE_E_J_MOL / R * (1 / bedK - 1 / T_REF_K)) * P_REF_MPA / Math.max(0.5, ph2MPa);
        coke += rate * Math.max(0, dtS);
    }

    /** Активность по коксу. */
    public double activity() {
        return Math.exp(-coke);
    }

    public double coke() {
        return coke;
    }

    public void setCoke(double c) {
        coke = Math.max(0, c);
    }

    /** Свежая загрузка катализатора. */
    public void loadCatalyst() {
        coke = 0;
    }
}
