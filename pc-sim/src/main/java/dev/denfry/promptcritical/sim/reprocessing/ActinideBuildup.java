package dev.denfry.promptcritical.sim.reprocessing;

/**
 * Состав облучённого уранового топлива по выгоранию — ORIGEN-подобная аппроксимация для тепловых реакторов на
 * обогащённом и природном уране (Benedict, Pigford, Levi, «Nuclear Chemical Engineering», 1981, табл. 3.x, 8.x — PWR
 * 3,2 %, 33 МВт·сут/кг: U-235 0,84 %, U-236 0,41 %, Pu 0,90 % ТМ, Pu-239/240/241/242 — 57/23/13/5 %; ВВЭР-440 и
 * графитовые реакторы на природном уране — та же форма с другим исходным обогащением).
 * <ul>
 *   <li>U-235 выгорает экспоненциально: x = x₀·exp(−BU/B₂₅), B₂₅ = 7,72·x₀[%] МВт·сут/кг;</li>
 *   <li>U-236 — радиационный захват на U-235: 0,173 от выгоревшего U-235 (α₂₅ ≈ 0,17);</li>
 *   <li>Pu копится и насыщается: Pu/ТМ = P_sat·(1 − e^{−BU/30}), P_sat = 1,25 % (на природном уране 0,9 % —
 *   больше U-238 на нейтрон, но и меньше выгорание);</li>
 *   <li>изотопия Pu: доля Pu-239 = 1 − 0,012·BU (не ниже 0,5), Pu-240 0,0070·BU, Pu-241 0,0040·BU, Pu-242 — остаток;
 *   при 0,1–1 МВт·сут/кг (котёл T0) — оружейный плутоний 99–98 % Pu-239;</li>
 *   <li>продукты деления: 1,05 г на МВт·сут;</li>
 *   <li>Np-237 — цепочки U-235(n,γ)U-236(n,γ)U-237(β⁻) и U-238(n,2n)U-237(β⁻): почти линейно по выгоранию,
 *   {@value #NP_PER_MWD} кг на МВт·сут/кг ТМ — 0,47 кг/т ТМ при 33 МВт·сут/кг (BPL табл. 8.x: ≈ 0,43–0,5 кг/т для PWR
 *   3,2 %; OECD/NEA «Physics and Safety of Transmutation Systems», 2006: ≈ 0,5 кг/т UOX при 33 ГВт·сут/т, ≈ 0,7 кг/т при
 *   50).</li>
 * </ul>
 */
public final class ActinideBuildup {
    public static final double B25_PER_PCT = 7.72, ALPHA25 = 0.173, PU_SAT = 0.0125, PU_SAT_NATURAL = 0.009, PU_B = 30, FP_PER_MWD = 1.05e-3;
    /** Np-237, кг на кг исходного ТМ на МВт·сут/кг. */
    public static final double NP_PER_MWD = 1.42e-5;

    /**
     * Состав на кг исходного тяжёлого металла.
     *
     * @param u235      доля U-235 в уране
     * @param u236      доля U-236 в уране
     * @param puPerHm   плутоний, кг на кг исходного ТМ
     * @param fpPerHm   продукты деления, кг на кг исходного ТМ
     * @param np237PerHm нептуний-237, кг на кг исходного ТМ
     */
    public record Composition(double burnup, double u235, double u236, double puPerHm, double pu239, double pu240, double pu241,
            double pu242, double fpPerHm, double np237PerHm) {
        /** Уран, кг на кг исходного ТМ. */
        public double uPerHm() {
            return Math.max(0, 1 - puPerHm - fpPerHm - np237PerHm);
        }
    }

    private ActinideBuildup() {
    }

    public static Composition at(double initialU235, double burnupMWdPerKg) {
        double bu = Math.max(0, burnupMWdPerKg), x0 = Math.max(1e-6, initialU235);
        double x25 = x0 * Math.exp(-bu / (B25_PER_PCT * x0 * 100));
        double u236 = ALPHA25 * (x0 - x25);
        double sat = x0 < 0.009 ? PU_SAT_NATURAL : PU_SAT;
        double pu = sat * (1 - Math.exp(-bu / PU_B));
        double p39 = Math.max(0.5, 1 - 0.012 * bu), p40 = Math.min(0.30, 0.0070 * bu), p41 = Math.min(0.15, 0.0040 * bu);
        double p42 = Math.max(0, 1 - p39 - p40 - p41);
        double fp = FP_PER_MWD * bu, np = np237(bu);
        // доли U — относительно оставшегося урана
        double uLeft = Math.max(1e-9, 1 - pu - fp - np);
        return new Composition(bu, x25 / uLeft, u236 / uLeft, pu, p39, p40, p41, p42, fp, np);
    }

    /** Нептуний-237 в облучённом топливе, кг на кг исходного ТМ. */
    public static double np237(double burnupMWdPerKg) {
        return NP_PER_MWD * Math.max(0, burnupMWdPerKg);
    }
}
