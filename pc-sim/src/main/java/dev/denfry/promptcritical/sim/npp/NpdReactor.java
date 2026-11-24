package dev.denfry.promptcritical.sim.npp;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.fuel.FuelDesign;
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
 * NPD (Nuclear Power Demonstration, Rolphton, 1962–1987), первый CANDU: 92 МВт тепловых, 22 МВт электрических брутто,
 * 132 горизонтальных канала с трубами давления из Zircaloy-2 в алюминиевом каландре ⌀5,2 × 4,6 м с D₂O-замедлителем
 * низкого давления, перегрузка на ходу с обоих торцов (ScienceDirect, обзор NPD; IAEA-NDS, WIMS-D, ячейка NPD;
 * CANTEACH, «CANDU Origins and Evolution»). Решётка {@link LatticeDesigns#T3_NPD}, пучок {@link FuelDesign#NPD_19},
 * девять в канале.
 *
 * <p><b>Регулирование уровнем замедлителя.</b> Стержней у NPD не было. Мощность держали высотой D₂O в каландре: насосы
 * замедлителя поднимали воду из бака сброса под каландром, регулирующий клапан спускал её обратно; каналы над уровнем
 * не замедляются, и зона становится «ниже» ({@link LightWaterCore#setModeratedFraction}). Автомат ведёт мощность к
 * заданной, ограничивая период: целевая обратная величина периода пропорциональна логарифму отношения заданной и
 * фактической мощности и не больше 1/30 с⁻¹ на подъёме.
 *
 * <p><b>Останов — сброс замедлителя.</b> Защита открывает клапаны выравнивания гелия, и D₂O под своим весом уходит
 * через большие сбросные проёмы в бак: расход по Торричелли от высоты уровня над дном каландра. Обратный подъём —
 * только насосами, после сброса защиты и вручную; это десятки минут, за которые может набраться ксенон.
 *
 * <p><b>Теплоноситель</b> — D₂O под давлением, без кипения [оценка: 7,6 МПа, 247 → 277 °C — по 92 МВт и расходу около
 * 600 кг/с двумя насосами; NPD перевели на кипение только в 1968 г., CANTEACH 19710101]. Защита: период меньше 10 с,
 * мощность выше 110 %, расход ниже 70 % на мощности, внешний сигнал и кнопка.
 *
 * <p>Упрощения [для §0.13]: горизонтальный каландр считается тем же цилиндром {@link LightWaterCore}, что и
 * вертикальные зоны, и уровень режет высоту зоны, а не круг каналов; уровень замедлителя в долях диаметра каландра
 * переводится в долю «высоты» зоны по тому, сколько каналов под ним; гелий покровного газа не моделируется.
 */
public final class NpdReactor {
    public static final double DT = 0.05;
    public static final int CHANNELS = 132, BUNDLES_PER_CHANNEL = 9;
    public static final double NOMINAL_W = 92e6, P_COOLANT = 7.6e6, INLET_K = 520.15, OUTLET_K = 550.15, MODERATOR_K = 343.15;
    /** Плотность D₂O к H₂O при тех же P, T. */
    public static final double D2O_FACTOR = 1.105, D2O_DENSITY = 1085;
    public static final double TRIP_PERIOD_S = 10, TRIP_POWER = 1.10, TRIP_FLOW = 0.7;
    public static final double FUEL_R_K_PER_KW_M = 17.5, FUEL_C_J_PER_KG_HM = 400;
    public static final double SIGMA_D_CM2 = 0.519e-27, TRITIUM_HALF_LIFE_S = 12.32 * 365.25 * 86400;
    /** Каландр: внутренний диаметр и длина, м; зона — круг каналов ⌀3,4 м по оси каландра. */
    public static final double CALANDRIA_D = 5.15, CALANDRIA_L = 4.6, CORE_D = 3.4;
    /**
     * Насосы замедлителя: подъём из бака сброса, кг/с [оценка: каландр ≈ 85 т заполняется за ~20 мин]; регулирующий
     * клапан спуска — тот же расход.
     */
    public static final double PUMP_KG_S = 70, DRAIN_KG_S = 70;
    /** Сбросные проёмы: площадь, м², и коэффициент расхода [оценка: уровень падает на треть за ~3 с]. */
    public static final double DUMP_AREA_M2 = 2.5, DUMP_CD = 0.62;
    /** Автомат: наибольшая целевая обратная величина периода на подъёме и спуске, 1/с; усиление по ней. */
    public static final double MAX_RISE_PER_S = 1.0 / 30, MAX_FALL_PER_S = 1.0 / 20, LEVEL_GAIN = 40;
    /** Канал: проход трубы давления за вычетом 19 элементов; фидеры и коллекторы — ещё столько же [оценка]. */
    public static final double CHANNEL_FLOW_M2 = Math.PI * (0.041402 * 0.041402 - 19 * 0.007625 * 0.007625),
            BUNDLE_M = 0.495, COOLANT_HOT_DENSITY = 860, LOOP_FACTOR = 2;

    public enum Trip { NONE, PERIOD, POWER, FLOW, EXTERNAL, MANUAL }

    public enum LevelMode { MANUAL, AUTO }

    private static final WaterProperties W = WaterProperties.tables();
    public static final LightWaterCore.Spec SPEC = new LightWaterCore.Spec(LatticeDesigns.T3_NPD, CHANNELS, true,
            CellComposition.nonFuel(ModeratorKind.D2O, 1, CoolantKind.NONE, 0, 0), 0.85, BUNDLES_PER_CHANNEL, 0.00711,
            FuelDesign.NPD_19, EnumSet.of(FuelDesign.NPD_19));

    private final LightWaterCore core = new LightWaterCore(SPEC, NOMINAL_W, DT);
    private final ThermoState ts = new ThermoState();
    private final HeavyWaterInventory moderator = new HeavyWaterInventory(), dumpTank = new HeavyWaterInventory(),
            coolant = new HeavyWaterInventory();
    private Trip trip = Trip.MANUAL;
    private boolean externalTrip, pumpsPowered = true;
    private LevelMode mode = LevelMode.MANUAL;
    private int levelDrive;
    private double powerSetpoint = 1, levelRate;
    private double fuelK = INLET_K, flowCommand, flowActual, coolantVoid, outletK = INLET_K, coolantDensity;
    private double refuelledBundles, lightWaterIngressKgS;

    /** Реактор в работе: каландр полон реакторной D₂O, бак сброса пуст, контур залит. */
    public NpdReactor() {
        moderator.set(moderatorCapacityKg(), HeavyWater.REACTOR_GRADE_X);
        dumpTank.set(0, HeavyWater.REACTOR_GRADE_X);
        coolant.set(coolantCapacityKg(), HeavyWater.REACTOR_GRADE_X);
        levels();
    }

    /** Новый сухой реактор: тяжёлой воды нет нигде. */
    public static NpdReactor dry() {
        NpdReactor r = new NpdReactor();
        r.moderator.set(0, HeavyWater.REACTOR_GRADE_X);
        r.coolant.set(0, HeavyWater.REACTOR_GRADE_X);
        r.levels();
        return r;
    }

    public LightWaterCore core() {
        return core;
    }

    public static double channelKg(double bundleKg) {
        return bundleKg * BUNDLES_PER_CHANNEL;
    }

    /** Первая загрузка канала (9 пучков): только при сброшенном замедлителе или заглушенном реакторе. */
    public boolean loadChannel(int ch, double bundleKg, double u235, double u234) {
        return core.load(shutDown(), ch, FuelDesign.NPD_19, channelKg(bundleKg), u235, u234);
    }

    /**
     * Перегрузка на ходу: {@code bundles} свежих пучков в канал {@code ch} против течения, с другого торца выходят
     * столько же выгоревших. Возвращает выгруженное (средний состав канала) или null.
     */
    public LightWaterCore.AssemblyFuel refuel(int ch, int bundles, double bundleKg, double u235, double u234) {
        LightWaterCore.AssemblyFuel out = core.shift(ch, FuelDesign.NPD_19, bundles * bundleKg, u235, u234, channelKg(bundleKg));
        if (out != null) refuelledBundles += bundles;
        return out;
    }

    public int mostBurntChannel() {
        int best = -1;
        for (int i = 0; i < CHANNELS; i++)
            if (core.loaded(i) && (best < 0 || core.burnup(i) > core.burnup(best))) best = i;
        return best;
    }

    public boolean shutDown() {
        return core.power() < 1e-3 && (trip != Trip.NONE || moderatorLevel() < 0.5);
    }

    // ------------------------------------------------------------------ управление

    public void setLevelMode(LevelMode m) {
        mode = m;
        levelDrive = 0;
    }

    public LevelMode levelMode() {
        return mode;
    }

    /** Ручное: +1 — насосы поднимают уровень, −1 — клапан спускает, 0 — стоп. */
    public void driveLevel(int dir) {
        levelDrive = Integer.signum(dir);
    }

    public int levelDrive() {
        return levelDrive;
    }

    /** Заданная мощность автомата, доля номинала. */
    public void setPowerSetpoint(double n) {
        powerSetpoint = Math.max(1e-6, Math.min(1.05, n));
    }

    public double powerSetpoint() {
        return powerSetpoint;
    }

    /** Питание насосов замедлителя (собственные нужды): без него уровень не поднять. */
    public void setPumpsPowered(boolean on) {
        pumpsPowered = on;
    }

    public void setFlow(double f) {
        flowCommand = Math.max(0, Math.min(1.1, f));
    }

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

    /** Сброс защиты: закрывает сбросные клапаны; уровень поднимать насосами. */
    public boolean resetTrip() {
        if (trip == Trip.NONE) return true;
        if (cause() != Trip.NONE) return false;
        trip = Trip.NONE;
        mode = LevelMode.MANUAL;
        levelDrive = 0;
        return true;
    }

    public Trip trip() {
        return trip;
    }

    /** Сбросные клапаны открыты (защита сработала и не сброшена). */
    public boolean dumping() {
        return trip != Trip.NONE;
    }

    // ------------------------------------------------------------------ шаг

    public void step(double gameDt) {
        Trip c = cause();
        if (trip == Trip.NONE && c != Trip.NONE) trip = c;
        moderatorFlow();
        core.step(gameDt, feedbackState(), 0);
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

    /** Насосы, регулирующий клапан и сброс за шаг {@link #DT}. */
    private void moderatorFlow() {
        double up = 0, down = 0;
        if (trip != Trip.NONE) {
            double h = levelHeightM();
            down = D2O_DENSITY * DUMP_CD * DUMP_AREA_M2 * Math.sqrt(2 * dev.denfry.promptcritical.sim.combustion.Chimney.GRAVITY * Math.max(0, h));
        } else {
            double u = mode == LevelMode.AUTO ? autoCommand() : levelDrive;
            if (u > 0 && pumpsPowered) up = u * PUMP_KG_S;
            if (u < 0) down = -u * DRAIN_KG_S;
        }
        double moved = 0;
        if (up > 0) moved = transfer(dumpTank, moderator, Math.min(up * DT, moderatorCapacityKg() - moderator.kg()));
        if (down > 0) moved = -transfer(moderator, dumpTank, down * DT);
        levelRate = moved / DT / moderatorCapacityKg();
        levels();
    }

    private static double transfer(HeavyWaterInventory from, HeavyWaterInventory to, double kg) {
        if (kg <= 0 || from.kg() <= 0) return 0;
        double purity = from.purity(), tritium = from.tritiumAtoms(), share = Math.min(1, kg / from.kg());
        double out = from.remove(kg);
        to.add(out, purity);
        to.setTritiumAtoms(to.tritiumAtoms() + tritium * share);
        return out;
    }

    /** Команда автомата насосам и клапану, −1…+1: обратная величина периода к целевой. */
    private double autoCommand() {
        double n = Math.max(1e-9, core.power());
        double target = Math.max(-MAX_FALL_PER_S, Math.min(MAX_RISE_PER_S, 0.05 * Math.log(powerSetpoint / n)));
        double t = core.periodS();
        double omega = Double.isFinite(t) && Math.abs(t) > 1e-9 ? 1 / t : 0;
        return Math.max(-1, Math.min(1, LEVEL_GAIN * (target - omega)));
    }

    private CellFeedbackState feedbackState() {
        return core.state(fuelK, coolantDensity, MODERATOR_K, 0).withD2oPurity(moderator.purity(), coolant.purity());
    }

    private void properties() {
        double avg = (INLET_K + outletK) / 2;
        W.stateFromPressureEnthalpy(P_COOLANT, W.enthalpyPT(P_COOLANT, Math.min(avg, 560)), ts);
        coolantDensity = D2O_FACTOR * ts.density * (1 - Math.max(coolantVoid, 1 - coolantFill()));
    }

    /** Уровень в каландре → доля каналов под замедлителем (зона — круг ⌀{@link #CORE_D} по оси каландра). */
    private void levels() {
        double h = levelHeightM() - (CALANDRIA_D - CORE_D) / 2;
        core.setModeratedFraction(circleBelow(h / CORE_D));
        properties();
    }

    /** Доля площади круга единичного диаметра ниже уровня {@code x} (0…1 от дна). */
    static double circleBelow(double x) {
        if (x <= 0) return 0;
        if (x >= 1) return 1;
        double y = 2 * x - 1; // −1…1
        return 0.5 + (y * Math.sqrt(1 - y * y) + Math.asin(y)) / Math.PI;
    }

    // ------------------------------------------------------------------ тяжёлая вода

    /** Каландр полный, кг D₂O: цилиндр ⌀{@link #CALANDRIA_D} × {@link #CALANDRIA_L} за вычетом каландровых труб. */
    public double moderatorCapacityKg() {
        double v = Math.PI / 4 * CALANDRIA_D * CALANDRIA_D * CALANDRIA_L
                - CHANNELS * Math.PI * 0.0520827 * 0.0520827 * CALANDRIA_L;
        return D2O_DENSITY * v;
    }

    public double coolantCapacityKg() {
        return CHANNELS * CHANNEL_FLOW_M2 * BUNDLES_PER_CHANNEL * BUNDLE_M * COOLANT_HOT_DENSITY * LOOP_FACTOR;
    }

    public HeavyWaterInventory moderator() {
        return moderator;
    }

    public HeavyWaterInventory dumpTank() {
        return dumpTank;
    }

    public HeavyWaterInventory coolant() {
        return coolant;
    }

    /** Заполнение каландра, доля полного объёма. */
    public double moderatorLevel() {
        return Math.min(1, moderator.kg() / moderatorCapacityKg());
    }

    /** Высота уровня над дном каландра, м: обратная к площади сегмента горизонтального цилиндра. */
    public double levelHeightM() {
        double f = moderatorLevel(), lo = 0, hi = 1;
        if (f <= 0) return 0;
        if (f >= 1) return CALANDRIA_D;
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2;
            if (circleBelow(mid) < f) lo = mid;
            else hi = mid;
        }
        return (lo + hi) / 2 * CALANDRIA_D;
    }

    /** Скорость уровня за последний шаг, доля в секунду (+ подъём). */
    public double levelRate() {
        return levelRate;
    }

    public double coolantFill() {
        return Math.min(1, coolant.kg() / coolantCapacityKg());
    }

    /** Залить D₂O в бак сброса (система замедлителя заполняется через него); возвращает принятое, кг. */
    public double addModerator(double kg, double x) {
        double take = Math.max(0, Math.min(kg, moderatorCapacityKg() * 1.1 - dumpTank.kg() - moderator.kg()));
        dumpTank.add(take, x);
        return take;
    }

    public double addCoolant(double kg, double x) {
        double take = Math.max(0, Math.min(kg, coolantCapacityKg() - coolant.kg()));
        coolant.add(take, x);
        levels();
        return take;
    }

    /** Слить из бака сброса до {@code kg}; возвращает слитое, кг. */
    public double drainDumpTank(double kg) {
        return dumpTank.remove(kg);
    }

    public void setLightWaterIngress(double kgPerS) {
        lightWaterIngressKgS = Math.max(0, kgPerS);
    }

    public double lightWaterIngress() {
        return lightWaterIngressKgS;
    }

    private void ingress(double gameDt) {
        if (lightWaterIngressKgS <= 0 || gameDt <= 0) return;
        HeavyWaterInventory into = moderator.kg() > 0 ? moderator : dumpTank;
        into.add(lightWaterIngressKgS * gameDt, HeavyWater.NATURAL_X);
        if (into == moderator) moderator.remove(Math.max(0, moderator.kg() - moderatorCapacityKg()));
        levels();
    }

    private void thermal() {
        flowActual += (flowCommand - flowActual) * Math.min(1, DT / 15);
        double heat = core.heatW();
        double w = Math.max(0.03, flowActual * coolantFill());
        double ua = Math.max(1e3, core.rodLengthM() * 1000 / FUEL_R_K_PER_KW_M);
        double c = Math.max(1e5, FUEL_C_J_PER_KG_HM * core.heavyMetalKg());
        double cool = (INLET_K + outletK) / 2;
        double q = ua * (fuelK - cool);
        fuelK += ((core.loadedAssemblies() > 0 ? heat : 0) - q) / c * DT;
        outletK = INLET_K + (OUTLET_K - INLET_K) * Math.max(0, q) / NOMINAL_W / w;
        properties();
    }

    private void tritium(double gameDt) {
        if (gameDt <= 0) return;
        double lambda = Math.log(2) / TRITIUM_HALF_LIFE_S, decay = Math.exp(-lambda * gameDt), flux = core.averageFlux();
        // в каландре зона занимает долю (D_зоны/D_каландра)²; в отражателе поток считаем вдвое ниже
        double inCore = CORE_D * CORE_D / (CALANDRIA_D * CALANDRIA_D), weight = inCore + 0.5 * (1 - inCore);
        double modRate = SIGMA_D_CM2 * flux * moderator.deuteriumAtoms() * weight;
        moderator.setTritiumAtoms(moderator.tritiumAtoms() * decay + modRate / lambda * (1 - decay));
        dumpTank.setTritiumAtoms(dumpTank.tritiumAtoms() * decay);
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

    public double tritiumBq() {
        return (moderator.tritiumAtoms() + dumpTank.tritiumAtoms()) * Math.log(2) / TRITIUM_HALF_LIFE_S;
    }

    public double coolantTritiumBq() {
        return coolant.tritiumAtoms() * Math.log(2) / TRITIUM_HALF_LIFE_S;
    }

    public double moderatorTritiumBqPerKg() {
        double kg = moderator.kg() + dumpTank.kg();
        return kg > 0 ? tritiumBq() / kg : 0;
    }

    public double tritiumGrams() {
        return (moderator.tritiumAtoms() + dumpTank.tritiumAtoms()) * 3.016 / NuclideVector.AVOGADRO;
    }

    public double refuelledBundles() {
        return refuelledBundles;
    }

    public double steamGeneratorW() {
        return Math.max(0, core.heatW());
    }

    /** Реактивность при текущем уровне (статический расчёт). */
    public double evaluateReactivity() {
        CellFeedbackState s = feedbackState();
        core.solveStatic(s);
        return core.reactivity(s, 0);
    }

    /**
     * Критический уровень при текущем состоянии топлива: доля полного каландра, на которой реактивность нулевая;
     * 1 — даже полный каландр подкритичен; уровень после поиска возвращается на место.
     */
    public double criticalLevel() {
        double keep = moderator.kg();
        double lo = 0, hi = 1;
        setLevelKg(hi * moderatorCapacityKg());
        double result;
        if (evaluateReactivity() < 0) result = 1;
        else {
            for (int i = 0; i < 30; i++) {
                double mid = (lo + hi) / 2;
                setLevelKg(mid * moderatorCapacityKg());
                if (evaluateReactivity() > 0) hi = mid;
                else lo = mid;
            }
            result = (lo + hi) / 2;
        }
        setLevelKg(keep);
        return result;
    }

    private void setLevelKg(double kg) {
        double x = moderator.purity(), t = moderator.tritiumAtoms();
        moderator.set(kg, x);
        moderator.setTritiumAtoms(t);
        levels();
    }

    /**
     * Горячее равновесие на мощности {@code n}: расход номинальный, топливо по T_eff, уровень — критический (недостача
     * D₂O в каландре уходит в бак сброса), автомат держит {@code n}.
     */
    public void hotStart(double n) {
        flowCommand = flowActual = 1;
        outletK = INLET_K + (OUTLET_K - INLET_K) * n;
        fuelK = (INLET_K + outletK) / 2 + n * NOMINAL_W / Math.max(1, core.rodLengthM() * 1000 / FUEL_R_K_PER_KW_M);
        trip = Trip.NONE;
        properties();
        double total = moderator.kg() + dumpTank.kg();
        double lvl = criticalLevel();
        double kg = Math.min(total, lvl * moderatorCapacityKg());
        setLevelKg(kg);
        dumpTank.set(total - kg, moderator.purity());
        powerSetpoint = n;
        mode = LevelMode.AUTO;
        core.equilibrate(feedbackState(), n);
    }

    public void save(StateWriter out) {
        core.save(out);
        out.putDouble("tf", fuelK);
        out.putDouble("tout", outletK);
        out.putDouble("flow", flowCommand);
        out.putDouble("flow_act", flowActual);
        moderator.save(out, "mod_d2o");
        dumpTank.save(out, "dump_d2o");
        coolant.save(out, "cool_d2o");
        out.putDouble("ingress", lightWaterIngressKgS);
        out.putDouble("refuelled", refuelledBundles);
        out.putDouble("setpoint", powerSetpoint);
        out.putString("mode", mode.name());
        out.putString("trip", trip.name());
    }

    public void load(StateReader in) {
        fuelK = in.optDouble("tf", fuelK);
        outletK = in.optDouble("tout", INLET_K);
        flowCommand = in.optDouble("flow", 0);
        flowActual = in.optDouble("flow_act", 0);
        moderator.load(in, "mod_d2o");
        dumpTank.load(in, "dump_d2o");
        coolant.load(in, "cool_d2o");
        lightWaterIngressKgS = Math.max(0, in.optDouble("ingress", 0));
        refuelledBundles = in.optDouble("refuelled", 0);
        powerSetpoint = in.optDouble("setpoint", 1);
        mode = parse(LevelMode.class, in.optString("mode", "MANUAL"), LevelMode.MANUAL);
        trip = parse(Trip.class, in.optString("trip", "MANUAL"), Trip.MANUAL);
        levels();
        core.load(in, this::feedbackState);
    }

    private static <E extends Enum<E>> E parse(Class<E> c, String s, E fallback) {
        try {
            return Enum.valueOf(c, s);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
