package dev.denfry.promptcritical.sim.mining;

/**
 * Накопленное отравление (аналог COHb) канарейки и игрока (08 §8.2.5):
 * dB/dt = k_b·C_CO/0,1 % − B/τ_b + k_O₂·max(0, 0,17 − x_O₂). Физиология идёт в реальном времени (1 Гц),
 * концентрации — из SLOW; при постоянных концентрациях шаг интегрируется точно. B = 0,3 — беспокойство,
 * 0,6 — падение, 1 — гибель (необратимо). Игрок — та же модель с k_b, делённым на
 * {@code mining.canary_uptake_ratio}.
 */
public final class GasExposure {
    /** k_b канарейки, с⁻¹. */
    public static final double CANARY_KB = 1.0 / 120;
    /** τ_b, с. */
    public static final double TAU_S = 1800;
    /** k_O₂, с⁻¹ на долю недостающего кислорода. */
    public static final double K_O2 = 0.05;
    /** Порог кислородного голодания по O₂. */
    public static final double O2_THRESHOLD = 0.17;
    public static final double AGITATED = 0.3, FALLEN = 0.6, DEAD = 1.0;

    private final double kb;
    private double b;
    private boolean dead;

    private GasExposure(double kb) {
        this.kb = kb;
    }

    public static GasExposure canary() {
        return new GasExposure(CANARY_KB);
    }

    /** Человек: в {@code uptakeRatio} раз медленнее канарейки по CO. */
    public static GasExposure human(double uptakeRatio) {
        return new GasExposure(CANARY_KB / uptakeRatio);
    }

    /**
     * Шаг в реальном времени.
     *
     * @param dtS мольные доли действуют весь шаг
     * @param xCo доля CO
     * @param xO2 доля O₂
     */
    public void step(double dtS, double xCo, double xO2) {
        if (dead) return;
        double drive = kb * xCo / 0.001 + K_O2 * Math.max(0, O2_THRESHOLD - xO2);
        double eq = drive * TAU_S;
        b = eq + (b - eq) * Math.exp(-dtS / TAU_S);
        if (b >= DEAD) {
            b = DEAD;
            dead = true;
        }
    }

    /** Насыщение B. */
    public double saturation() {
        return b;
    }

    public ExposureState state() {
        if (dead) return ExposureState.DEAD;
        if (b >= FALLEN) return ExposureState.FALLEN;
        if (b >= AGITATED) return ExposureState.AGITATED;
        return ExposureState.CALM;
    }

    public boolean alive() {
        return !dead;
    }
}
