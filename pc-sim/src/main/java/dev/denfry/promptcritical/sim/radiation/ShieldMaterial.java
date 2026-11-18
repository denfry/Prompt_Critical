package dev.denfry.promptcritical.sim.radiation;

/**
 * Материалы защиты от γ и нейтронов.
 *
 * <p>Массовые коэффициенты ослабления μ/ρ, см²/г, на сетке 0,5–2 МэВ — полные, с когерентным рассеянием (NIST XCOM,
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
 * свинец 0,0104, бетон 0,039. Метод выведения годится за водородсодержащей средой; для чистого графита и свинца — это
 * оценка снизу ослабления.
 */
public enum ShieldMaterial {
    LEAD(11.35, new double[] {0.1614, 0.1248, 0.0887, 0.0710, 0.0588, 0.0522, 0.0461}, 0.383, -0.0334, 0.0104),
    IRON(7.87, new double[] {0.0840, 0.0777, 0.0670, 0.0599, 0.0536, 0.0488, 0.0426}, 0.806, 0.0530, 0.0200),
    CONCRETE(2.30, new double[] {0.0870, 0.0805, 0.0707, 0.0636, 0.0568, 0.0518, 0.0445}, 0.913, 0.0706, 0.0387),
    WATER(1.00, new double[] {0.0969, 0.0896, 0.0787, 0.0707, 0.0632, 0.0575, 0.0494}, 1.03, 0.0930, 0.103),
    GRAPHITE(1.70, new double[] {0.0871, 0.0806, 0.0707, 0.0636, 0.0569, 0.0518, 0.0444}, 0.913, 0.0706, 0.047),
    SOIL(1.60, CONCRETE.massAtt, 0.913, 0.0706, 0.045),
    WOOD(0.60, WATER.massAtt, 1.03, 0.0930, 0.09);

    /** Сетка энергий для μ/ρ, МэВ. */
    public static final double[] ENERGY_MEV = {0.5, 0.6, 0.8, 1.0, 1.25, 1.5, 2.0};

    public final double density;
    private final double[] massAtt;
    final double buildupA, buildupB, removalPerGramCm2;

    ShieldMaterial(double density, double[] massAtt, double buildupA, double buildupB, double removalPerGramCm2) {
        this.density = density;
        this.massAtt = massAtt;
        this.buildupA = buildupA;
        this.buildupB = buildupB;
        this.removalPerGramCm2 = removalPerGramCm2;
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
