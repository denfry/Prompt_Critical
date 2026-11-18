package dev.denfry.promptcritical.sim.refining;

/**
 * Электрообессоливающая установка (ЭЛОУ, GDD 09 §9.7.3; прототип — электродегидратор ЭГ-160): нефть промывают пресной
 * водой, эмульсию перемешивают на смесительном клапане, в поле электродов капли воды сливаются и оседают.
 *
 * <p><b>Соль после ступени</b> (09 §9.7.3, тест T9.43):
 * <pre>S_out = S_in · w_res / (w_in + η_mix·r_w),</pre>
 * S — соль, мг на литр нефти; w_in — объёмная доля пластовой воды на входе; r_w — доля промывной воды; η_mix —
 * полнота смешения; w_res — остаточная вода после отстоя.
 *
 * <p><b>Отстой</b> (09 §9.7.2–9.7.3): капли оседают по Стоксу v = g·d²·Δρ/(18·μ_o); поле поляризует и сливает
 * капли, d_eff = d₀·(1 + k_E·E²), k_E = {@value #K_E} (см/кВ)² ({@code desalter.field_coalescence_k} [BALANCE]);
 * вода, не осевшая за время пребывания, остаётся в нефти: w_res = w_tot·exp(−t_res·v/h).
 *
 * <p><b>Смесительный клапан</b> (09 §9.7.3: Δp 0,05–0,15 МПа, η_mix 0,5–0,9, «больше — лучше смешение, но мельче
 * капли»): η_mix = 1 − exp(−Δp/{@value #DP_MIX_MPA}) — 0,54 при 0,05 МПа и 0,90 при 0,15 МПа; квадрат исходного
 * диаметра капель падает с перепадом линейно, d₀² = (200 мкм)²·(1 − γ·(Δp − 0,1)), γ = {@value #DROPLET_DP_SLOPE}
 * МПа⁻¹ (200 мкм — капли без деэмульгатора, §9.7.2; γ — [BALANCE]: оптимум перепада около 0,1 МПа). Оптимум Δp
 * ищет игрок; он пологий, главное — поле и нагрев.
 *
 * <p><b>Вязкость нефти</b> μ(T) = μ₂₀·exp(B·(1/T − 1/293,15)): μ₂₀ = 8 мПа·с (лёгкая западносибирская нефть, как у
 * жидкости {@code crude_oil}), B = {@value #VISCOSITY_B_K} К — 1,5 мПа·с при 120 °C [BALANCE]. Нагрев до 110–140 °C
 * (§9.7.3) ускоряет отстой. Время пребывания {@value #RESIDENCE_S} с и высота отстоя {@value #SETTLING_H_M} м
 * [BALANCE] подобраны так, что номинальный режим (2 кВ/см, 0,1 МПа, 120 °C, r_w = 0,06) даёт w_res ≈ 0,002 — значение
 * примера §9.7.3. Без поля (нет напряжения) капли в 3 раза мельче и отстой почти не идёт.
 *
 * <p>Установка — две ступени последовательно, на каждую — свежая промывная вода r_w [допущение: схема без
 * противотока воды со второй ступени на первую].
 */
public final class Desalting {
    public static final double K_E = 0.5, FIELD_KV_CM = 2;
    public static final double D_REF_M = 200e-6, DP_REF_MPA = 0.10, DROPLET_DP_SLOPE = 1.1, DP_MIX_MPA = 0.0651;
    public static final double DP_MIN_MPA = 0.05, DP_MAX_MPA = 0.15;
    public static final double G = 9.81, DELTA_RHO_KG_M3 = 160, MU20_PA_S = 8e-3, VISCOSITY_B_K = 1928;
    public static final double RESIDENCE_S = 250, SETTLING_H_M = 1.5;
    /** Нормы: перед перегонкой на НПЗ — соли ≤ 3–5 мг/л (§9.7.3); берём верх, 5 мг/л. */
    public static final double REFINERY_SALT_MAX_MG_L = 5;

    private Desalting() {
    }

    /** Соль после одной ступени, мг/л (09 §9.7.3). */
    public static double stageSalt(double saltIn, double waterIn, double washRatio, double mixEfficiency, double residualWater) {
        double wet = waterIn + mixEfficiency * washRatio;
        return wet <= 0 ? saltIn : saltIn * Math.min(1, residualWater / wet);
    }

    /** Полнота смешения на клапане перепадом {@code dpMpa}. */
    public static double mixingEfficiency(double dpMpa) {
        return 1 - Math.exp(-Math.max(0, dpMpa) / DP_MIX_MPA);
    }

    /** Эффективный диаметр капель после клапана и поля, м. */
    public static double dropletM(double dpMpa, double fieldKvCm) {
        double d0 = D_REF_M * Math.sqrt(Math.max(0.05, 1 - DROPLET_DP_SLOPE * (dpMpa - DP_REF_MPA)));
        return d0 * (1 + K_E * fieldKvCm * fieldKvCm);
    }

    /** Динамическая вязкость нефти, Па·с. */
    public static double viscosity(double tK) {
        return MU20_PA_S * Math.exp(VISCOSITY_B_K * (1 / tK - 1 / 293.15));
    }

    /** Скорость осаждения капли по Стоксу, м/с. */
    public static double settlingVelocity(double dM, double tK) {
        return G * dM * dM * DELTA_RHO_KG_M3 / (18 * viscosity(tK));
    }

    /** Остаточная вода после отстоя, объёмная доля, если в нефти перед отстоем {@code waterTotal}. */
    public static double residualWater(double waterTotal, double dpMpa, double fieldKvCm, double tK) {
        double v = settlingVelocity(dropletM(dpMpa, fieldKvCm), tK);
        return waterTotal * Math.exp(-RESIDENCE_S * v / SETTLING_H_M);
    }

    /**
     * @param saltOut      соль в нефти после двух ступеней, мг/л
     * @param stage1Salt   соль после первой ступени, мг/л
     * @param waterOut     остаточная вода в нефти, объёмная доля
     * @param mixEfficiency полнота смешения
     * @param washWater    расход промывной воды на обе ступени, доля от нефти
     * @param brine        солёная вода из отстоя на обе ступени, доля от нефти
     */
    public record Result(double saltOut, double stage1Salt, double waterOut, double mixEfficiency, double washWater, double brine) {
    }

    /** Две ступени ЭЛОУ. */
    public static Result twoStage(double saltIn, double waterIn, double washRatio, double dpMpa, double fieldKvCm, double tK) {
        double eta = mixingEfficiency(dpMpa);
        double w1 = residualWater(waterIn + washRatio, dpMpa, fieldKvCm, tK);
        double s1 = stageSalt(saltIn, waterIn, washRatio, eta, w1);
        double w2 = residualWater(w1 + washRatio, dpMpa, fieldKvCm, tK);
        double s2 = stageSalt(s1, w1, washRatio, eta, w2);
        double brine = waterIn + 2 * washRatio - w2;
        return new Result(s2, s1, w2, eta, 2 * washRatio, brine);
    }
}
