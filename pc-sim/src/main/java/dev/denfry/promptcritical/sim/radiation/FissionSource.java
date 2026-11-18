package dev.denfry.promptcritical.sim.radiation;

/**
 * Источник излучения делящейся системы (Glasstone, Sesonske, «Nuclear Reactor Engineering», табл. 2.x; Lamarsh,
 * гл. 3): на деление — 200 МэВ, 2,43 мгновенных нейтрона, ≈ 7 МэВ мгновенного γ и ≈ 6,5 МэВ γ продуктов деления.
 * γ считается одной эффективной группой 1 МэВ. После останова γ остаточного энерговыделения — половина мощности
 * распада (остальное — β).
 *
 * <p>Вспышка СЦР в растворе: раствор сам глушит большую часть нейтронов и γ. Утечка откалибрована по дозам аварий в
 * растворах — около 0,4 Гр на 1 м на 10¹⁷ делений (LA-13638, 2000; та же опора, что была у
 * {@code sim.enrichment.Criticality}), нейтроны дают 3/4 дозы.
 */
public final class FissionSource {
    public static final double MEV_PER_FISSION = 200, NU = 2.43, PROMPT_GAMMA_MEV = 7.0, FP_GAMMA_MEV = 6.5, GAMMA_E = 1.0;
    public static final double DECAY_GAMMA_SHARE = 0.5;
    /** Утечка из раствора при вспышке: доля нейтронов и доля γ-энергии. */
    public static final double BURST_NEUTRON_LEAK, BURST_GAMMA_LEAK;

    static {
        // 0,3 Зв нейтронов и 0,1 Гр γ на 1 м от 10¹⁷ делений
        double r2 = 4 * Math.PI * 1e4;
        BURST_NEUTRON_LEAK = 0.3 / DoseRate.NEUTRON_H_SV_CM2 * r2 / (NU * 1e17);
        double kermaPerPhoton = GAMMA_E * DoseRate.airMuEn(GAMMA_E) * DoseRate.MEV_J * 1000 / r2;
        BURST_GAMMA_LEAK = 0.1 / kermaPerPhoton / (PROMPT_GAMMA_MEV / GAMMA_E * 1e17);
    }

    private FissionSource() {
    }

    /** Делений в секунду при мощности, Вт. */
    public static double fissionRate(double powerW) {
        return powerW / (MEV_PER_FISSION * DoseRate.MEV_J);
    }

    /** Работающий реактор (мощность деления) плюс остаточное энерговыделение, Вт: источник в центре зоны. */
    public static PointSource reactor(double fissionPowerW, double decayHeatW) {
        double f = fissionRate(Math.max(0, fissionPowerW));
        double photons = f * (PROMPT_GAMMA_MEV + FP_GAMMA_MEV) / GAMMA_E
                + Math.max(0, decayHeatW) * DECAY_GAMMA_SHARE / (GAMMA_E * DoseRate.MEV_J);
        return PointSource.line(GAMMA_E, photons, f * NU);
    }

    /** Вспышка СЦР в растворе: полное число вышедших частиц. */
    public static PointSource solutionBurst(double fissions) {
        double n = Math.max(0, fissions);
        return PointSource.line(GAMMA_E, n * PROMPT_GAMMA_MEV / GAMMA_E * BURST_GAMMA_LEAK, n * NU * BURST_NEUTRON_LEAK);
    }
}
