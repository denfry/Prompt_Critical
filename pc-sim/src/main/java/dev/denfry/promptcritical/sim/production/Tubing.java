package dev.denfry.promptcritical.sim.production;

import dev.denfry.promptcritical.sim.drilling.NaturalGas;
import dev.denfry.promptcritical.sim.drilling.Trap;

/**
 * Лифт по насосно-компрессорным трубам (НКТ) и устьевой штуцер.
 *
 * <p>Кривая лифта (VLP): давление по стволу интегрируется сверху вниз моделью дрейфа Зубера–Финдли (Brill &amp;
 * Mukherjee, «Multiphase Flow in Wells», гл. 4; Brown, «The Technology of Artificial Lift Methods», т. 1, гл. 2):
 * истинное газосодержание α = v_sg/(C₀v_m + v_d), C₀ = 1,2, скорость всплытия пузырей по Хармати
 * v_d = 1,53·(gσΔρ/ρ_ж²)^¼, σ = 0,025 Н/м. dp/dz = ρ_см·g + 2fρ_нс·v_m²/d, где ρ_см = αρ_г + (1 − α)ρ_ж — с учётом
 * проскальзывания, ρ_нс — без него (для трения). На малых дебитах газ всплывает сквозь жидкость, столб почти жидкий,
 * и скважина с низким пластовым давлением не фонтанирует; на больших газ облегчает столб («газлифт»), пока не
 * берёт верх трение. Газосодержание нефти Rs(p) = Rs₀·min(1, p/p_b), B_o = 1 + 0,0022·Rs, свободный газ — по
 * {@link NaturalGas}, коэффициент Фаннинга 0,006. Без притока газ уходит из жидкости, и столб считается жидким.
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
    /** Параметр распределения и поверхностное натяжение нефти, Н/м, для модели дрейфа. */
    public static final double C0 = 1.2, SURFACE_TENSION = 0.025;
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
                double qLiq = qOil * bo + qWater;
                double liquidMass = qOil * (ProducingReservoir.STOCK_OIL_KGM3 + rs * gasStdKgM3) + qWater * ProducingReservoir.WATER_KGM3;
                double rhoL = liquidMass / qLiq;
                double rhoG = NaturalGas.densityKgM3(p + NaturalGas.STD_PRESSURE_PA, t);
                double vsg = qGas / area, vm = (qLiq + qGas) / area;
                double vd = 1.53 * Math.pow(G * SURFACE_TENSION * Math.max(0, rhoL - rhoG) / (rhoL * rhoL), 0.25);
                double holdup = vsg > 0 ? Math.min(vsg / (C0 * vm + vd), 1) : 0;
                double rho = holdup * rhoG + (1 - holdup) * rhoL;
                double rhoNoSlip = (liquidMass + qOil * Math.max(0, gor - rs) * gasStdKgM3) / (qLiq + qGas);
                grad = rho * G + 2 * FANNING * rhoNoSlip * vm * vm / DIAMETER_M;
            }
            p += grad * dz;
        }
        return p;
    }

    /**
     * Скважина запускается сама: при малом дебите пласт держит столб с устьевым давлением сепаратора. Иначе
     * фонтан, даже если устойчивая точка работы есть, надо вызывать (свабированием, компрессором).
     */
    public static boolean kicksOff(Inflow ipr, double gor, double waterCut, double depthM, double separatorPa) {
        double q = ipr.maxRateM3s() * 1e-4;
        return q > 0 && ipr.pwfAtPa(q) > bottomholePa(separatorPa, q, gor, waterCut, depthM, ipr.bubblePointPa());
    }

    /** Устьевое давление перед штуцером по Гилберту, Па (избыточное). */
    public static double gilbertWellheadPa(double qLiquidM3s, double glrM3M3, double choke64) {
        if (qLiquidM3s <= 0 || choke64 <= 0) return 0;
        double r = glrM3M3 * SCF_PER_BBL_PER_M3M3 / 1000;
        return 435 * Math.pow(r, 0.546) * (qLiquidM3s / BPD) / Math.pow(choke64, 1.89) * PSI;
    }

    /**
     * Точка работы фонтанной скважины: дебит, при котором забойное давление по IPR равно давлению по лифту при
     * устьевом давлении штуцера. Кривая лифта с проскальзыванием U-образная, и пересечений бывает два; устойчиво
     * правое — там, где при росте дебита лифт требует больше, чем даёт пласт. Ищется последняя смена знака невязки
     * с плюса на минус по сетке дебитов, затем делением пополам. 0 — пересечения нет, скважина не фонтанирует.
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
        if (qMax <= 0) return 0;
        int n = 48;
        double lo = Double.NaN, hi = Double.NaN, prevQ = qMax * 1e-4, prev = g.applyAsDouble(prevQ);
        for (int k = 1; k <= n; k++) {
            double q = qMax * Math.pow(10, -4 + 4.0 * k / n) * (k == n ? 0.999_999 : 1);
            double v = g.applyAsDouble(q);
            if (prev > 0 && v <= 0) {
                lo = prevQ;
                hi = q;
            }
            prevQ = q;
            prev = v;
        }
        if (Double.isNaN(lo)) return 0;
        for (int i = 0; i < 60; i++) {
            double mid = 0.5 * (lo + hi);
            if (g.applyAsDouble(mid) > 0) lo = mid;
            else hi = mid;
        }
        return lo;
    }
}
