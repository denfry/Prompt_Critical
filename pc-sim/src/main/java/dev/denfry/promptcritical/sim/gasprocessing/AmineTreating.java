package dev.denfry.promptcritical.sim.gasprocessing;

/**
 * Аминовая очистка газа раствором метилдиэтаноламина (GDD 09 §9.10.3): абсорбер при 4–8 МПа и 35–50 °C поглощает H₂S
 * и часть CO₂, десорбер с ребойлером (0,15–0,2 МПа, куб 120–125 °C) отдаёт кислый газ на установку Клауса.
 *
 * <p><b>Циркуляция</b> (§9.10.3, тест T9.37): Q = ṅ_кисл/(Δα·C_ам), C_ам — молярная концентрация амина
 * (45 % МДЭА, ρ = 1040 кг/м³, M = 119,16 → 3,93 кмоль/м³), Δα — разность загрузок насыщенного и регенерированного
 * амина. Пример: 1 млн нм³/сут газа с 2 % H₂S, Δα = 0,44 → 21,5 м³/ч.
 *
 * <p><b>Абсорбер.</b> Реакция H₂S с третичным амином быстрая — поглощение ограничено ёмкостью раствора: насыщенный
 * амин не берёт больше α_max = {@value #ALPHA_RICH_MAX} моль/моль (§9.10.3: 0,45–0,50). Пока ёмкости хватает, газ
 * очищается до равновесия над регенерированным амином на верхней тарелке:
 * y* = {@value #Y_LEAN_REF_PPM} ppm·(α_lean/{@value #ALPHA_LEAN_REF})² [BALANCE: при α_lean 0,01 газ проходит норму GPA
 * 4 ppm с запасом; квадратичная зависимость давления H₂S от загрузки при малых загрузках — по форме кривых Jou, Mather,
 * Otto, «сверить»]. Не хватает циркуляции — излишек H₂S проскакивает. CO₂ реагирует медленно: эффективность его
 * поглощения {@value #CO2_EFFICIENCY} (§9.10.3: 0,1–0,2) — селективность к H₂S.
 *
 * <p><b>Регенерация.</b> Ребойлер 110–140 кг пара на м³ раствора даёт регенерированную загрузку 0,005–0,01
 * (§9.10.3): α_lean = {@value #ALPHA_LEAN_REF}·({@value #STEAM_REF_KG_M3}/s)² [BALANCE: 140 кг/м³ → 0,0062].
 * Теплота пара — {@value #STEAM_LATENT_J_KG} Дж/кг (пример §9.10.3: 2,6 т/ч ≈ 1,6 МВт).
 */
public final class AmineTreating {
    public static final double MDEA_MASS_FRAC = 0.45, SOLUTION_KG_M3 = 1040, M_MDEA = 119.16;
    /** Молярная концентрация амина, кмоль/м³ (= моль/л). */
    public static final double C_AMINE = MDEA_MASS_FRAC * SOLUTION_KG_M3 / M_MDEA;
    public static final double ALPHA_RICH_MAX = 0.50, ALPHA_LEAN_REF = 0.01, STEAM_REF_KG_M3 = 110, STEAM_LATENT_J_KG = 2.2e6;
    public static final double Y_LEAN_REF_PPM = 2, CO2_EFFICIENCY = 0.15;
    /** Нормальный молярный объём, нм³/кмоль. */
    public static final double NM3_PER_KMOL = 22.414;
    public static final double M_H2S = 34.08;
    /** Норма H₂S в товарном газе: 20 мг/м³ (ГОСТ 5542-2014) — ≈ 13 ppm; GPA — 4 ppm. */
    public static final double SPEC_PPM_GOST = 20 / (M_H2S / NM3_PER_KMOL * 1000), SPEC_PPM_GPA = 4;

    private AmineTreating() {
    }

    /** Циркуляция амина, м³/ч, для потока кислого газа {@code acidKmolH} при разности загрузок {@code deltaAlpha}. */
    public static double circulationM3h(double acidKmolH, double deltaAlpha) {
        return acidKmolH / (deltaAlpha * C_AMINE);
    }

    /** Регенерированная загрузка при расходе пара {@code steamKgM3} на м³ раствора. */
    public static double leanLoading(double steamKgM3) {
        double s = Math.max(1, steamKgM3);
        return Math.min(ALPHA_RICH_MAX, ALPHA_LEAN_REF * (STEAM_REF_KG_M3 / s) * (STEAM_REF_KG_M3 / s));
    }

    /**
     * @param sweetH2sPpm  H₂S в очищенном газе, ppm (мольн.)
     * @param h2sKmolH     поглощённый H₂S, кмоль/ч
     * @param co2KmolH     поглощённый CO₂, кмоль/ч
     * @param richLoading  загрузка насыщенного амина
     * @param leanLoading  загрузка регенерированного амина
     * @param acidPurity   доля H₂S в кислом газе (сухом)
     * @param reboilerW    тепло ребойлера, Вт
     * @param saturated    амину не хватает ёмкости — H₂S проскакивает
     */
    public record Result(double sweetH2sPpm, double h2sKmolH, double co2KmolH, double richLoading, double leanLoading,
            double acidPurity, double reboilerW, boolean saturated) {
        /** H₂S в очищенном газе, мг/нм³. */
        public double sweetH2sMgNm3() {
            return sweetH2sPpm * 1e-6 * M_H2S / NM3_PER_KMOL * 1e6;
        }
    }

    /**
     * Абсорбер и десорбер на установившемся режиме.
     *
     * @param gasNm3h      сырой газ, нм³/ч
     * @param yH2s         мольная доля H₂S в сыром газе
     * @param yCo2         мольная доля CO₂
     * @param amineM3h     циркуляция раствора, м³/ч
     * @param steamKgM3    пар на ребойлер, кг на м³ раствора
     */
    public static Result treat(double gasNm3h, double yH2s, double yCo2, double amineM3h, double steamKgM3) {
        double lean = leanLoading(steamKgM3);
        double gasKmolH = gasNm3h / NM3_PER_KMOL;
        double h2sIn = gasKmolH * yH2s;
        double ystar = Y_LEAN_REF_PPM * 1e-6 * (lean / ALPHA_LEAN_REF) * (lean / ALPHA_LEAN_REF);
        double wanted = Math.max(0, h2sIn - gasKmolH * Math.min(yH2s, ystar));
        double co2 = gasKmolH * yCo2 * CO2_EFFICIENCY;
        double capacity = Math.max(0, amineM3h * C_AMINE * (ALPHA_RICH_MAX - lean));
        // CO₂ занимает ту же ёмкость амина; H₂S реагирует быстрее и берёт её первым
        double h2s = Math.min(wanted, capacity);
        co2 = Math.min(co2, capacity - h2s);
        boolean saturated = wanted > capacity * (1 - 1e-9) && wanted > 0;
        double slip = h2sIn - h2s;
        double sweetPpm = gasKmolH > 0 ? slip / gasKmolH * 1e6 : 0;
        double rich = amineM3h > 0 ? lean + (h2s + co2) / (amineM3h * C_AMINE) : lean;
        double purity = h2s + co2 > 0 ? h2s / (h2s + co2) : 0;
        double reboilerW = amineM3h * steamKgM3 * STEAM_LATENT_J_KG / 3600;
        return new Result(sweetPpm, h2s, co2, rich, lean, purity, reboilerW, saturated);
    }
}
