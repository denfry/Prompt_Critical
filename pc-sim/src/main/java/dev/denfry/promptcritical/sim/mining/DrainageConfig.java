package dev.denfry.promptcritical.sim.mining;

/**
 * Ключи водоотлива {@code mining.water_*}, {@code mining.sump_*} (08 §8.2.9, §8.19).
 *
 * @param waterYieldM3PerT вода, вскрытая тонной добычи, м³/т (водообильность 0,5–10)
 * @param filtrationM3h    фильтрация в выработки шахты, м³/ч [BALANCE]
 * @param sumpCapacityM3   ёмкость водосборника, м³ [BALANCE: ≥ 4 ч притока по ПБ]
 * @param releaseTauS      постоянная стока вскрытой воды, с физ. [BALANCE]
 * @param acidSulfurPct    сера S^d, %, с которой вода пласта кислая ({@code mine_water}) [допущение]
 */
public record DrainageConfig(double waterYieldM3PerT, double filtrationM3h, double sumpCapacityM3, double releaseTauS,
        double acidSulfurPct) {
    public DrainageConfig {
        if (!(waterYieldM3PerT >= 0 && filtrationM3h >= 0 && sumpCapacityM3 > 0 && releaseTauS > 0 && acidSulfurPct >= 0))
            throw new IllegalArgumentException("drainage config");
    }

    public static DrainageConfig defaults() {
        return new DrainageConfig(1.0, 2.0, 400, 21_600, 1.5);
    }
}
