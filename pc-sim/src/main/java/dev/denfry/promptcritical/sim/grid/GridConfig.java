package dev.denfry.promptcritical.sim.grid;

/**
 * Ключи {@code grid.*} электрической модели (07 §7.21).
 *
 * @param baseMva             {@code grid.base_mva} — базисная мощность S_b
 * @param nominalFrequencyHz  {@code grid.nominal_frequency_hz} — 50 или 60
 * @param substeps            {@code grid.substeps} — подшаги машин на тик
 * @param solverIterations    {@code grid.solver_iterations} — итераций сети на подшаг
 * @param solverRelaxation    {@code grid.solver_relaxation} — нижняя релаксация ϖ
 * @param maxRefactorPerTick  {@code grid.max_refactor_per_tick} — переразложений островов за тик
 * @param loadDamping         {@code grid.load_damping} [BALANCE] — D_L нагрузок без своего D_f
 * @param governorDeadbandHz  {@code grid.governor_deadband_hz} [сверить] — мёртвая зона регуляторов
 */
public record GridConfig(double baseMva, double nominalFrequencyHz, int substeps, int solverIterations,
        double solverRelaxation, int maxRefactorPerTick, double loadDamping, double governorDeadbandHz) {
    public GridConfig {
        if (!(baseMva > 0)) throw new IllegalArgumentException("baseMva");
        if (nominalFrequencyHz != 50 && nominalFrequencyHz != 60) throw new IllegalArgumentException("nominalFrequencyHz");
        if (substeps < 1) throw new IllegalArgumentException("substeps");
        if (solverIterations < 1) throw new IllegalArgumentException("solverIterations");
        if (!(solverRelaxation > 0 && solverRelaxation <= 1)) throw new IllegalArgumentException("solverRelaxation");
        if (maxRefactorPerTick < 1) throw new IllegalArgumentException("maxRefactorPerTick");
        if (!(loadDamping >= 0)) throw new IllegalArgumentException("loadDamping");
        if (!(governorDeadbandHz >= 0)) throw new IllegalArgumentException("governorDeadbandHz");
    }

    public static GridConfig defaults() {
        return new GridConfig(100, 50, 2, 2, 0.8, 2, 1.5, 0.075);
    }

    public GridConfig withGovernorDeadbandHz(double hz) {
        return new GridConfig(baseMva, nominalFrequencyHz, substeps, solverIterations, solverRelaxation,
                maxRefactorPerTick, loadDamping, hz);
    }

    /** S_b, В·А. */
    public double baseVa() {
        return baseMva * 1e6;
    }

    /** ω_s = 2πf₀, рад/с. */
    public double synchronousSpeed() {
        return 2 * Math.PI * nominalFrequencyHz;
    }
}
