package dev.denfry.promptcritical.sim.production;

import dev.denfry.promptcritical.sim.drilling.NaturalGas;
import dev.denfry.promptcritical.sim.drilling.Trap;

/**
 * Лифт по насосно-компрессорным трубам (НКТ) и устьевой штуцер.
 *
 * <p>Кривая лифта (VLP): давление по стволу интегрируется сверху вниз гомогенной моделью без проскальзывания фаз
 * (Brown, «The Technology of Artificial Lift Methods», т. 1, гл. 2; Brill &amp; Mukherjee, «Multiphase Flow in Wells»,
 * гл. 4): dp/dz = ρ_см·g + 2fρ_см·v²/d, ρ_см — масса нефти, растворённого и свободного газа и воды на суммарный
 * объём фаз при местном давлении. Газосодержание нефти Rs(p) = Rs₀·min(1, p/p_b) (линейная аппроксимация), объёмный
 * коэффициент B_o = 1 + 0,0022·Rs; свободный газ — по уравнению состояния {@link NaturalGas}; коэффициент Фаннинга
 * 0,006. Модель без проскальзывания занижает плотность смеси на малых дебитах, этого достаточно для точки работы
 * фонтанной скважины. Без притока газ уходит из жидкости, и столб считается жидким.
 *
 * <p>Штуцер: корреляция Гилберта для критического течения смеси p_у = 435·R^0,546·q / S^1,89 (psi, R — газожидкостный
 * фактор, тыс. фут³/барр, q — барр/сут, S — диаметр штуцера в 1/64″; Gilbert, «Flowing and Gas-Lift Well Performance»,
 * API Drilling and Production Practice, 1954). Корреляция верна, пока устьевое давление вдвое выше давления за
 * штуцером; ниже — давление на устье не меньше давления в сепараторе.
 */
public final class Tubing {
    public static final double G = 9.806_65;
    /** НКТ 2⅞″ (внутр. 2,441″). */
    public static final double DIAMETER_M = 2.441 * 0.0254;
    public static final double FANNING = 0.006;
    public static final double WELLHEAD_K = 300;
    private static final int STEPS = 60;
    private static final double PSI = 6894.757, BPD = 0.158_987 / 86_400, SCF_PER_BBL_PER_M3M3 = 5.614_6;

    private Tubing() {
    }

    /** Газосодержание нефти при давлении, м³/м³. */
    public static double solutionGor(double pressurePa, double bubblePa, double rsInitial) {
        return rsInitial * Math.min(1, Math.max(0, pressurePa) / Math.max(1, bubblePa));
    }

    public static double oilFvf(double rs) {
        return 1 + 0.0022 * rs;
    }

    /**
     * Забойное давление при устьевом {@code pwhPa}, дебите нефти {@code qOil} (м³/с, н. у.) на глубине {@code depthM}.
     *
     * @param gor        газовый фактор, м³/м³
     * @param waterCut   обводнённость, доля
     * @param bubblePa   давление насыщения, Па
     */
    public static double bottomholePa(double pwhPa, double qOil, double gor, double waterCut, double depthM, double bubblePa) {
        double area = Math.PI / 4 * DIAMETER_M * DIAMETER_M;
        double qWater = waterCut >= 1 ? 0 : qOil * waterCut / (1 - waterCut);
        double rsi = Math.min(gor, ProducingReservoir.RS_M3M3);
        double tBottom = Trap.temperatureK(depthM);
        double dz = depthM / STEPS, p = pwhPa;
        double gasStdKgM3 = NaturalGas.densityKgM3(NaturalGas.STD_PRESSURE_PA, NaturalGas.STD_TEMPERATURE_K);
        for (int i = 0; i < STEPS; i++) {
            double t = WELLHEAD_K + (tBottom - WELLHEAD_K) * (i + 0.5) / STEPS;
            double rs = solutionGor(p, bubblePa, rsi);
            double bo = oilFvf(rs);
            double grad;
            if (qOil <= 0) {
                double liveOil = (ProducingReservoir.STOCK_OIL_KGM3 + rs * gasStdKgM3) / bo;
                grad = ((1 - waterCut) * liveOil + waterCut * ProducingReservoir.WATER_KGM3) * G;
            } else {
                double freeStd = qOil * Math.max(0, gor - rs);
                double qGas = NaturalGas.volumeM3(NaturalGas.moles(freeStd, NaturalGas.STD_PRESSURE_PA, NaturalGas.STD_TEMPERATURE_K),
                        p + NaturalGas.STD_PRESSURE_PA, t);
                double mass = qOil * (ProducingReservoir.STOCK_OIL_KGM3 + gor * gasStdKgM3) + qWater * ProducingReservoir.WATER_KGM3;
                double vol = qOil * bo + qWater + qGas;
                double rho = mass / vol, v = vol / area;
                grad = rho * G + 2 * FANNING * rho * v * v / DIAMETER_M;
            }
            p += grad * dz;
        }
        return p;
    }

    /** Устьевое давление перед штуцером по Гилберту, Па (избыточное). */
    public static double gilbertWellheadPa(double qLiquidM3s, double glrM3M3, double choke64) {
        if (qLiquidM3s <= 0 || choke64 <= 0) return 0;
        double r = glrM3M3 * SCF_PER_BBL_PER_M3M3 / 1000;
        return 435 * Math.pow(r, 0.546) * (qLiquidM3s / BPD) / Math.pow(choke64, 1.89) * PSI;
    }

    /**
     * Точка работы фонтанной скважины: дебит, при котором забойное давление по IPR равно давлению по лифту при
     * устьевом давлении штуцера. 0 — скважина не фонтанирует (пласт не поднимает столб).
     */
    public static double flowingRateM3s(Inflow ipr, double gor, double waterCut, double depthM, double choke64, double separatorPa) {
        if (choke64 <= 0) return 0;
        double glr = gor * (1 - waterCut);
        java.util.function.DoubleUnaryOperator g = q -> {
            double ql = q / (1 - waterCut);
            double pwh = Math.max(separatorPa, gilbertWellheadPa(ql, glr, choke64));
            return ipr.pwfAtPa(q) - bottomholePa(pwh, q, gor, waterCut, depthM, ipr.bubblePointPa());
        };
        double qMax = ipr.maxRateM3s();
        if (qMax <= 0 || g.applyAsDouble(qMax * 1e-6) <= 0) return 0;
        double lo = qMax * 1e-6, hi = qMax;
        for (int i = 0; i < 60; i++) {
            double mid = 0.5 * (lo + hi);
            if (g.applyAsDouble(mid) > 0) lo = mid;
            else hi = mid;
        }
        return lo;
    }
}
