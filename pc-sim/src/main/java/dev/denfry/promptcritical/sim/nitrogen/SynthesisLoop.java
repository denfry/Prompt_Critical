package dev.denfry.promptcritical.sim.nitrogen;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.gasprocessing.GasCompressor;

/**
 * Цикл синтеза аммиака: компрессор свежего газа (2,6 МПа → давление цикла), циркуляционное колесо, колонна синтеза
 * ({@link AmmoniaSynthesis#converter}), водяной холодильник до 35 °C и аммиачный холодильник до заданной
 * температуры, сепаратор жидкого аммиака и продувка инертов. Состав газа цикла — запас молей в объёме цикла; давление
 * цикла не задаётся, а устанавливается само: свежий газ, который не успевает превратиться, поднимает его, пока
 * колонна не догонит (так ведут себя настоящие циклы, Appl, «Ammonia», 1999, §4.6; «Справочник азотчика», т. 1,
 * гл. 6). У предела 32 МПа свежий газ уходит на байпас компрессора. Метан и аргон свежего газа копятся в цикле: без
 * продувки их доля растёт, парциальные давления H₂ и N₂ падают, и выработка гаснет.
 *
 * <p>В газе после сепаратора остаётся аммиак по давлению насыщенного пара: y = p_s(T)/P. Холод аммиачного холодильника
 * — работа парокомпрессионной машины с холодильным коэффициентом в половину цикла Карно (конденсация при 35 °C).
 * Сжатие — {@link GasCompressor#idealWorkJPerMol} с k = 1,4.
 */
public final class SynthesisLoop {
    public static final double SUCTION_BAR = 26, MAX_BAR = 320, LOOP_DP_BAR = 12, LOOP_VOLUME_M3 = 3, T_GAS_K = 320,
            T_SUCTION_K = 313.15, WATER_COOLER_K = 308.15, K_SYNGAS = 1.4;
    /** Слои катализатора, м³ (≈ 0,7 м³ на ≈ 30 т/сут при 20–25 МПа). */
    public static final double[] BEDS_M3 = {0.12, 0.2, 0.35};
    /** Проектная циркуляция и производительность компрессора свежего газа, моль/с. */
    public static final double DESIGN_CIRC_MOL_S = 230, MAX_MAKEUP_MOL_S = 60;
    /** Свежий газ (после метанатора, H₂:N₂ = 3). */
    public static final double[] NOMINAL_MAKEUP = makeup();
    /** Низшая теплота сгорания: H₂, CH₄, NH₃, Дж/моль. */
    public static final double LHV_H2 = 241.8e3, LHV_CH4 = 802.3e3, LHV_NH3 = 316.8e3;
    public static final double OVERHEAT_LOSS_PER_H_10K = 0.02;
    private static final int H2 = Species.H2.ordinal(), N2 = Species.N2.ordinal(), NH3 = Species.NH3.ordinal(),
            CH4 = Species.CH4.ordinal(), AR = Species.AR.ordinal();

    private final double[] gas = Species.flow();
    private double circulation = 1, purgeMolS = 3, separatorK = 268.15, bedInK = 673.15, activity = 1;
    private boolean running;
    private Result last = Result.IDLE;

    /** Итог шага: расходы, моль/с; температуры, К; мощность на валу, Вт; тепло продувочного газа, Вт. */
    public record Result(double makeupMolS, double circulationMolS, double ammoniaMolS, double purgeMolS, double inletNh3,
            double outletNh3, double maxBedK, double shaftW, double chillerW, double purgeFuelW, double inerts) {
        static final Result IDLE = new Result(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static double[] makeup() {
        double[] y = Species.flow();
        // индексы — не поля класса: они ещё не инициализированы, когда строится эта константа
        y[Species.H2.ordinal()] = 0.7420;
        y[Species.N2.ordinal()] = 0.2473;
        y[Species.CH4.ordinal()] = 0.0083;
        y[Species.AR.ordinal()] = 0.0024;
        return y;
    }

    public double pressureBar() {
        return Species.total(gas) * Species.R * T_GAS_K / LOOP_VOLUME_M3 / 1e5;
    }

    public double fraction(Species s) {
        return Species.fraction(gas, s);
    }

    public boolean running() {
        return running;
    }

    public void setRunning(boolean r) {
        running = r;
    }

    public double circulation() {
        return circulation;
    }

    public void setCirculation(double c) {
        circulation = Math.max(0.3, Math.min(1.3, c));
    }

    public double purgeMolS() {
        return purgeMolS;
    }

    public void setPurgeMolS(double p) {
        purgeMolS = Math.max(0, Math.min(15, p));
    }

    public double separatorK() {
        return separatorK;
    }

    public void setSeparatorK(double k) {
        separatorK = Math.max(253.15, Math.min(308.15, k));
    }

    public double bedInK() {
        return bedInK;
    }

    public void setBedInK(double k) {
        bedInK = Math.max(633.15, Math.min(733.15, k));
    }

    public double activity() {
        return activity;
    }

    public Result last() {
        return last;
    }

    /** Холодильный коэффициент аммиачной машины: половина Карно при конденсации 35 °C. */
    public static double chillerCop(double coldK) {
        return Math.max(1, Math.min(6, 0.5 * coldK / (WATER_COOLER_K - coldK)));
    }

    /**
     * Шаг цикла на {@code dtS} с: свежего газа доступно {@code makeupMol} моль (состав — {@link #NOMINAL_MAKEUP}),
     * вал отдаёт до {@code shaftW}. Возвращает итог; жидкий аммиак за шаг — ammoniaMolS·dtS.
     */
    public Result step(double dtS, double makeupMol, double shaftW) {
        double p = pressureBar(), total = Species.total(gas);
        if (!running || dtS <= 0) {
            last = Result.IDLE;
            return last;
        }
        double mk = Math.min(MAX_MAKEUP_MOL_S, Math.max(0, makeupMol) / dtS);
        if (p >= MAX_BAR) mk = 0;
        double f = total > 0 && p > LOOP_DP_BAR ? DESIGN_CIRC_MOL_S * circulation : 0;
        double wMk = GasCompressor.idealWorkJPerMol(T_SUCTION_K, SUCTION_BAR, Math.max(p, SUCTION_BAR), K_SYNGAS);
        double wC = p > LOOP_DP_BAR ? GasCompressor.idealWorkJPerMol(WATER_COOLER_K, p - LOOP_DP_BAR, p, K_SYNGAS) : 0;
        double[] in = Species.flow(), out = Species.flow();
        double maxK = 0;
        if (f > 0) {
            for (int i = 0; i < in.length; i++) in[i] = gas[i] / total * f;
            AmmoniaSynthesis.Converter c = AmmoniaSynthesis.converter(in, bedInK, p, BEDS_M3, activity);
            out = c.out();
            maxK = c.maxK();
        }
        // сепаратор: после водяного холодильника и аммиачного
        double inert = Species.total(out) - out[NH3];
        double ySep = Math.min(0.95, AmmoniaSynthesis.vaporPressureBar(separatorK) / Math.max(p, 1));
        double yWater = Math.min(0.95, AmmoniaSynthesis.vaporPressureBar(WATER_COOLER_K) / Math.max(p, 1));
        double gasNh3 = Math.min(out[NH3], ySep / (1 - ySep) * inert);
        double liquid = out[NH3] - gasNh3;
        double byWater = Math.max(0, out[NH3] - Math.min(out[NH3], yWater / (1 - yWater) * inert));
        double chillDuty = separatorK < WATER_COOLER_K
                ? (liquid - byWater) * AmmoniaSynthesis.LATENT_J_MOL + Species.heatCapacity(out, 300) * (WATER_COOLER_K - separatorK) : 0;
        double chillerW = Math.max(0, chillDuty) / chillerCop(separatorK);
        double demand = mk * wMk + f * wC + chillerW;
        double s = demand > shaftW ? Math.max(0, shaftW) / demand : 1;
        mk *= s;
        f *= s;
        liquid *= s;
        chillerW *= s;
        // газ после сепаратора возвращается в цикл, продувка — с него
        double[] sep = out.clone();
        sep[NH3] = gasNh3;
        double sepTotal = Species.total(sep);
        double purge = sepTotal > 0 ? Math.min(purgeMolS, 0.3 * sepTotal * s) : 0;
        if (p < 0.5 * SUCTION_BAR) purge = 0;
        double[] d = Species.flow();
        for (int i = 0; i < d.length; i++) {
            double ySepI = sepTotal > 0 ? sep[i] / sepTotal : 0;
            d[i] = mk * NOMINAL_MAKEUP[i] + (sep[i] - in[i]) * s - purge * ySepI;
            gas[i] = Math.max(0, gas[i] + d[i] * dtS);
        }
        double fuel = sepTotal > 0
                ? purge * (sep[H2] * LHV_H2 + sep[CH4] * LHV_CH4 + sep[NH3] * LHV_NH3) / sepTotal : 0;
        // перегрев катализатора
        if (maxK > AmmoniaSynthesis.CATALYST_LIMIT_K)
            activity = Math.max(0.2, activity - OVERHEAT_LOSS_PER_H_10K * (maxK - AmmoniaSynthesis.CATALYST_LIMIT_K) / 10 * dtS / 3600);
        double inerts = Species.total(gas) > 0 ? (gas[CH4] + gas[AR]) / Species.total(gas) : 0;
        last = new Result(mk, f, liquid, purge, Species.fraction(in, Species.NH3), Species.fraction(out, Species.NH3), maxK,
                mk * wMk + f * wC + chillerW, chillerW, fuel, inerts);
        return last;
    }

    /** Сброс давления цикла (например, при разборе). */
    public void depressurize() {
        java.util.Arrays.fill(gas, 0);
    }

    public void save(StateWriter out) {
        for (Species sp : Species.values()) if (gas[sp.ordinal()] > 0) out.putDouble("n_" + sp.name(), gas[sp.ordinal()]);
        out.putDouble("circ", circulation);
        out.putDouble("purge", purgeMolS);
        out.putDouble("sep", separatorK);
        out.putDouble("bed_in", bedInK);
        out.putDouble("act", activity);
        out.putBoolean("run", running);
    }

    public void load(StateReader in) {
        for (Species sp : Species.values()) gas[sp.ordinal()] = Math.max(0, in.optDouble("n_" + sp.name(), 0));
        setCirculation(in.optDouble("circ", 1));
        setPurgeMolS(in.optDouble("purge", 3));
        setSeparatorK(in.optDouble("sep", 268.15));
        setBedInK(in.optDouble("bed_in", 673.15));
        activity = Math.max(0.2, Math.min(1, in.optDouble("act", 1)));
        running = in.has("run") && in.getBoolean("run");
    }
}
