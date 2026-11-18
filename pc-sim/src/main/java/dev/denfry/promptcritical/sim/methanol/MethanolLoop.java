package dev.denfry.promptcritical.sim.methanol;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.gasprocessing.GasCompressor;
import dev.denfry.promptcritical.sim.nitrogen.Species;
import dev.denfry.promptcritical.sim.thermo.if97.If97;

/**
 * Контур синтеза метанола низкого давления (схема Lurgi/ICI): компрессор свежего газа (2 МПа → давление контура),
 * циркуляционное колесо, трубчатый реактор с кипящей водой в межтрубье ({@link MethanolSynthesis#reactor}), водяной
 * холодильник до 40 °C, сепаратор сырого метанола и продувка. Как и у {@code SynthesisLoop} аммиака, давление не
 * задаётся: свежий газ, который не успевает превратиться, поднимает его, пока реактор не догонит; у предела
 * {@link #MAX_BAR} свежий газ уходит на байпас. Метан и азот свежего газа, а при модуле S > 2 и избыточный водород
 * копятся в контуре — продувка их выводит (кратность циркуляции 4–5 при 5–8 % метанола на выходе реактора; Supp,
 * 1990; Hansen, Højlund Nielsen, 2008).
 *
 * <p>Тепло реакции уходит в кипящую воду межтрубья: пар при выбранном давлении (2–8 МПа, по умолчанию 4 МПа, 250 °C)
 * из питательной воды 104 °C. Нет воды — слой разогревается к адиабатической равновесной температуре (за
 * {@link #BED_TAU_S} с). Медь спекается: при нормальной работе медленно (вторая степень по активности, k ∝
 * exp(−E/RT), половина активности за ≈ 3 года при 250 °C — обычный пробег 2–4 года), выше {@link MethanolSynthesis#SINTER_K}
 * — быстро [BALANCE]. Сжатие — {@link GasCompressor#idealWorkJPerMol} с k = 1,4.
 */
public final class MethanolLoop {
    public static final double SUCTION_BAR = MethanolReformer.PRESSURE_BAR - 1, MAX_BAR = 100, LOOP_DP_BAR = 4,
            LOOP_VOLUME_M3 = 4, T_GAS_K = 320, SEPARATOR_K = 313.15, K_SYNGAS = 1.4, FEEDWATER_K = 377.15;
    /** Катализатор ≈ 1,2 м³ (насыпная плотность 1250 кг/м³). */
    public static final double CATALYST_KG = 1500;
    /** Проектная циркуляция через реактор и производительность компрессора свежего газа, моль/с. */
    public static final double DESIGN_CIRC_MOL_S = 220, MAX_MAKEUP_MOL_S = 60;
    /** Низшая теплота сгорания газов продувки, Дж/моль. */
    public static final double LHV_H2 = 241.8e3, LHV_CO = 283.0e3, LHV_CH4 = 802.3e3, LHV_MEOH = 638.1e3;
    /** Тепловая инерция слоя, с. */
    public static final double BED_TAU_S = 120;
    /** Старение: k при 250 °C, 1/с (1/a − 1 = k·t: половина за 3 года), энергия активации, Дж/моль [BALANCE]. */
    public static final double AGING_K_REF = 1 / (3 * 365.25 * 86400.0), AGING_T_REF = 523.15, AGING_E = 100e3;
    /** Спекание выше {@link MethanolSynthesis#SINTER_K}: доля активности в час на 10 К превышения [BALANCE]. */
    public static final double OVERHEAT_LOSS_PER_H_10K = 0.05;
    private static final int H2 = Species.H2.ordinal(), CO = Species.CO.ordinal(), CH4 = Species.CH4.ordinal(),
            MEOH = Species.CH3OH.ordinal(), N2 = Species.N2.ordinal(), AR = Species.AR.ordinal();

    private final double[] gas = Species.flow();
    private double circulation = 1, purgeMolS = 12, steamBar = 40, activity, bedK = 300;
    private boolean running;
    private Result last = Result.IDLE;

    /**
     * Итог шага: расходы, моль/с; доли метанола на входе, выходе реактора и в равновесии при температуре слоя;
     * температура слоя, К; модуль S газа на входе в реактор и свежего газа; мощность на валу, Вт; тепло реакции, Вт;
     * пар межтрубья, кг/с; тепло продувочного газа, Вт; доля инертов (CH₄ + N₂ + Ar); доля принятого свежего газа.
     */
    public record Result(double makeupMolS, double circulationMolS, double methanolMolS, double waterMolS, double purgeMolS,
            double inletMeoh, double outletMeoh, double equilibriumMeoh, double bedK, double loopSn, double makeupSn, double shaftW,
            double dutyW, double steamKgS, double purgeFuelW, double inerts, double accepted) {
        static final Result IDLE = new Result(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        /** Массовая доля метанола в сыром метаноле. */
        public double crudeStrength() {
            double m = methanolMolS * Species.CH3OH.molarMass(), w = waterMolS * Species.H2O.molarMass();
            return m + w > 0 ? m / (m + w) : 0;
        }
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
        purgeMolS = Math.max(0, Math.min(25, p));
    }

    /** Давление пара в межтрубье, бар; от него температура кипения и слоя. */
    public double steamBar() {
        return steamBar;
    }

    public void setSteamBar(double b) {
        steamBar = Math.max(20, Math.min(80, b));
    }

    public double coolantK() {
        return If97.tsat(steamBar * 1e5);
    }

    public double activity() {
        return activity;
    }

    public void loadCatalyst() {
        activity = 1;
    }

    public double bedK() {
        return bedK;
    }

    public Result last() {
        return last;
    }

    /**
     * Шаг контура на {@code dtS} с: свежий газ предлагается потоком {@code makeupMolS} (моль/с по {@link Species}),
     * вал отдаёт до {@code shaftW}, {@code cooled} — в межтрубье есть питательная вода. Принятая доля свежего газа —
     * {@link Result#accepted()}; сырой метанол за шаг — (methanol + water)·dtS.
     */
    public Result step(double dtS, double[] makeupMolS, double shaftW, boolean cooled) {
        double p = pressureBar(), total = Species.total(gas);
        if (!running || dtS <= 0) {
            bedK += (T_GAS_K - bedK) * (1 - Math.exp(-dtS / (10 * BED_TAU_S)));
            last = Result.IDLE;
            return last;
        }
        double offered = Species.total(makeupMolS);
        // у предела давления байпас компрессора открывается плавно, в последних 2 бар
        double mk = Math.min(MAX_MAKEUP_MOL_S, offered) * Math.max(0, Math.min(1, (MAX_BAR - p) / 2));
        double f = total > 0 && p > LOOP_DP_BAR ? DESIGN_CIRC_MOL_S * circulation : 0;
        double wMk = GasCompressor.idealWorkJPerMol(SEPARATOR_K, SUCTION_BAR, Math.max(p, SUCTION_BAR), K_SYNGAS);
        double wC = p > LOOP_DP_BAR ? GasCompressor.idealWorkJPerMol(SEPARATOR_K, p - LOOP_DP_BAR, p, K_SYNGAS) : 0;
        double demand = mk * wMk + f * wC;
        double s = demand > shaftW ? Math.max(0, shaftW) / demand : 1;
        mk *= s;
        f *= s;
        double[] in = Species.flow(), out = Species.flow();
        double eqMeoh = 0, duty = 0;
        if (f > 0) {
            for (int i = 0; i < in.length; i++) in[i] = gas[i] / total * f;
            // слой: к кипящей воде (+ перегрев) или, без воды, к адиабатическому равновесию
            double target = cooled ? coolantK() + MethanolSynthesis.BED_RISE_K
                    : MethanolSynthesis.adiabaticEquilibriumK(in, coolantK(), p);
            bedK += (target - bedK) * (1 - Math.exp(-dtS / BED_TAU_S));
            out = MethanolSynthesis.reactor(in, bedK, p, CATALYST_KG, activity);
            eqMeoh = Species.fraction(MethanolSynthesis.equilibrium(in, bedK, p), Species.CH3OH);
            duty = cooled ? Math.max(0, Species.enthalpy(in, bedK) - Species.enthalpy(out, bedK)) : 0;
        } else {
            bedK += (coolantK() - bedK) * (1 - Math.exp(-dtS / BED_TAU_S));
        }
        double[][] sep = MethanolSynthesis.separate(out, SEPARATOR_K, Math.max(p - LOOP_DP_BAR, 1));
        double[] g = sep[0], liq = sep[1];
        double gTotal = Species.total(g);
        double purge = gTotal > 0 ? Math.min(purgeMolS, 0.3 * gTotal) : 0;
        if (p < 0.5 * SUCTION_BAR) purge = 0;
        double scale = offered > 0 ? mk / offered : 0;
        for (int i = 0; i < gas.length; i++) {
            double y = gTotal > 0 ? g[i] / gTotal : 0;
            gas[i] = Math.max(0, gas[i] + (makeupMolS[i] * scale + g[i] - in[i] - purge * y) * dtS);
        }
        double fuel = gTotal > 0
                ? purge * (g[H2] * LHV_H2 + g[CO] * LHV_CO + g[CH4] * LHV_CH4 + g[MEOH] * LHV_MEOH) / gTotal : 0;
        age(dtS, f > 0);
        double tot = Species.total(gas);
        double inerts = tot > 0 ? (gas[CH4] + gas[N2] + gas[AR]) / tot : 0;
        last = new Result(mk, f, liq[MEOH], liq[Species.H2O.ordinal()], purge, Species.fraction(in, Species.CH3OH),
                Species.fraction(out, Species.CH3OH), eqMeoh, bedK, MethanolSynthesis.stoichiometricNumber(in),
                MethanolSynthesis.stoichiometricNumber(makeupMolS), mk * wMk + f * wC, duty, steamKgS(duty), fuel, inerts,
                offered > 0 ? mk / offered : 0);
        return last;
    }

    /** Пар межтрубья из питательной воды 104 °C, кг/с. */
    public double steamKgS(double dutyW) {
        if (dutyW <= 0) return 0;
        double pa = steamBar * 1e5, ts = If97.tsat(pa);
        double hg = If97.inRegion(2, pa, ts).h(), hf = If97.pt(pa, FEEDWATER_K).h();
        return dutyW / (hg - hf);
    }

    /** Старение и спекание меди по температуре слоя за {@code dtS} с. */
    private void age(double dtS, boolean onStream) {
        if (activity <= 0 || !onStream) return;
        double k = AGING_K_REF * Math.exp(-AGING_E / Species.R * (1 / bedK - 1 / AGING_T_REF));
        activity = activity / (1 + k * activity * dtS);
        if (bedK > MethanolSynthesis.SINTER_K)
            activity = Math.max(0, activity - OVERHEAT_LOSS_PER_H_10K * (bedK - MethanolSynthesis.SINTER_K) / 10 * dtS / 3600);
    }

    /** Сброс давления контура. */
    public void depressurize() {
        java.util.Arrays.fill(gas, 0);
    }

    public void save(StateWriter out) {
        for (Species sp : Species.values()) if (gas[sp.ordinal()] > 0) out.putDouble("n_" + sp.name(), gas[sp.ordinal()]);
        out.putDouble("circ", circulation);
        out.putDouble("purge", purgeMolS);
        out.putDouble("steam", steamBar);
        out.putDouble("act", activity);
        out.putDouble("bed", bedK);
        out.putBoolean("run", running);
    }

    public void load(StateReader in) {
        for (Species sp : Species.values()) gas[sp.ordinal()] = Math.max(0, in.optDouble("n_" + sp.name(), 0));
        setCirculation(in.optDouble("circ", 1));
        setPurgeMolS(in.optDouble("purge", 12));
        setSteamBar(in.optDouble("steam", 40));
        activity = Math.max(0, Math.min(1, in.optDouble("act", 0)));
        bedK = in.optDouble("bed", 300);
        running = in.has("run") && in.getBoolean("run");
    }
}
