package dev.denfry.promptcritical.sim.metallurgy;

/**
 * Ключи коксохима и домны (08 §8.19): {@code metallurgy.coke_chamber_width_m}, {@code coke.*},
 * {@code blast_furnace.*}.
 *
 * @param cokeChamberWidthM       {@code metallurgy.coke_chamber_width_m} [BALANCE] — ширина камеры b, м (0,45)
 * @param cokingConstantHM2       {@code coke.coking_constant_h_m2} — k_c при 1300 °C, ч/м² (80)
 * @param heatupDaysPhys          {@code coke.heatup_days_phys} [BALANCE] — разогрев динаса, сут физ. (7)
 * @param minWallTempK            {@code coke.min_wall_temp_k} — ниже этого разогретый динас рвётся, К (873)
 * @param maxProductivityTM3Day   {@code blast_furnace.max_productivity_t_m3_day} — предел газодинамики (2,5)
 * @param directReductionDefault  {@code blast_furnace.direct_reduction_default} — r_d при прочном коксе (0,4)
 * @param lsC0                    {@code blast_furnace.ls_coeffs}[0] [BALANCE] (0,9)
 * @param lsCB                    {@code blast_furnace.ls_coeffs}[1] — при основности (2,0)
 * @param lsCT                    {@code blast_furnace.ls_coeffs}[2] — при T − 1773 К (0,01)
 */
public record IronmakingConfig(double cokeChamberWidthM, double cokingConstantHM2, double heatupDaysPhys,
        double minWallTempK, double maxProductivityTM3Day, double directReductionDefault, double lsC0, double lsCB,
        double lsCT) {
    public IronmakingConfig {
        if (!(cokeChamberWidthM > 0.1 && cokeChamberWidthM < 1)) throw new IllegalArgumentException("cokeChamberWidthM");
        if (!(cokingConstantHM2 > 0)) throw new IllegalArgumentException("cokingConstantHM2");
        if (!(heatupDaysPhys > 0)) throw new IllegalArgumentException("heatupDaysPhys");
        if (!(minWallTempK > 293 && minWallTempK < 1273)) throw new IllegalArgumentException("minWallTempK");
        if (!(maxProductivityTM3Day > 0)) throw new IllegalArgumentException("maxProductivityTM3Day");
        if (!(directReductionDefault >= 0 && directReductionDefault < 1))
            throw new IllegalArgumentException("directReductionDefault");
        if (!(Double.isFinite(lsC0) && lsCB >= 0 && lsCT >= 0)) throw new IllegalArgumentException("ls_coeffs");
    }

    public static IronmakingConfig defaults() {
        return new IronmakingConfig(0.45, 80, 7, 873, 2.5, 0.4, 0.9, 2.0, 0.01);
    }

    /** L_S = (S)/[S] = exp(c₀ + c_B·B + c_T·(T − 1773)) (08 §8.5.3). */
    public double sulfurPartition(double basicity, double tempK) {
        return Math.exp(lsC0 + lsCB * basicity + lsCT * (tempK - 1773));
    }
}
