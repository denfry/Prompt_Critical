package dev.denfry.promptcritical.sim.npp;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.fuel.FuelDesign;
import dev.denfry.promptcritical.sim.nuclide.PlutoniumCharge;
import dev.denfry.promptcritical.sim.heavywater.HeavyWater;
import dev.denfry.promptcritical.sim.heavywater.HeavyWaterInventory;
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
 * <p><b>Тяжёлая вода</b> — две инвентаризации {@link HeavyWaterInventory}: замедлитель каландра и теплоноситель
 * каналов. Обе заливают снаружи ({@link #addModerator}, {@link #addCoolant}); новый реактор ({@link #dry}) сухой и
 * подкритичен. Уровень замедлителя — доля высоты зоны под ним ({@link LightWaterCore#setModeratedFraction});
 * недолитый контур — то же осушение каналов, что и при потере теплоносителя (положительная реактивность). Изотопная
 * чистота обеих сред входит в поглощение и замедление ячейки ({@link CellFeedbackState#withD2oPurity}): лёгкая вода
 * в замедлителе отнимает ~12 мк на атомный процент. Течь H₂O ({@link #setLightWaterIngress}) и долив воды
 * низшего сорта разбавляют D₂O; слив ({@link #drainModerator}) уносит вместе с водой и тритий.
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
    /**
     * Теплоноситель в канале: проходное сечение трубы давления ⌀103,4 мм за вычетом 37 твэлов ⌀13,08 мм, длина 12
     * пучков по 495,3 мм (AECL, «CANDU 6 Technical Summary», 2005); плотность D₂O при 10 МПа и ~290 °C — 823 кг/м³
     * ({@link #D2O_FACTOR} × H₂O). Вне зоны — фидеры и коллекторы ещё столько же [оценка]; насосы и ПГ в мире — свои
     * блоки, их D₂O не считается.
     */
    public static final double CHANNEL_FLOW_M2 = Math.PI / 4 * (0.1034 * 0.1034 - 37 * 0.01308 * 0.01308),
            BUNDLE_M = 0.4953, COOLANT_HOT_DENSITY = 823, LOOP_FACTOR = 2;

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
    private double refuelledBundles;
    private final HeavyWaterInventory moderator = new HeavyWaterInventory(), coolant = new HeavyWaterInventory();
    /** Течь лёгкой воды в замедлитель, кг/с (теплообменник, влага покровного газа, ремонт). */
    private double lightWaterIngressKgS;

    /** Реактор в работе: замедлитель и контур залиты реакторной D₂O. */
    public CanduReactor() {
        moderator.set(moderatorCapacityKg(), HeavyWater.REACTOR_GRADE_X);
        coolant.set(coolantCapacityKg(), HeavyWater.REACTOR_GRADE_X);
        levels();
    }

    /** Новый сухой реактор: тяжёлой воды нет, пока её не зальют. */
    public static CanduReactor dry() {
        CanduReactor r = new CanduReactor();
        r.moderator.set(0, HeavyWater.REACTOR_GRADE_X);
        r.coolant.set(0, HeavyWater.REACTOR_GRADE_X);
        r.levels();
        return r;
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
     * Первая загрузка канала пучками МОКС: в пучке {@code bundleUKg} урана (доли U-235 и U-234 в уране) и плутоний
     * {@code bundlePu}. CANDU сжигает МОКС на природном или обеднённом уране без переделки зоны — пучок той же
     * геометрии (AECL, Dimayuga, «Plutonium disposition in CANDU», 1997; Chan et al., IAEA-SM-358, 1999).
     */
    public boolean loadChannel(int ch, double bundleUKg, double u235, double u234, PlutoniumCharge bundlePu) {
        return core.load(shutDown(), ch, FuelDesign.CANDU_37, channelKg(bundleUKg), u235, u234,
                bundlePu.scaled(BUNDLES_PER_CHANNEL));
    }

    /** Перегрузка на ходу пучками МОКС (см. {@link #refuel(int, int, double, double, double)}). */
    public LightWaterCore.AssemblyFuel refuel(int ch, int bundles, double bundleUKg, double u235, double u234, PlutoniumCharge bundlePu) {
        LightWaterCore.AssemblyFuel out = core.shift(ch, FuelDesign.CANDU_37, bundles * bundleUKg, u235, u234,
                bundlePu.scaled(bundles), channelKg(bundleUKg + bundlePu.totalKg()));
        refuelledBundles += bundles;
        return out;
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
        ingress(gameDt);
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
        return core.state(fuelK, coolantDensity, MODERATOR_K, 0).withD2oPurity(moderator.purity(), coolant.purity());
    }

    private void properties() {
        double avg = (INLET_K + outletK) / 2;
        W.stateFromPressureEnthalpy(P_COOLANT, W.enthalpyPT(P_COOLANT, Math.min(avg, 583)), ts);
        coolantDensity = D2O_FACTOR * ts.density * (1 - Math.max(coolantVoid, 1 - coolantFill()));
    }

    /** Уровень замедлителя и плотность теплоносителя по инвентаризациям D₂O. */
    private void levels() {
        core.setModeratedFraction(moderatorLevel());
        properties();
    }

    // ------------------------------------------------------------------ тяжёлая вода

    /** Каландр с отражателем, полный, кг D₂O: 83 % объёма решётки всех каналов плюс оболочка отражателя. */
    public double moderatorCapacityKg() {
        double r = core.fullCoreRadius(), h = core.coreHeight(), t = SPEC.reflectorM();
        double vReflector = Math.PI * ((r + t) * (r + t) * (h + 2 * t) - r * r * h);
        return D2O_DENSITY * (0.83 * Math.PI * r * r * h + vReflector);
    }

    /** Теплоноситель в каналах и фидерах, полный, кг D₂O. */
    public double coolantCapacityKg() {
        return CHANNELS * CHANNEL_FLOW_M2 * BUNDLES_PER_CHANNEL * BUNDLE_M * COOLANT_HOT_DENSITY * LOOP_FACTOR;
    }

    public HeavyWaterInventory moderator() {
        return moderator;
    }

    public HeavyWaterInventory coolant() {
        return coolant;
    }

    /** Уровень замедлителя, доля полного. */
    public double moderatorLevel() {
        return Math.min(1, moderator.kg() / moderatorCapacityKg());
    }

    public double coolantFill() {
        return Math.min(1, coolant.kg() / coolantCapacityKg());
    }

    /** Долить в каландр {@code kg} воды с долей D {@code x}; возвращает принятое (до полного), кг. */
    public double addModerator(double kg, double x) {
        double take = Math.max(0, Math.min(kg, moderatorCapacityKg() - moderator.kg()));
        moderator.add(take, x);
        levels();
        return take;
    }

    /** Долить в контур теплоносителя; возвращает принятое, кг. */
    public double addCoolant(double kg, double x) {
        double take = Math.max(0, Math.min(kg, coolantCapacityKg() - coolant.kg()));
        coolant.add(take, x);
        levels();
        return take;
    }

    /** Слить из каландра до {@code kg}; возвращает слитое, кг (доля D — {@code moderator().purity()}). */
    public double drainModerator(double kg) {
        double out = moderator.remove(kg);
        levels();
        return out;
    }

    public void setLightWaterIngress(double kgPerS) {
        lightWaterIngressKgS = Math.max(0, kgPerS);
    }

    public double lightWaterIngress() {
        return lightWaterIngressKgS;
    }

    private void ingress(double gameDt) {
        if (lightWaterIngressKgS <= 0 || gameDt <= 0 || moderator.kg() <= 0) return;
        // течь вытесняет столько же через переливную линию: уровень держится, чистота падает
        moderator.add(lightWaterIngressKgS * gameDt, HeavyWater.NATURAL_X);
        moderator.remove(Math.max(0, moderator.kg() - moderatorCapacityKg()));
        levels();
    }

    private void thermal() {
        flowActual += (flowCommand - flowActual) * Math.min(1, DT / 15);
        double heat = core.heatW();
        // недолитый контур: насосы гонят газ, тепло уносит только залитая доля
        double w = Math.max(0.03, flowActual * coolantFill());
        double ua = Math.max(1e3, core.rodLengthM() * 1000 / FUEL_R_K_PER_KW_M);
        double c = Math.max(1e5, FUEL_C_J_PER_KG_HM * core.heavyMetalKg());
        double cool = (INLET_K + outletK) / 2;
        double q = ua * (fuelK - cool);
        fuelK += ((core.loadedAssemblies() > 0 ? heat : 0) - q) / c * DT;
        // ПГ держат вход 266 °C; подогрев — по расходу (номинал 44 К на 100 МВт)
        outletK = INLET_K + (OUTLET_K - INLET_K) * Math.max(0, q) / NOMINAL_W / w;
        properties();
    }

    /** Масса D₂O в каландре сейчас, кг. */
    public double moderatorKg() {
        return moderator.kg();
    }

    private void tritium(double gameDt) {
        if (gameDt <= 0) return;
        double lambda = Math.log(2) / TRITIUM_HALF_LIFE_S, decay = Math.exp(-lambda * gameDt), flux = core.averageFlux();
        // дейтерий в потоке зоны: замедлитель внутри решётки, в отражателе поток считаем вдвое ниже
        double r = core.fullCoreRadius(), inCore = D2O_DENSITY * 0.83 * Math.PI * r * r * core.coreHeight();
        double cap = moderatorCapacityKg(), weight = (inCore + 0.5 * (cap - inCore)) / cap;
        double modRate = SIGMA_D_CM2 * flux * moderator.deuteriumAtoms() * weight;
        moderator.setTritiumAtoms(moderator.tritiumAtoms() * decay + modRate / lambda * (1 - decay));
        // теплоноситель: в зоне только доля 1/LOOP_FACTOR контура
        double coolRate = SIGMA_D_CM2 * flux * coolant.deuteriumAtoms() / LOOP_FACTOR;
        coolant.setTritiumAtoms(coolant.tritiumAtoms() * decay + coolRate / lambda * (1 - decay));
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
        return moderator.tritiumAtoms() * Math.log(2) / TRITIUM_HALF_LIFE_S;
    }

    /** Тритий в теплоносителе, Бк. */
    public double coolantTritiumBq() {
        return coolant.tritiumAtoms() * Math.log(2) / TRITIUM_HALF_LIFE_S;
    }

    /** Удельная активность трития в замедлителе, Бк/кг; 0 — пустой каландр. */
    public double moderatorTritiumBqPerKg() {
        return moderator.kg() > 0 ? tritiumBq() / moderator.kg() : 0;
    }

    /** Тритий в замедлителе, г. */
    public double tritiumGrams() {
        return moderator.tritiumAtoms() * 3.016 / NuclideVector.AVOGADRO;
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
        moderator.save(out, "mod_d2o");
        coolant.save(out, "cool_d2o");
        out.putDouble("ingress", lightWaterIngressKgS);
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
        // сохранения до учёта D₂O: реактор был залит, тритий — в замедлителе
        moderator.set(moderatorCapacityKg(), HeavyWater.REACTOR_GRADE_X);
        moderator.setTritiumAtoms(in.optDouble("tritium", 0));
        coolant.set(coolantCapacityKg(), HeavyWater.REACTOR_GRADE_X);
        moderator.load(in, "mod_d2o");
        coolant.load(in, "cool_d2o");
        lightWaterIngressKgS = Math.max(0, in.optDouble("ingress", 0));
        refuelledBundles = in.optDouble("refuelled", 0);
        try {
            trip = Trip.valueOf(in.optString("trip", Trip.MANUAL.name()));
        } catch (IllegalArgumentException e) {
            trip = Trip.MANUAL;
        }
        levels();
        core.load(in, this::feedbackState);
    }
}
