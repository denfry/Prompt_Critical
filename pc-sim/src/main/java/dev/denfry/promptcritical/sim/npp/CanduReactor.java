package dev.denfry.promptcritical.sim.npp;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.fuel.FuelDesign;
import dev.denfry.promptcritical.sim.neutronics.CellComposition;
import dev.denfry.promptcritical.sim.neutronics.CellFeedbackState;
import dev.denfry.promptcritical.sim.neutronics.CoolantKind;
import dev.denfry.promptcritical.sim.neutronics.LatticeDesigns;
import dev.denfry.promptcritical.sim.neutronics.ModeratorKind;
import dev.denfry.promptcritical.sim.nuclide.NuclideVector;
import dev.denfry.promptcritical.sim.thermo.ThermoState;
import dev.denfry.promptcritical.sim.thermo.WaterProperties;
import java.util.EnumSet;

/**
 * Тяжеловодный канальный реактор T3 (CANDU, 00 §0.5): горизонтальные каналы с пучками природного UO₂ ({@link
 * FuelDesign#CANDU_37}, 12 в канале) в каландре с холодным D₂O-замедлителем; D₂O-теплоноситель 10 МПа, 266 → 310 °C
 * (AECL, «CANDU 6 Technical Summary», 2005). Зона — та же {@link LightWaterCore} с решёткой
 * {@link LatticeDesigns#T3_CLUSTER_D2O}, квадратной сеткой каналов шагом 286 мм и отражателем D₂O 0,65 м.
 *
 * <p><b>Физика T3.</b> Время жизни нейтронов ~10⁻³ с — у D₂O медленное замедление и длинная диффузия. Пустотный
 * коэффициент теплоносителя положительный: убыль D₂O в канале уменьшает резонансный захват при слабом вкладе
 * теплоносителя в замедление (полное осушение +10…+15 mk, Rouben, «CANDU Fuel Management», 1999; 01 §1.7.5). Бор и
 * гадолиний в замедлителе не моделируются: запас реактивности мал, его держит перегрузка на ходу.
 *
 * <p><b>Перегрузка на ходу</b> — {@link #refuel}: машина вдвигает пучки в канал, с другого конца выходят выгоревшие,
 * реактивность растёт. <b>Тритий</b> — активация дейтерия замедлителя D(n,γ)T, σ = 0,519 мб (ENDF/B-VII.1), распад
 * 12,32 г.: dN/dt = σφN_D·(доля D₂O в потоке) − λN.
 *
 * <p><b>Защита</b> — как у СУЗ T1: период меньше 10 с, мощность выше 110 %, расход ниже 70 % на мощности, внешний
 * сигнал и кнопка; стержни аварийного останова падают за 2 с. Регулирующие поглотители (жидкостные зоны и
 * регуляторы) — одной группой с приводом.
 */
public final class CanduReactor {
    public static final double DT = 0.05;
    /** Каналов: квадратная сетка, ближайшие к оси первыми. */
    public static final int CHANNELS = 97, BUNDLES_PER_CHANNEL = 12, SHIFT_BUNDLES = 8;
    public static final double NOMINAL_W = 100e6, P_COOLANT = 10e6, INLET_K = 539.15, OUTLET_K = 583.15, MODERATOR_K = 343.15;
    /** Плотность D₂O к H₂O при тех же P, T (отношение молярных масс 20,03/18,02 при близких молярных объёмах). */
    public static final double D2O_FACTOR = 1.105;
    public static final double WORTH_SHUTOFF = 0.08, WORTH_CONTROL = 0.03, SHUTOFF_S = 2.0, CONTROL_FRAC_S = 1.0 / 60;
    public static final double TRIP_PERIOD_S = 10, TRIP_POWER = 1.10, TRIP_FLOW = 0.7;
    public static final double FUEL_R_K_PER_KW_M = 17.5, FUEL_C_J_PER_KG_HM = 400;
    /** D(n,γ)T: сечение, см²; период трития, с; молярная масса D₂O, кг/моль. */
    public static final double SIGMA_D_CM2 = 0.519e-27, TRITIUM_HALF_LIFE_S = 12.32 * 365.25 * 86400, M_D2O = 20.03e-3;
    public static final double D2O_DENSITY = 1085;

    public enum Trip { NONE, PERIOD, POWER, FLOW, EXTERNAL, MANUAL }

    private static final WaterProperties W = WaterProperties.tables();
    public static final LightWaterCore.Spec SPEC = new LightWaterCore.Spec(LatticeDesigns.T3_CLUSTER_D2O, CHANNELS, true,
            CellComposition.nonFuel(ModeratorKind.D2O, 1, CoolantKind.NONE, 0, 0), 0.65, BUNDLES_PER_CHANNEL, 0.00711,
            FuelDesign.CANDU_37, EnumSet.of(FuelDesign.CANDU_37));

    private final LightWaterCore core = new LightWaterCore(SPEC, NOMINAL_W, DT);
    private final ThermoState ts = new ThermoState();
    private double shutoff = 1, control = 1, shutoffSpeed;
    private int controlDrive;
    private Trip trip = Trip.MANUAL;
    private boolean externalTrip;
    private double fuelK = 539.15, flowCommand, flowActual, coolantVoid, outletK = INLET_K, coolantDensity;
    private double tritiumAtoms, refuelledBundles;

    public CanduReactor() {
        properties();
    }

    public LightWaterCore core() {
        return core;
    }

    /** Масса урана полного канала из пучков {@code bundleKg}. */
    public static double channelKg(double bundleKg) {
        return bundleKg * BUNDLES_PER_CHANNEL;
    }

    /** Первая загрузка канала целиком (12 пучков): только на заглушенном реакторе. */
    public boolean loadChannel(int ch, double bundleKg, double u235, double u234) {
        return core.load(shutDown(), ch, FuelDesign.CANDU_37, channelKg(bundleKg), u235, u234);
    }

    /**
     * Перегрузка на ходу: {@code bundles} свежих пучков в канал {@code ch}; полный канал выдаёт столько же выгоревших.
     * Возвращает выгруженное (среднего состава канала) или null.
     */
    public LightWaterCore.AssemblyFuel refuel(int ch, int bundles, double bundleKg, double u235, double u234) {
        refuelledBundles += bundles;
        return core.shift(ch, FuelDesign.CANDU_37, bundles * bundleKg, u235, u234, channelKg(bundleKg));
    }

    /** Канал с наибольшим выгоранием среди загруженных — кандидат на перегрузку; −1 — нет загруженных. */
    public int mostBurntChannel() {
        int best = -1;
        for (int i = 0; i < CHANNELS; i++)
            if (core.loaded(i) && (best < 0 || core.burnup(i) > core.burnup(best))) best = i;
        return best;
    }

    public boolean shutDown() {
        return core.power() < 1e-3 && shutoff > 0.99;
    }

    public void driveControl(int dir) {
        controlDrive = Integer.signum(dir);
    }

    public int controlDrive() {
        return controlDrive;
    }

    /** Насосы теплоносителя: расход, доля номинала. */
    public void setFlow(double f) {
        flowCommand = Math.max(0, Math.min(1.1, f));
    }

    /** Доля пара (осушения) в каналах — для аварий с потерей теплоносителя и тестов. */
    public void setCoolantVoid(double v) {
        coolantVoid = Math.max(0, Math.min(1, v));
        properties();
    }

    public void setExternalTrip(boolean on) {
        externalTrip = on;
    }

    public void scram() {
        if (trip == Trip.NONE) trip = Trip.MANUAL;
    }

    public boolean resetTrip() {
        if (trip == Trip.NONE) return true;
        if (cause() != Trip.NONE || shutoff < 0.99) return false;
        trip = Trip.NONE;
        return true;
    }

    public Trip trip() {
        return trip;
    }

    public void setRods(double shutoff, double control) {
        this.shutoff = shutoff;
        this.control = control;
    }

    // ------------------------------------------------------------------ шаг

    public void step(double gameDt) {
        Trip c = cause();
        if (trip == Trip.NONE && c != Trip.NONE) trip = c;
        moveRods();
        core.step(gameDt, feedbackState(), rodReactivity());
        thermal();
        tritium(gameDt);
    }

    private Trip cause() {
        if (externalTrip) return Trip.EXTERNAL;
        double t = core.periodS();
        if (t > 0 && t < TRIP_PERIOD_S && core.power() > 1e-6) return Trip.PERIOD;
        if (core.power() > TRIP_POWER) return Trip.POWER;
        if (core.power() > 0.1 && flowActual < TRIP_FLOW) return Trip.FLOW;
        return Trip.NONE;
    }

    private static double sCurve(double x) {
        return x - Math.sin(2 * Math.PI * x) / (2 * Math.PI);
    }

    public double rodReactivity() {
        return -(WORTH_SHUTOFF * sCurve(shutoff) + WORTH_CONTROL * sCurve(control));
    }

    private void moveRods() {
        if (trip != Trip.NONE) {
            shutoffSpeed += 2 / (SHUTOFF_S * SHUTOFF_S) * DT; // равноускоренно: полный ход за SHUTOFF_S
            shutoff = Math.min(1, shutoff + shutoffSpeed * DT);
            control = Math.min(1, control + CONTROL_FRAC_S * 4 * DT);
            controlDrive = 0;
            return;
        }
        shutoffSpeed = 0;
        if (shutoff > 0) shutoff = Math.max(0, shutoff - CONTROL_FRAC_S * DT * 2); // взвод
        control = Math.max(0, Math.min(1, control + controlDrive * CONTROL_FRAC_S * DT));
    }

    private CellFeedbackState feedbackState() {
        return core.state(fuelK, coolantDensity, MODERATOR_K, 0);
    }

    private void properties() {
        double avg = (INLET_K + outletK) / 2;
        W.stateFromPressureEnthalpy(P_COOLANT, W.enthalpyPT(P_COOLANT, Math.min(avg, 583)), ts);
        coolantDensity = D2O_FACTOR * ts.density * (1 - coolantVoid);
    }

    private void thermal() {
        flowActual += (flowCommand - flowActual) * Math.min(1, DT / 15);
        double heat = core.heatW();
        double w = Math.max(0.03, flowActual);
        double ua = Math.max(1e3, core.rodLengthM() * 1000 / FUEL_R_K_PER_KW_M);
        double c = Math.max(1e5, FUEL_C_J_PER_KG_HM * core.heavyMetalKg());
        double cool = (INLET_K + outletK) / 2;
        double q = ua * (fuelK - cool);
        fuelK += ((core.loadedAssemblies() > 0 ? heat : 0) - q) / c * DT;
        // ПГ держат вход 266 °C; подогрев — по расходу (номинал 44 К на 100 МВт)
        outletK = INLET_K + (OUTLET_K - INLET_K) * Math.max(0, q) / NOMINAL_W / w;
        properties();
    }

    /** Масса D₂O замедлителя и отражателя, кг: 83 % объёма решётки плюс оболочка отражателя. */
    public double moderatorKg() {
        double r = core.coreRadius(), h = core.coreHeight(), t = SPEC.reflectorM();
        double vReflector = Math.PI * ((r + t) * (r + t) * (h + 2 * t) - r * r * h);
        return D2O_DENSITY * (0.83 * core.coreVolumeM3() + vReflector);
    }

    private void tritium(double gameDt) {
        if (gameDt <= 0) return;
        double lambda = Math.log(2) / TRITIUM_HALF_LIFE_S;
        // дейтерий в потоке зоны: замедлитель внутри решётки, в отражателе поток считаем вдвое ниже
        double inCore = D2O_DENSITY * 0.83 * core.coreVolumeM3(), reflector = moderatorKg() - inCore;
        double deuterons = 2 * (inCore + 0.5 * reflector) / M_D2O * NuclideVector.AVOGADRO;
        double rate = SIGMA_D_CM2 * core.averageFlux() * deuterons;
        tritiumAtoms = tritiumAtoms * Math.exp(-lambda * gameDt) + rate / lambda * (1 - Math.exp(-lambda * gameDt));
    }

    // ------------------------------------------------------------------ показания

    public double power() {
        return core.power();
    }

    public double fuelK() {
        return fuelK;
    }

    public double outletK() {
        return outletK;
    }

    public double coolantDensity() {
        return coolantDensity;
    }

    public double flowFraction() {
        return flowActual;
    }

    public double shutoffPosition() {
        return shutoff;
    }

    public double controlPosition() {
        return control;
    }

    /** Тритий в замедлителе, Бк. */
    public double tritiumBq() {
        return tritiumAtoms * Math.log(2) / TRITIUM_HALF_LIFE_S;
    }

    /** Тритий в замедлителе, г. */
    public double tritiumGrams() {
        return tritiumAtoms * 3.016 / NuclideVector.AVOGADRO;
    }

    public double refuelledBundles() {
        return refuelledBundles;
    }

    /** Тепло в ПГ, Вт. */
    public double steamGeneratorW() {
        return Math.max(0, core.heatW());
    }

    public double evaluateReactivity() {
        CellFeedbackState s = feedbackState();
        core.solveStatic(s);
        return core.reactivity(s, rodReactivity());
    }

    /** Горячее равновесие на мощности {@code n}: расход номинальный, топливо по T_eff. */
    public void hotStart(double n) {
        flowCommand = flowActual = 1;
        outletK = INLET_K + (OUTLET_K - INLET_K) * n;
        fuelK = (INLET_K + outletK) / 2 + n * NOMINAL_W / Math.max(1, core.rodLengthM() * 1000 / FUEL_R_K_PER_KW_M);
        trip = Trip.NONE;
        properties();
        core.equilibrate(feedbackState(), n);
    }

    public void save(StateWriter out) {
        core.save(out);
        out.putDouble("so", shutoff);
        out.putDouble("ctrl", control);
        out.putDouble("tf", fuelK);
        out.putDouble("tout", outletK);
        out.putDouble("flow", flowCommand);
        out.putDouble("flow_act", flowActual);
        out.putDouble("tritium", tritiumAtoms);
        out.putDouble("refuelled", refuelledBundles);
        out.putString("trip", trip.name());
    }

    public void load(StateReader in) {
        shutoff = in.optDouble("so", 1);
        control = in.optDouble("ctrl", 1);
        fuelK = in.optDouble("tf", fuelK);
        outletK = in.optDouble("tout", INLET_K);
        flowCommand = in.optDouble("flow", 0);
        flowActual = in.optDouble("flow_act", 0);
        tritiumAtoms = in.optDouble("tritium", 0);
        refuelledBundles = in.optDouble("refuelled", 0);
        try {
            trip = Trip.valueOf(in.optString("trip", Trip.MANUAL.name()));
        } catch (IllegalArgumentException e) {
            trip = Trip.MANUAL;
        }
        properties();
        core.load(in, this::feedbackState);
    }
}
