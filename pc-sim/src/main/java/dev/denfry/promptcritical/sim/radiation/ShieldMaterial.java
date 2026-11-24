package dev.denfry.promptcritical.sim.radiation;

/**
 * Материалы защиты от γ и нейтронов (04b §4b.4).
 *
 * <p>Массовые коэффициенты ослабления μ/ρ, см²/г, на сетке 0,5–2 МэВ и 6 МэВ (линии N-16 и γ захвата на железе) — полные, с когерентным рассеянием (NIST XCOM,
 * Berger, Hubbell; для бетона — обычный бетон NIST, ρ 2,3). Грунт и кладка считаются по составу бетона (кремнезём,
 * кальций, алюминий — μ/ρ на этих энергиях почти не зависит от Z до железа), дерево — как вода.
 *
 * <p>Фактор накопления дозы для точечного изотропного источника — форма Бергера B = 1 + a·μx·e^{b·μx}, параметры
 * подобраны по таблицам ANSI/ANS-6.4.3 (1991) при 1 МэВ на μx = 1 и 10 (Машкович В. П., Кудрявцева А. В., «Защита от
 * ионизирующих излучений», 1995, табл. 7.x — те же данные). Энергетическая зависимость B не учитывается: при 0,5 МэВ в
 * воде B занижен примерно на треть, при 2 МэВ — завышен; на мощностях дозы мира это меньше разброса геометрии.
 * Графит — параметры бетона (низкое Z).
 *
 * <p>Быстрые нейтроны — сечение выведения Σ_R/ρ, см²/г (Гусев Н. Г. и др., «Защита от ионизирующих излучений», т. 1;
 * Chilton, Shultis, Faw, «Principles of Radiation Shielding», табл. 10.x): вода 0,103, графит 0,047, железо 0,020,
 * свинец 0,0104, бетон 0,039. Метод выведения годится за водородсодержащей средой: если на луче меньше
 * {@value DoseRate#NO_HYDROGEN_CM} см замедлителя, сечение тяжёлых материалов берётся вдвое меньше
 * ({@link DoseRate#neutronSvH}). Выведенные нейтроны захватываются в том же материале и дают вторичные γ
 * ({@link #captureGammas}).
 */
public enum ShieldMaterial {
    LEAD(11.35, new double[] {0.1614, 0.1248, 0.0887, 0.0710, 0.0588, 0.0522, 0.0461, 0.04391}, 0.383, -0.0334, 0.0104, false, 6.0, 0.05),
    IRON(7.87, new double[] {0.0840, 0.0777, 0.0670, 0.0599, 0.0536, 0.0488, 0.0426, 0.03057}, 0.806, 0.0530, 0.0200, false, 7.6, 1.27),
    CONCRETE(2.30, new double[] {0.0870, 0.0805, 0.0707, 0.0636, 0.0568, 0.0518, 0.0445, 0.02697}, 0.913, 0.0706, 0.0387, true,
            2.223, 0.6, 6.0, 0.1),
    WATER(1.00, new double[] {0.0969, 0.0896, 0.0787, 0.0707, 0.0632, 0.0575, 0.0494, 0.02770}, 1.03, 0.0930, 0.103, true, 2.223, 1.11),
    /** Захват в графите мал (σ_a 3,4 мб): выведенные нейтроны поглощает топливо котла, вторичных γ кладки нет. */
    GRAPHITE(1.70, new double[] {0.0871, 0.0806, 0.0707, 0.0636, 0.0569, 0.0518, 0.0444, 0.02460}, 0.913, 0.0706, 0.047, true),
    SOIL(1.60, CONCRETE.massAtt, 0.913, 0.0706, 0.045, true, 2.223, 0.5),
    WOOD(0.60, WATER.massAtt, 1.03, 0.0930, 0.09, true, 2.223, 0.8),
    /** Магнетитовый бетон 04b §4b.4.1: μ/ρ обычного бетона (Z/A минералов ≈ 0,50), Σ_R 0,11 см⁻¹, Fe(n,γ) 7,6 МэВ. */
    HEAVY_CONCRETE(3.60, CONCRETE.massAtt, 0.913, 0.0706, 0.11 / 3.60, true, 2.223, 0.4, 7.6, 0.4),
    /** Полиэтилен (CH₂)ₙ, NIST XCOM; Σ_R 0,123 см⁻¹ (04b §4b.4.3). */
    POLYETHYLENE(0.95, new double[] {0.09947, 0.09198, 0.08078, 0.07260, 0.06493, 0.05910, 0.05068, 0.02760}, 1.03, 0.0930, 0.123 / 0.95, true,
            2.223, 1.11),
    /**
     * Борированный полиэтилен, 5 % B (04b §4b.4.3): Σ_R 0,120 см⁻¹; захват идёт в основном на B-10(n,α)Li-7*, линия
     * 0,478 МэВ вместо 2,223 МэВ водорода — поэтому он стоит снаружи водяной защиты.
     */
    BORATED_POLYETHYLENE(1.00, POLYETHYLENE.massAtt, 1.03, 0.0930, 0.120, true, 0.478, 0.68, 2.223, 0.11),
    /**
     * Вода с 4 % H₃BO₃ (≈ 7 000 ppm B). Σ_a: водород 0,022 см⁻¹, B-10 (19,9 % природного бора, 3 840 б) 0,30 см⁻¹ —
     * бор забирает ≈ 93 % захватов: 0,478 МэВ × 0,93 × 0,94 (ветвь с γ) = 0,87 на нейтрон, водороду остаётся 1,11 × 0,07
     * [оценка по сечениям ENDF/B-VII].
     */
    BORATED_WATER(1.02, WATER.massAtt, 1.03, 0.0930, 0.103 / 1.02, true, 0.478, 0.87, 2.223, 0.08),
    /**
     * Свинцовое стекло окон горячих камер, ≈ 60 % PbO, ρ 4,8 (04b §4b.4.1, тяжёлый флинт): μ/ρ — смесь 55,7 % Pb и
     * 44,3 % стекла по массе (μ/ρ стекла — как у бетона), Σ_R по той же смеси; фактор накопления — свинца.
     */
    LEAD_GLASS(4.80, new double[] {0.1284, 0.1052, 0.0807, 0.0677, 0.0579, 0.0520, 0.0454, 0.0364}, 0.383, -0.0334, 0.0229, false, 6.0, 0.1);

    /** Сетка энергий для μ/ρ, МэВ. */
    public static final double[] ENERGY_MEV = {0.5, 0.6, 0.8, 1.0, 1.25, 1.5, 2.0, 6.0};

    public final double density;
    private final double[] massAtt;
    final double buildupA, buildupB, removalPerGramCm2;
    /** Водородсодержащий (или графит — тоже замедлитель): выводит быстрые нейтроны полным сечением (04b §4b.4.3). */
    public final boolean moderating;
    private final double[] captureMeV, captureYield;

    /**
     * @param capture пары «энергия γ захвата, МэВ; фотонов на выведенный нейтрон» (04b §4b.4.3, таблица вторичных γ)
     */
    ShieldMaterial(double density, double[] massAtt, double buildupA, double buildupB, double removalPerGramCm2, boolean moderating,
            double... capture) {
        this.density = density;
        this.massAtt = massAtt;
        this.buildupA = buildupA;
        this.buildupB = buildupB;
        this.removalPerGramCm2 = removalPerGramCm2;
        this.moderating = moderating;
        this.captureMeV = new double[capture.length / 2];
        this.captureYield = new double[capture.length / 2];
        for (int i = 0; i < captureMeV.length; i++) {
            captureMeV[i] = capture[2 * i];
            captureYield[i] = capture[2 * i + 1];
        }
    }

    /**
     * Вторичные γ захвата от {@code removedNeutrons} нейтронов, выведенных в этом материале (точечный источник в точке
     * выведения; нейтронов в нём нет).
     */
    public PointSource captureGammas(double removedNeutrons) {
        double[] p = new double[captureYield.length];
        for (int i = 0; i < p.length; i++) p[i] = captureYield[i] * removedNeutrons;
        return new PointSource(captureMeV.clone(), p, 0);
    }

    /** μ/ρ при энергии (лог-лог интерполяция по сетке, вне её — крайние значения), см²/г. */
    public double massAttenuation(double energyMeV) {
        return logLog(ENERGY_MEV, massAtt, energyMeV);
    }

    /** Линейный коэффициент ослабления при плотности {@code rho}, 1/см. */
    public double mu(double energyMeV, double rho) {
        return massAttenuation(energyMeV) * rho;
    }

    /** Фактор накопления дозы на {@code mfp} длинах свободного пробега. */
    public double buildup(double mfp) {
        if (mfp <= 0) return 1;
        return 1 + buildupA * mfp * Math.exp(buildupB * mfp);
    }

    /** Макроскопическое сечение выведения быстрых нейтронов при плотности {@code rho}, 1/см. */
    public double removal(double rho) {
        return removalPerGramCm2 * rho;
    }

    static double logLog(double[] xs, double[] ys, double x) {
        if (x <= xs[0]) return ys[0];
        for (int i = 1; i < xs.length; i++) {
            if (x <= xs[i]) {
                double t = Math.log(x / xs[i - 1]) / Math.log(xs[i] / xs[i - 1]);
                return Math.exp(Math.log(ys[i - 1]) + t * (Math.log(ys[i]) - Math.log(ys[i - 1])));
            }
        }
        return ys[ys.length - 1];
    }
}
