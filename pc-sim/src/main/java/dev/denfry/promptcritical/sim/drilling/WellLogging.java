package dev.denfry.promptcritical.sim.drilling;

import java.util.SplittableRandom;

/**
 * Каротаж открытого ствола (09 §9.2.5) и анализ керна (§9.2.6).
 *
 * <ul>
 * <li><b>Гамма-каротаж:</b> GR = GR_песч + V_гл(GR_гл − GR_песч), песчаник {@link #GR_SAND_API} API, глина
 * {@link #GR_SHALE_API} API (середины диапазонов 15–30 и 80–150); счёт пуассоновский — 60 имп/с на 100 API в окне
 * Δt = 0,1524/v, погрешность 1/√(NΔt).</li>
 * <li><b>Нейтронный (две зоны):</b> N_ближн/N_дальн = 2·e^{Δr/L_s}, 1/L_s² = (1 − φ)/L_м² + φ·HI/L_в², Δr = 30 см,
 * L_в = 5,2 см, L_м: песчаник 20 см; HI = S_в + 0,95·S_н + 2,2·ρ_г/1000·S_г. Кажущаяся пористость — та, что даёт
 * то же отношение в водоносном пласте (HI = 1): газ её занижает (газовый эффект).</li>
 * <li><b>Электрический:</b> закон Арчи R_t = aR_в/(φ^m S_в^n), a = 1, m = n = 2; R_в = {@link #RW_OHMM} Ом·м
 * (середина 0,03–0,2), глина {@link #SHALE_RT_OHMM} Ом·м [BALANCE].</li>
 * </ul>
 * Разрез ствола: глинистая покрышка над коллектором и под ним, коллектор — песчаник ловушки; над контактом —
 * углеводороды при остаточной водонасыщенности 0,25, ниже — вода. Запись идёт при подъёме со скоростью 0,15 м/с,
 * шаг 0,1524 м. Прихват зонда — вероятность 0,3 % × (1 + 3·[глинистый интервал &gt; 50 м]) на спуск [BALANCE §9.2.5];
 * обвалы ствола не моделируются.
 */
public final class WellLogging {
    public static final double STEP_M = 0.1524, SPEED_MS = 0.15;
    public static final double GR_SAND_API = 22.5, GR_SHALE_API = 115, COUNTS_PER_S_PER_100API = 60;
    public static final double SPACING_CM = 30, L_WATER_CM = 5.2, L_SAND_CM = 20;
    public static final double RW_OHMM = 0.05, SHALE_RT_OHMM = 2;
    public static final double SWI = 0.25;
    public static final double STUCK_P = 0.003, STUCK_SHALE_FACTOR = 3, STUCK_SHALE_M = 50;

    public enum Curve { GR, NPHI, RT }

    private WellLogging() {
    }

    public static double gammaApi(double vsh) {
        return GR_SAND_API + Math.clamp(vsh, 0, 1) * (GR_SHALE_API - GR_SAND_API);
    }

    /** Линейный индекс глинистости по GR. */
    public static double vshale(double grApi, double grMin, double grMax) {
        return Math.clamp((grApi - grMin) / (grMax - grMin), 0, 1);
    }

    /** Отношение счётов ближнего и дальнего детекторов. */
    public static double neutronRatio(double phi, double hydrogenIndex, double lMatrixCm) {
        double ls = 1 / Math.sqrt((1 - phi) / (lMatrixCm * lMatrixCm) + phi * hydrogenIndex / (L_WATER_CM * L_WATER_CM));
        return 2 * Math.exp(SPACING_CM / ls);
    }

    /** Кажущаяся (водонасыщенная) пористость по отношению счётов. */
    public static double apparentPorosity(double ratio, double lMatrixCm) {
        double ls = SPACING_CM / Math.log(ratio / 2);
        double inv = 1 / (ls * ls), m = 1 / (lMatrixCm * lMatrixCm), w = 1 / (L_WATER_CM * L_WATER_CM);
        return Math.clamp((inv - m) / (w - m), 0, 1);
    }

    /** Водородный индекс флюида пор. */
    public static double hydrogenIndex(double sw, double so, double sg, double gasKgM3) {
        return sw + 0.95 * so + 2.2 * gasKgM3 / 1000 * sg;
    }

    /** Закон Арчи, Ом·м. */
    public static double archieRt(double rw, double phi, double sw) {
        return rw / (phi * phi * sw * sw);
    }

    /** Истинные свойства разреза на глубине: глинистость, пористость, водонасыщенность, флюид. */
    public record Point(double vsh, double phi, double sw, FormationFluid fluid, double gasKgM3) {
    }

    public static Point formation(Prospect p, double depthM) {
        if (p == null || !p.contains(depthM)) return new Point(1, 0, 1, FormationFluid.WATER, 0);
        Trap t = p.trap();
        FormationFluid f = t.fluidAt(depthM);
        double gas = f == FormationFluid.GAS ? NaturalGas.densityKgM3(t.pressureAt(depthM) + NaturalGas.STD_PRESSURE_PA, Trap.temperatureK(depthM)) : 0;
        return new Point(0, t.porosity(), f.hydrocarbon() ? SWI : 1, f, gas);
    }

    /** Отклик приборов без шума: GR, API; кажущаяся нейтронная пористость; R_t, Ом·м. */
    public static double ideal(Curve c, Point pt) {
        return switch (c) {
            case GR -> gammaApi(pt.vsh());
            case NPHI -> {
                if (pt.phi() <= 0) yield 0;
                double so = pt.fluid() == FormationFluid.OIL ? 1 - pt.sw() : 0, sg = pt.fluid() == FormationFluid.GAS ? 1 - pt.sw() : 0;
                yield apparentPorosity(neutronRatio(pt.phi(), hydrogenIndex(pt.sw(), so, sg, pt.gasKgM3()), L_SAND_CM), L_SAND_CM);
            }
            case RT -> pt.phi() <= 0 ? SHALE_RT_OHMM : archieRt(RW_OHMM, pt.phi(), pt.sw());
        };
    }

    /**
     * Кривая от {@code bottomM} вверх до {@code topM} с шагом {@link #STEP_M}; GR — с пуассоновским шумом счёта при
     * скорости {@link #SPEED_MS}, остальные — без шума [допущение]. Значения по глубине сверху вниз.
     */
    public static double[] log(Curve c, Prospect p, double topM, double bottomM, long seed) {
        int n = Math.max(1, (int) Math.floor((bottomM - topM) / STEP_M) + 1);
        double[] out = new double[n];
        SplittableRandom rnd = new SplittableRandom(seed);
        double window = STEP_M / SPEED_MS;
        for (int i = 0; i < n; i++) {
            double z = topM + i * STEP_M;
            double v = ideal(c, formation(p, z));
            if (c == Curve.GR) {
                double mean = v / 100 * COUNTS_PER_S_PER_100API * window;
                double counts = mean + Math.sqrt(mean) * gaussian(rnd);
                v = Math.max(0, counts) / (COUNTS_PER_S_PER_100API * window) * 100;
            }
            out[i] = v;
        }
        return out;
    }

    private static double gaussian(SplittableRandom r) {
        double u = Math.max(1e-12, r.nextDouble()), w = r.nextDouble();
        return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * w);
    }

    /** Относительная погрешность гамма-каротажа 1/√(NΔt). */
    public static double grRelativeError(double grApi) {
        return 1 / Math.sqrt(grApi / 100 * COUNTS_PER_S_PER_100API * STEP_M / SPEED_MS);
    }

    /** Вероятность прихвата зонда на спуск в интервал. */
    public static double stuckProbability(Prospect p, double topM, double bottomM) {
        double shale = bottomM - topM;
        if (p != null) shale -= Math.max(0, Math.min(bottomM, p.baseM()) - Math.max(topM, p.topM()));
        return STUCK_P * (1 + (shale > STUCK_SHALE_M ? STUCK_SHALE_FACTOR : 0));
    }

    // --- керн §9.2.6 ---

    /** Погрешности станции анализа керна: пористость ±0,005 абс., проницаемость ±10 % отн., насыщенность ±0,03 абс. */
    public static final double CORE_PHI_ERR = 0.005, CORE_K_REL_ERR = 0.10, CORE_SW_ERR = 0.03;

    /** Измерение керна: пористость, проницаемость, мД, водонасыщенность (равномерная ошибка в пределах погрешности). */
    public record CoreMeasurement(double porosity, double permeabilityMd, double waterSaturation) {
    }

    public static CoreMeasurement analyse(Point truth, double permeabilityMd, long seed) {
        SplittableRandom r = new SplittableRandom(seed);
        double phi = Math.max(0, truth.phi() + CORE_PHI_ERR * (2 * r.nextDouble() - 1));
        double k = Math.max(0, permeabilityMd * (1 + CORE_K_REL_ERR * (2 * r.nextDouble() - 1)));
        double sw = Math.clamp(truth.sw() + CORE_SW_ERR * (2 * r.nextDouble() - 1), 0, 1);
        return new CoreMeasurement(phi, k, sw);
    }
}
