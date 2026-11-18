package dev.denfry.promptcritical.sim.npp;

import dev.denfry.promptcritical.sim.reactor.pwr.CoolantPump;
import dev.denfry.promptcritical.sim.reactor.pwr.Pressurizer;
import dev.denfry.promptcritical.sim.reactor.pwr.SecondarySide;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.fuel.FuelDesign;
import dev.denfry.promptcritical.sim.neutronics.CellComposition;
import dev.denfry.promptcritical.sim.neutronics.CellFeedbackState;
import dev.denfry.promptcritical.sim.neutronics.CoolantKind;
import dev.denfry.promptcritical.sim.neutronics.DecayHeat;
import dev.denfry.promptcritical.sim.neutronics.DelayedGroups;
import dev.denfry.promptcritical.sim.neutronics.DelayedNeutronData;
import dev.denfry.promptcritical.sim.neutronics.FissionPoisons;
import dev.denfry.promptcritical.sim.neutronics.LatticeDesigns;
import dev.denfry.promptcritical.sim.neutronics.LatticeModel;
import dev.denfry.promptcritical.sim.neutronics.ModeratorKind;
import dev.denfry.promptcritical.sim.neutronics.PeriodMeter;
import dev.denfry.promptcritical.sim.neutronics.PointKinetics;
import dev.denfry.promptcritical.sim.neutronics.ReactorShape;
import dev.denfry.promptcritical.sim.neutronics.SixFactor;
import dev.denfry.promptcritical.sim.neutronics.Westcott;
import dev.denfry.promptcritical.sim.nuclide.NuclideVector;
import dev.denfry.promptcritical.sim.protection.DnbrCalculator;
import dev.denfry.promptcritical.sim.protection.DnbrSignal;
import dev.denfry.promptcritical.sim.protection.HotChannelModel;
import dev.denfry.promptcritical.sim.protection.PlantNominals;
import dev.denfry.promptcritical.sim.protection.ProtectionAction;
import dev.denfry.promptcritical.sim.protection.ProtectionConfig;
import dev.denfry.promptcritical.sim.protection.ProtectionEventSink;
import dev.denfry.promptcritical.sim.protection.ProtectionInputs;
import dev.denfry.promptcritical.sim.protection.ProtectionMode;
import dev.denfry.promptcritical.sim.protection.ProtectionSystem;
import dev.denfry.promptcritical.sim.protection.TripReason;
import dev.denfry.promptcritical.sim.protection.TripSource;
import dev.denfry.promptcritical.sim.reactor.FuelDepletion;
import dev.denfry.promptcritical.sim.thermo.SaturationState;
import dev.denfry.promptcritical.sim.thermo.ThermoState;
import dev.denfry.promptcritical.sim.thermo.WaterProperties;

/**
 * Стартовый блок с водой под давлением T2a (00 §0.5, R5): 100 МВт(т), корпус на 19 ТВС ВВЭР/PWR, два петли с ГЦН и
 * горизонтальными ПГ, компенсатор давления, борное регулирование. Параметры первого контура — как у ВВЭР-1000:
 * 15,7 МПа, 290 → 320 °C (Денисов, Драгунов, «Реакторные установки ВВЭР для АЭС», 2002).
 *
 * <p><b>Нейтроника.</b> Ячейка — {@link LatticeModel} решётки {@link LatticeDesigns#T2_HEX} по среднему составу ТВС с
 * бором, плотностью и температурой воды, Xe-135 и Sm-149; пересчёт раз в секунду, между пересчётами — линейно по
 * производным (Доплер, плотность и температура воды, бор). k_eff = k∞/(1 + M²B²) цилиндра 19 ТВС с водяным
 * отражателем 0,3 м. Кинетика — {@link PointKinetics} (группы по долям делений, Λ из решётки).
 *
 * <p><b>Первый контур.</b> Топливо — сосредоточенная ёмкость (UO₂ и оболочки), термическое сопротивление твэла
 * 17,5 К на кВт/м до T_eff по Роуландсу [UO₂ λ ≈ 3 Вт/(м·К): q'/4πλ·4/9 + зазор и оболочка]. Вода — два узла с
 * перемешиванием (горячий: выход зоны и горячие нитки; холодный: ПГ, холодные нитки, опускной участок) по энтальпии
 * при давлении КД. Объём петель постоянен: тепловое расширение уходит в КД (insurge) и обратно — отсюда реакция
 * давления и уровня на изменение нагрузки. ПГ — теплообменник с кипящим вторым контуром: Q = ε·W·(h_гор − h(T_s)),
 * ε = 1 − e^(−NTU), UA подобран на номинал (ε = 0,746 при T_s = 279,8 °C, 01 §1.14.1).
 *
 * <p><b>КД</b> — {@link Pressurizer}: ТЭНы (пропорциональная группа и три резервные), впрыск из напора ГЦН, ИПУ и ПК.
 * <b>Подпитка-продувка</b>: регулятор уровня КД подаёт воду насосами подпитки (до 6 кг/с), продувка 1 кг/с; водообмен
 * для борного регулирования — чистым конденсатом или концентратом 40 г/кг H₃BO₃ при равной продувке. <b>САОЗ ВД</b> —
 * по сигналу защиты при P &lt; 12,5 МПа: раствор 16 г/кг H₃BO₃, до 15 кг/с, напор до 14 МПа.
 *
 * <p><b>Защита</b> — {@link ProtectionSystem} с уставками 01 §1.11.3, приведёнными к номиналу 15,7 МПа, и встроенными
 * датчиками (по три канала на функцию: камеры, давление КД, расход и обороты в каждой петле, T горячей нитки, уровень
 * ПГ) и тремя вычислителями DNBR. Внешний сигнал АЗ (ПАЗ, ПЛК) приходит как запрос автоматики.
 *
 * <p><b>Аварии на минимуме.</b> Течь холодной нитки площадью A: критическое истечение недогретой воды
 * G = 0,61·A·√(2ρ(P − P_s)) (оценка Бернулли до давления вскипания). Если КД опустел, дефицит массы — пар в горячем
 * узле при P_s(T_гор). Кризис теплообмена (истинная DNBR < 1 дольше 5 с) разрушает оболочки горячих ТВС —
 * активность переходит в теплоноситель. Давление выше 1,25 расчётного — разрыв контура (событие для DamageBudget).
 */
public final class PressurizedWaterReactor {
    public static final double DT = 0.05;
    public static final int POSITIONS = 19, LOOPS = 2;
    public static final double NOMINAL_W = 100e6;
    public static final double P_NOMINAL = 15.7e6, INLET_K = 563.15, OUTLET_K = 593.15;
    /** Объёмы петель: горячий и холодный узлы, м³; КД 3 м³, номинальный уровень 60 % [масштаб ПР-300 на 100 МВт]. */
    public static final double V_HOT = 3.0, V_COLD = 5.0, PZR_VOLUME = 3.0, PZR_LEVEL = 0.6;
    /** Уставки КД: ИПУ и ПК, Па, их пропускная способность, кг/с; расчётное давление и давление разрыва. */
    public static final double RELIEF_PA = 17.0e6, SAFETY_PA = 17.6e6, RELIEF_KG_S = 5, SAFETY_KG_S = 15,
            DESIGN_PA = 17.6e6, RUPTURE_PA = 1.25 * DESIGN_PA;
    /** ТЭНы: пропорциональная группа и резервные, Вт; впрыск, кг/с. */
    public static final double HEATER_PROP_W = 50e3, HEATER_BACKUP_W = 150e3, SPRAY_MAX_KG_S = 4;
    /** Теплопотери КД через изоляцию, Вт [оценка: корпус 3 м³ при 346 °C] — их покрывает пропорциональная группа ТЭН. */
    public static final double PZR_LOSS_W = 20e3;
    public static final double CHARGING_MAX_KG_S = 6, LETDOWN_KG_S = 1, CHARGING_K = 413.15, EXCHANGE_MAX_KG_S = 5;
    /** Борная кислота: концентрат водообмена и раствор САОЗ, г/кг H₃BO₃; г/кг H₃BO₃ → ppm бора. */
    public static final double CONCENTRATE_G_KG = 40, SI_G_KG = 16, PPM_B_PER_G_KG = 1000 * 10.811 / 61.833;
    public static final double SI_PRESSURE_PA = 12.5e6, SI_SHUTOFF_PA = 14e6, SI_MAX_KG_S = 15, SI_K = 313.15, SI_HOLD_S = 60;
    /** Естественная циркуляция: доля номинального расхода на номинальной мощности (∝ Q^(1/3)). */
    public static final double NATURAL_FLOW = 0.05;
    /** Ценность групп (полное погружение), Δk/k: аварийная (АЗ) и регулирующая (РГ). */
    public static final double WORTH_AZ = 0.06, WORTH_RG = 0.025;
    /** Привод СУЗ 0,02 м/с (R3); падение по 01 §1.10.3: g' = 6,4 м/с², v_t = 1,55 м/с на ходе 1 м (сброс 0,95 с). */
    public static final double DRIVE_M_S = 0.02, DROP_ACCEL = 6.4, DROP_TERMINAL = 1.55;
    /** Термическое сопротивление твэла до T_eff, К на кВт/м; теплоёмкость топлива с оболочкой, Дж/(кг ТМ·К). */
    public static final double FUEL_R_K_PER_KW_M = 17.5, FUEL_C_J_PER_KG_HM = 400;
    /** Теплопотери корпуса и петель в помещение на рабочих температурах, Вт. */
    public static final double AMBIENT_LOSS_W = 300e3;
    public static final double CONVERSION = 0.5, SOURCE_N_S = 1e8, REFLECTOR_M = 0.3;
    /** Кризис теплообмена: истинная DNBR ниже 1 дольше 5 с — разрушение оболочек горячей ТВС. */
    public static final double DNB_FAIL_S = 5;

    private static final WaterProperties W = WaterProperties.tables();
    /** Доля энергии деления, выделяющаяся с запаздыванием (распад ПД): мгновенная часть — остальное. */
    private static final double DECAY_SHARE = DecayHeat.afterInfiniteOperation(0);
    private static final double H_IN_NOM = W.enthalpyPT(P_NOMINAL, INLET_K), H_OUT_NOM = W.enthalpyPT(P_NOMINAL, OUTLET_K);
    /** Номинальный расход через зону, кг/с. */
    public static final double NOMINAL_FLOW_KG_S = NOMINAL_W / (H_OUT_NOM - H_IN_NOM);
    private static final double T_SEC_NOM = W.saturationP(SecondarySide.P_NOMINAL).tSat();
    private static final double H_SEC_NOM = W.enthalpyPT(P_NOMINAL, T_SEC_NOM);
    private static final double EPS_NOM = (H_OUT_NOM - H_IN_NOM) / (H_OUT_NOM - H_SEC_NOM);
    /** UA обоих ПГ, Вт/К: NTU номинала на среднюю теплоёмкость между T_гор и T_s (≈ 4,6 МВт/К, как у ПГВ ПР-300 на 100 МВт). */
    public static final double UA_SG = -Math.log(1 - EPS_NOM) * NOMINAL_FLOW_KG_S * (H_OUT_NOM - H_SEC_NOM) / (OUTLET_K - T_SEC_NOM);
    /** ГЦН — агрегат ПР-300: J = 300 кг·м², 1500 об/мин, 617 кВт на валу (τ выбега 12 с, 01 §1.14.1). */
    public static final double PUMP_RPM = 1500, PUMP_SHAFT_W = 617e3;

    // ----------------------------------------------------------------- топливо
    private final double[] hm0 = new double[POSITIONS], u5 = new double[POSITIONS], u8 = new double[POSITIONS],
            pu9 = new double[POSITIONS], fp = new double[POSITIONS], burnup = new double[POSITIONS], u6 = new double[POSITIONS],
            pu0 = new double[POSITIONS], x234 = new double[POSITIONS], share = new double[POSITIONS], radius = new double[POSITIONS],
            dnbTimer = new double[POSITIONS];
    private final boolean[] failed = new boolean[POSITIONS];
    private FuelDesign design;

    // ----------------------------------------------------------------- нейтроника
    private final LatticeModel lattice = new LatticeModel();
    private final CellComposition cell = LatticeDesigns.referenceCell(LatticeDesigns.T2_HEX);
    private final CellComposition reflector = CellComposition.nonFuel(ModeratorKind.NONE, 0, CoolantKind.H2O, 1, 0);
    private final FissionPoisons poisons = new FissionPoisons();
    private final DecayHeat decay = new DecayHeat();
    private final PeriodMeter period = new PeriodMeter(DT);
    private final PointKinetics kinetics = new PointKinetics(DelayedNeutronData.keepin(0), 2e-5, PointKinetics.MIN_POWER);
    private DelayedGroups groups = DelayedNeutronData.keepin(0);
    private double staticRho = -1, keff, betaEff, generation, sigmaA, sigmaF;
    private double evalFuelK, evalRho, evalModK, evalBoron;
    private double alphaDoppler, alphaDensity, alphaModerator, alphaBoron;
    private double totalRho, fissionW, slowTimer = 1e9;
    private int loaded;

    // ----------------------------------------------------------------- СУЗ
    private double az = 1, rg = 1, azSpeed, rgSpeed;
    private int rgDrive;
    private boolean autoRegulator;
    private double setpoint = 1.0;
    private final ProtectionSystem protection;
    private final Sensors sensors = new Sensors();
    private boolean manualTrip, externalTrip, driveTorqueLost, lowFluxBlocked;
    private long tick;
    private boolean rodBlock, stepback, rodWithdrawal;

    // ----------------------------------------------------------------- контур
    private final CoolantPump[] pumps = new CoolantPump[LOOPS];
    private final double[] pumpDrive = new double[LOOPS];
    private final Pressurizer pzr = new Pressurizer(W, PZR_VOLUME);
    private double hHot = H_OUT_NOM, hCold = H_IN_NOM, loopKg, fuelK = 573.15;
    private double boronKg;
    private double exchangeKgS, exchangeGkg;
    private double leakAreaM2, leakKgS, siKgS, chargingKgS, sprayKgS, heaterW, siHold;
    private double flowKgS, qCoreW, qSgW, voidM3;
    private double hotK, coldK, coolantDensity, pressure;
    private double secondaryK = Double.NaN, sgLevel = Double.NaN;
    private int sgCount;
    private boolean ruptured, ruptureEvent;
    private int newlyFailed;
    private double mdnbr = Double.POSITIVE_INFINITY;
    private final DnbrCalculator trueDnbr;
    private final HotChannelModel hotChannel;
    private final ThermoState ts = new ThermoState();
    private final double[] satBuf = new double[17];
    private final SaturationState sat = new SaturationState();

    public PressurizedWaterReactor() {
        for (int i = 0; i < LOOPS; i++) pumps[i] = new CoolantPump(CoolantPump.INERTIA_KGM2, PUMP_RPM, PUMP_SHAFT_W);
        // гексагональная решётка: центр, кольцо из 6 на шаге, кольцо из 12 (6 на √3 шага, 6 на 2 шагах)
        for (int i = 0; i < POSITIONS; i++) radius[i] = i == 0 ? 0 : i <= 6 ? 1 : i % 2 == 1 ? Math.sqrt(3) : 2;
        hotChannel = hotChannelModel(FuelDesign.VVER_440);
        ProtectionConfig cfg = ProtectionConfig.defaults();
        trueDnbr = new DnbrCalculator(hotChannel, cfg.withoutDnbrUncertainty(), W);
        PlantNominals nom = PlantNominals.of(W, P_NOMINAL, OUTLET_K, SAFETY_PA, SI_PRESSURE_PA);
        protection = ProtectionSystem.pressurizedWater(cfg, nom, new DnbrCalculator(hotChannel, cfg, W), ProtectionEventSink.NONE);
        hotStandby();
    }

    /**
     * Модель горячего канала: проходное сечение и D_h — из ячейки ТВС (шаг минус твэлы и направляющие), тепловой поток —
     * на поверхность оболочек, F_ΔH = 2,0/1,55, консервативный F_z = 1,55 (01 §1.11.4).
     */
    static HotChannelModel hotChannelModel(FuelDesign d) {
        double cellArea = d.hexagonal ? Math.sqrt(3) / 2 * pitch(d) * pitch(d) : pitch(d) * pitch(d);
        double rodArea = Math.PI / 4 * d.cladOd * d.cladOd;
        double flowPer = cellArea - d.rods * rodArea - d.guideTubes * rodArea * 1.6;
        double wetted = d.rods * Math.PI * d.cladOd;
        double area = flowPer * POSITIONS;
        double heated = wetted * d.stackM * POSITIONS;
        return new HotChannelModel(NOMINAL_W, NOMINAL_FLOW_KG_S, area, 4 * flowPer / wetted, NOMINAL_W / heated, 2.0 / 1.55, 1.55, 8);
    }

    /** Шаг ТВС под ключ, м (ВВЭР-440 — 147 мм, ВВЭР-1000 — 236 мм, PWR 17×17 — 215 мм). */
    public static double pitch(FuelDesign d) {
        return switch (d) {
            case VVER_440 -> 0.147;
            case VVER_1000 -> 0.236;
            case PWR_17X17 -> 0.215;
            default -> 0.147;
        };
    }

    /** Конструкции ТВС, которые принимает корпус. */
    public static boolean accepts(FuelDesign d) {
        return d == FuelDesign.VVER_440 || d == FuelDesign.VVER_1000 || d == FuelDesign.PWR_17X17;
    }

    /** Горячий останов: контур 280 °C при номинальном давлении, КД на уровне, бор 12 г/кг, стержни внизу. */
    public void hotStandby() {
        double t = 553.15;
        hHot = hCold = W.enthalpyPT(P_NOMINAL, t);
        pzr.initialize(P_NOMINAL, PZR_LEVEL);
        pressure = P_NOMINAL;
        loopKg = requiredLoopKg(P_NOMINAL, hHot, hCold);
        setBoronGkg(12);
        fuelK = t;
        az = rg = 1;
        properties();
    }

    // ------------------------------------------------------------------ топливо

    /** Загрузка ТВС в позицию: только на заглушенном реакторе; конструкция — одна на весь корпус. */
    public boolean load(int pos, FuelDesign d, double hmKg, double u235, double u234) {
        if (!shutDown() || hm0[pos] > 0 || hmKg <= 0 || !accepts(d)) return false;
        if (design != null && loaded > 0 && design != d) return false;
        design = d;
        hm0[pos] = hmKg;
        u5[pos] = hmKg * u235;
        u8[pos] = hmKg * (1 - u235 - u234);
        x234[pos] = u234;
        pu9[pos] = fp[pos] = burnup[pos] = u6[pos] = pu0[pos] = dnbTimer[pos] = 0;
        failed[pos] = false;
        countLoaded();
        slowTimer = 1e9;
        return true;
    }

    /** Состав ТВС: начальный ТМ, U-235, U-238, U-236, Pu-239, Pu-240, ПД, кг; U-234 в исходном; выгорание. */
    public record AssemblyFuel(FuelDesign design, double hm0Kg, double u235Kg, double u238Kg, double u236Kg, double pu239Kg,
            double pu240Kg, double fpKg, double u234, double burnupMwdKg, boolean failed) {
        public double uraniumKg() {
            return u235Kg + u238Kg + u236Kg + hm0Kg * u234;
        }

        public double plutoniumKg() {
            return pu239Kg + pu240Kg;
        }
    }

    public AssemblyFuel fuel(int pos) {
        return new AssemblyFuel(design, hm0[pos], u5[pos], u8[pos], u6[pos], pu9[pos], pu0[pos], fp[pos], x234[pos], burnup[pos], failed[pos]);
    }

    public AssemblyFuel unload(int pos) {
        if (!shutDown() || hm0[pos] <= 0) return null;
        AssemblyFuel f = fuel(pos);
        hm0[pos] = u5[pos] = u8[pos] = pu9[pos] = fp[pos] = burnup[pos] = u6[pos] = pu0[pos] = dnbTimer[pos] = 0;
        failed[pos] = false;
        countLoaded();
        if (loaded == 0) design = null;
        slowTimer = 1e9;
        return f;
    }

    private void countLoaded() {
        loaded = 0;
        for (double m : hm0) if (m > 0) loaded++;
    }

    public boolean loaded(int pos) {
        return hm0[pos] > 0;
    }

    public int loadedAssemblies() {
        return loaded;
    }

    public FuelDesign design() {
        return design;
    }

    /** Заглушен: мощность ниже 0,1 % и аварийная группа внизу. */
    public boolean shutDown() {
        return power() < 1e-3 && az > 0.99;
    }

    // ------------------------------------------------------------------ управление

    /** Привод РГ: −1 — вверх, 0 — стоп, +1 — вниз. */
    public void driveRg(int dir) {
        rgDrive = Integer.signum(dir);
    }

    public int rgDrive() {
        return rgDrive;
    }

    public void setAutoRegulator(boolean on) {
        autoRegulator = on && !tripped();
    }

    public boolean autoRegulator() {
        return autoRegulator;
    }

    public void setSetpoint(double f) {
        setpoint = Math.max(0.01, Math.min(1.04, f));
    }

    public double setpoint() {
        return setpoint;
    }

    /** Привод ГЦН петли: обороты, которые держит двигатель, доля номинала (0 — обесточен). */
    public void setPumpDrive(int loop, double drive) {
        pumpDrive[loop] = Math.max(0, Math.min(1, drive));
    }

    /** Второй контур: температура насыщения котлов ПГ ({@code NaN} — тепло некуда отдать), уровень ПГ, число ПГ. */
    public void setSecondary(double tK, double levelFrac, int count) {
        secondaryK = tK;
        sgLevel = levelFrac;
        sgCount = Math.max(0, Math.min(LOOPS, count));
    }

    /** Водообмен: расход, кг/с, и концентрация подаваемой воды, г/кг H₃BO₃ (0 — чистый конденсат). */
    public void setWaterExchange(double kgS, double gPerKg) {
        exchangeKgS = Math.max(0, Math.min(EXCHANGE_MAX_KG_S, kgS));
        exchangeGkg = Math.max(0, gPerKg);
    }

    public double exchangeKgS() {
        return exchangeKgS;
    }

    public double exchangeGkg() {
        return exchangeGkg;
    }

    public void setLeakArea(double m2) {
        leakAreaM2 = Math.max(0, m2);
    }

    public double leakArea() {
        return leakAreaM2;
    }

    public void scram() {
        manualTrip = true;
    }

    public void setExternalTrip(boolean on) {
        externalTrip = on;
    }

    public void setDriveTorqueLost(boolean lost) {
        driveTorqueLost = lost;
    }

    /** Снять низкую (пусковую) уставку по мощности — разрешено выше 10 %. */
    public boolean blockLowFluxSetpoint() {
        lowFluxBlocked = protection.setLowFluxSetpointBlocked(true);
        return lowFluxBlocked;
    }

    public boolean resetTrip() {
        return protection.reset();
    }

    public ProtectionSystem protection() {
        return protection;
    }

    public boolean tripped() {
        ProtectionMode m = protection.mode();
        return m == ProtectionMode.TRIPPED;
    }

    // ------------------------------------------------------------------ шаг

    /** Шаг {@link #DT} реального времени; {@code gameDt} — игрового (яды, выгорание). */
    public void step(double gameDt) {
        tick++;
        newlyFailed = 0;
        ruptureEvent = false;
        slowTimer += DT;
        if (slowTimer >= 1) {
            slowTimer = 0;
            solveStatic();
        }
        protectionStep();
        moveRods();
        totalRho = reactivity0();
        double n = loaded > 0 ? kinetics.step(DT, totalRho, sourceNorm()) : PointKinetics.MIN_POWER;
        fissionW = n * NOMINAL_W;
        decay.step(DT, fissionW);
        poisonsStep(gameDt);
        if (fissionW > 0 && gameDt > 0)
            for (int i = 0; i < POSITIONS; i++)
                if (share[i] > 0) FuelDepletion.deplete(i, fissionW * share[i], gameDt, CONVERSION, hm0, u5, u8, u6, pu9, pu0, fp, burnup);
        hydraulics();
        period.sample(Math.max(n, PointKinetics.MIN_POWER));
        dnb();
    }

    private double sourceNorm() {
        return PointKinetics.normalizedSource(SOURCE_N_S, 2.43, generation, NOMINAL_W);
    }

    private double coreRadius() {
        FuelDesign d = design == null ? FuelDesign.VVER_440 : design;
        double a = d.hexagonal ? Math.sqrt(3) / 2 * pitch(d) * pitch(d) : pitch(d) * pitch(d);
        return Math.sqrt(Math.max(loaded, 1) * a / Math.PI);
    }

    private double coreHeight() {
        return design == null ? FuelDesign.VVER_440.stackM : design.stackM;
    }

    private double moderatorK() {
        return (hotK + coldK) / 2;
    }

    private CellFeedbackState feedbackState() {
        double tn = moderatorK() + LatticeModel.NEUTRON_GAS_OFFSET;
        return new CellFeedbackState(Math.max(1, fuelK), Math.max(1, coolantDensity), moderatorK(), Math.max(0, boronPpm()),
                poisons.xenonAbs(sigmaXe(tn)), poisons.samariumAbs(sigmaSm(tn)));
    }

    private static double sigmaXe(double tn) {
        return FissionPoisons.SIGMA_XE_B * Westcott.g(Westcott.Factor.XE135_A, tn) * Westcott.oneOverV(tn);
    }

    private static double sigmaSm(double tn) {
        return FissionPoisons.SIGMA_SM_B * Westcott.g(Westcott.Factor.SM149_A, tn) * Westcott.oneOverV(tn);
    }

    private void solveStatic() {
        countLoaded();
        NuclideVector fuel = FuelDepletion.averageFuel(hm0, u5, u8, pu9, fp, 0.04);
        CellFeedbackState s = feedbackState();
        SixFactor sf = lattice.evaluate(cell, fuel, s);
        SixFactor refl = lattice.evaluate(reflector, fuel, s);
        double delta = lattice.reflectorSavings(sf, refl, REFLECTOR_M);
        double b2 = lattice.geometricBuckling(ReactorShape.cylinder(coreRadius(), coreHeight()), delta);
        keff = loaded > 0 ? lattice.estimateKeff(sf, b2) : 0;
        staticRho = loaded > 0 ? (keff - 1) / keff : -1;
        evalFuelK = s.fuelEffTemp();
        evalRho = s.coolantDensity();
        evalModK = s.moderatorTemp();
        evalBoron = s.boronPpm();
        // Коэффициенты — по полному k_eff (ячейка, утечка, отражатель), центральными разностями: в малой зоне плотность
        // воды меняет длину миграции и утечку сильнее, чем k∞ ячейки, и производные одной ячейки здесь не годятся.
        if (loaded > 0) {
            alphaDoppler = (rhoAt(fuel, s.withFuelTemp(evalFuelK + 10)) - rhoAt(fuel, s.withFuelTemp(evalFuelK - 10))) / 20;
            alphaDensity = (rhoAt(fuel, s.withCoolantDensity(evalRho + 5)) - rhoAt(fuel, s.withCoolantDensity(Math.max(1, evalRho - 5))))
                    / (evalRho + 5 - Math.max(1, evalRho - 5));
            alphaModerator = (rhoAt(fuel, s.withModeratorTemp(evalModK + 5)) - rhoAt(fuel, s.withModeratorTemp(evalModK - 5))) / 10;
            alphaBoron = (rhoAt(fuel, s.withBoron(evalBoron + 20)) - rhoAt(fuel, s.withBoron(Math.max(0, evalBoron - 20))))
                    / (evalBoron + 20 - Math.max(0, evalBoron - 20));
        } else {
            alphaDoppler = alphaDensity = alphaModerator = alphaBoron = 0;
        }
        sigmaA = sf.sigmaA();
        sigmaF = sf.sigmaF();
        DelayedGroups g = lattice.delayedGroups(lattice.fissionFractions(cell, fuel, s), b2, sf);
        generation = lattice.generationTime(sf, moderatorK() + LatticeModel.NEUTRON_GAS_OFFSET);
        betaEff = g.betaTotal();
        groups = g;
        kinetics.setParameters(g, generation);
        // доли мощности ТВС по J₀(2,405 r/R)
        double r = coreRadius() + delta, step = pitch(design == null ? FuelDesign.VVER_440 : design), sum = 0;
        for (int i = 0; i < POSITIONS; i++) {
            share[i] = hm0[i] > 0 ? Math.max(0.05, j0(2.405 * radius[i] * step / r)) : 0;
            sum += share[i];
        }
        for (int i = 0; i < POSITIONS; i++) share[i] = sum > 0 ? share[i] / sum : 0;
    }

    /** Реактивность (k_eff − 1)/k_eff зоны с отражателем при состоянии {@code s}. */
    private double rhoAt(NuclideVector fuel, CellFeedbackState s) {
        SixFactor sf = lattice.evaluate(cell, fuel, s);
        double delta = lattice.reflectorSavings(sf, lattice.evaluate(reflector, fuel, s), REFLECTOR_M);
        double k = lattice.estimateKeff(sf, lattice.geometricBuckling(ReactorShape.cylinder(coreRadius(), coreHeight()), delta));
        return (k - 1) / k;
    }

    static double j0(double x) {
        double y = Math.min(3, Math.abs(x)) / 3, y2 = y * y;
        return 1 + y2 * (-2.2499997 + y2 * (1.2656208 + y2 * (-0.3163866 + y2 * (0.0444479 + y2 * (-0.0039444 + y2 * 0.0002100)))));
    }

    private static double sCurve(double x) {
        return x - Math.sin(2 * Math.PI * x) / (2 * Math.PI);
    }

    /** Реактивность стержней, Δk/k (отрицательная). */
    public double rodReactivity() {
        return -(WORTH_AZ * sCurve(az) + WORTH_RG * sCurve(rg));
    }

    private double reactivity0() {
        return staticRho + alphaDoppler * (fuelK - evalFuelK) + alphaDensity * (coolantDensity - evalRho)
                + alphaModerator * (moderatorK() - evalModK) + alphaBoron * (boronPpm() - evalBoron) + rodReactivity();
    }

    private void protectionStep() {
        protection.step(DT, sensors, tick);
        boolean siNow = false;
        rodBlock = stepback = false;
        for (ProtectionAction a : protection.pendingActions()) {
            if (a instanceof ProtectionAction.SafetyInjection) siNow = true;
            else if (a instanceof ProtectionAction.RodBlock) rodBlock = true;
            else if (a instanceof ProtectionAction.Stepback) stepback = true;
        }
        siHold = siNow ? SI_HOLD_S : Math.max(0, siHold - DT);
        manualTrip = false;
        if (protection.isTripped()) autoRegulator = false;
    }

    private void moveRods() {
        double h = coreHeight();
        rodWithdrawal = false;
        if (protection.rodsReleased()) {
            // гравитационное падение с сопротивлением воды (01 §1.10.3), ход в долях высоты зоны
            double a = DROP_ACCEL / h, vt = DROP_TERMINAL / h;
            azSpeed += a * (1 - (azSpeed / vt) * (azSpeed / vt)) * DT;
            rgSpeed += a * (1 - (rgSpeed / vt) * (rgSpeed / vt)) * DT;
            az = Math.min(1, az + azSpeed * DT);
            rg = Math.min(1, rg + rgSpeed * DT);
            rgDrive = 0;
            return;
        }
        azSpeed = rgSpeed = 0;
        double v = DRIVE_M_S / h * DT;
        if (protection.isTripped()) return; // питание снято, стержни на защёлках до освобождения
        // взвод АЗ: аварийная группа поднимается приводом
        if (az > 0) {
            az = Math.max(0, az - v);
            rodWithdrawal = true;
        }
        int dir = rgDrive;
        if (autoRegulator) {
            double err = Math.log(Math.max(power(), 1e-9) / setpoint);
            dir = err > 0.01 ? 1 : err < -0.01 ? -1 : 0;
        }
        if (stepback && power() > ProtectionSystem.STEPBACK_TARGET_FRAC) dir = 1;
        if (dir < 0 && rodBlock) dir = 0;
        if (dir < 0) rodWithdrawal = true;
        rg = Math.max(0, Math.min(1, rg + dir * v));
    }

    private void poisonsStep(double gameDt) {
        double f = 0, phi = 0;
        double vCm3 = Math.PI * Math.pow(coreRadius() * 100, 2) * coreHeight() * 100;
        if (loaded > 0 && sigmaF > 0) {
            f = fissionW / (PointKinetics.FISSION_ENERGY * vCm3);
            phi = f / sigmaF;
        }
        double tn = moderatorK() + LatticeModel.NEUTRON_GAS_OFFSET;
        poisons.step(gameDt, f, phi, sigmaXe(tn), sigmaSm(tn));
    }

    // ------------------------------------------------------------------ теплогидравлика

    private double requiredLoopKg(double p, double hh, double hc) {
        W.stateFromPressureEnthalpy(p, hh, ts);
        double rh = ts.density;
        W.stateFromPressureEnthalpy(p, hc, ts);
        return rh * V_HOT + ts.density * V_COLD;
    }

    private SaturationState sat(double p) {
        W.saturationP(p, satBuf);
        return sat.set(p, satBuf);
    }

    private void hydraulics() {
        double p = pressure;
        // насосы и расход
        double speedSum = 0;
        for (int i = 0; i < LOOPS; i++) {
            pumps[i].step(DT, pumpDrive[i]);
            speedSum += pumps[i].speed();
        }
        double heat = fissionW * (1 - DECAY_SHARE) + decay.powerW();
        double natural = NATURAL_FLOW * Math.cbrt(Math.max(0, heat) / NOMINAL_W);
        double flowFrac = Math.max(Math.max(0.005, natural), speedSum / LOOPS);
        flowKgS = NOMINAL_FLOW_KG_S * flowFrac;
        // топливо
        double hmTotal = 0, rodLength = 0;
        for (double m : hm0) hmTotal += m;
        if (design != null) rodLength = loaded * design.rods * design.stackM;
        double cFuel = Math.max(1e5, FUEL_C_J_PER_KG_HM * hmTotal);
        double uaFuel = Math.max(1e3, rodLength * 1000 / FUEL_R_K_PER_KW_M);
        double tCoolCore = coldK > 0 ? (hotK + coldK) / 2 : 573.15;
        qCoreW = uaFuel * (fuelK - tCoolCore);
        double toFuel = loaded > 0 ? heat : 0;
        fuelK += (toFuel - qCoreW) / cFuel * DT;
        // ПГ
        double pumpHeat = 0;
        for (CoolantPump pump : pumps) pumpHeat += pump.shaftPowerW() * 0.9;
        qSgW = 0;
        if (!Double.isNaN(secondaryK) && sgCount > 0 && hotK > secondaryK) {
            // кипящий второй контур изотермичен: ε = 1 − e^(−NTU), теплоёмкость — средняя между T_гор и T_s
            double hSec = W.enthalpyPT(p, secondaryK);
            double cBar = (hHot - hSec) / Math.max(0.1, hotK - secondaryK);
            double ntu = UA_SG * sgCount / LOOPS / Math.max(1, flowKgS * cBar);
            qSgW = (1 - Math.exp(-ntu)) * flowKgS * (hHot - hSec);
        }
        double loss = AMBIENT_LOSS_W * Math.max(0, (moderatorK() - 293.15) / (578 - 293.15));
        // узлы: горячий — выход зоны, холодный — выход ПГ
        double mHot = densityPH(p, hHot) * V_HOT, mCold = densityPH(p, hCold) * V_COLD;
        double hSgOut = hHot - qSgW / Math.max(1e-6, flowKgS);
        hHot += (flowKgS * (hCold - hHot) + qCoreW) / mHot * DT;
        hCold += (flowKgS * (hSgOut - hCold) + pumpHeat - loss) / mCold * DT;
        // подпитка, продувка, водообмен, САОЗ, течь
        double levelErr = PZR_LEVEL - pzr.level();
        double letdown = LETDOWN_KG_S + exchangeKgS;
        chargingKgS = Math.max(0, Math.min(CHARGING_MAX_KG_S, LETDOWN_KG_S + 0.2 * levelErr * 100 * 0.25));
        double ppm = boronPpm();
        SaturationState sc = sat(p);
        W.stateFromPressureEnthalpy(p, hCold, ts);
        double rhoCold = ts.density;
        double tColdNow = ts.temperature;
        double pSatCold = W.saturationPressure(Math.min(640, tColdNow));
        leakKgS = leakAreaM2 > 0 ? 0.61 * leakAreaM2 * Math.sqrt(2 * rhoCold * Math.max(0.05 * p, p - Math.max(pSatCold, 0.1e6))) : 0;
        siKgS = siHold > 0 ? SI_MAX_KG_S * Math.max(0, Math.min(1, (SI_SHUTOFF_PA - p) / (SI_SHUTOFF_PA - 3e6))) : 0;
        double hCharge = W.enthalpyPT(p, CHARGING_K), hSi = W.enthalpyPT(p, SI_K);
        double inKg = (chargingKgS + exchangeKgS + siKgS) * DT;
        double outKg = (letdown + leakKgS) * DT;
        double totalKg = Math.max(1, loopKg + pzr.liquidKg());
        boronKg += (chargingKgS * ppm + exchangeKgS * exchangeGkg * PPM_B_PER_G_KG + siKgS * SI_G_KG * PPM_B_PER_G_KG) * DT / 1e6
                - outKg * boronKg / totalKg;
        boronKg = Math.max(0, boronKg);
        double eIn = (chargingKgS + exchangeKgS) * DT * hCharge + siKgS * DT * hSi - outKg * hCold;
        double mColdNew = mCold + inKg - outKg;
        hCold = (hCold * mCold + eIn) / Math.max(1, mColdNew);
        loopKg += inKg - outKg;
        // объём петель постоянен: излишек — в КД, недостаток — из КД
        double required = requiredLoopKg(p, hHot, hCold);
        double surge = loopKg - required;
        voidM3 = 0;
        if (surge < 0 && pzr.liquidKg() + surge < 1) {
            // КД пуст: пар в горячем узле при давлении насыщения
            double fromPzr = Math.max(0, pzr.liquidKg() - 1);
            double deficit = -surge - fromPzr;
            surge = -fromPzr;
            W.stateFromPressureEnthalpy(p, hHot, ts);
            voidM3 = Math.min(V_HOT, deficit / Math.max(1, ts.density));
        }
        // впрыск — из напора ГЦН первой петли
        double pErr = p - P_NOMINAL;
        double sprayDemand = Math.max(0, Math.min(1, (pErr - 0.1e6) / 0.3e6));
        sprayKgS = SPRAY_MAX_KG_S * sprayDemand * Math.min(1, pumps[0].speed());
        heaterW = pzr.level() < 0.1 ? 0
                : HEATER_PROP_W * Math.max(0, Math.min(1, (0.1e6 - pErr) / 0.2e6)) + (pErr < -0.15e6 ? HEATER_BACKUP_W : 0);
        double outH = pzr.step(DT, surge, hHot, sprayKgS, hCold, heaterW - PZR_LOSS_W, RELIEF_PA, RELIEF_KG_S, SAFETY_PA, SAFETY_KG_S);
        // впрыск забирается из холодной нитки и возвращается через КД; outsurge приносит энтальпию КД в горячий узел
        loopKg -= sprayKgS * DT;
        if (surge > 0) loopKg -= surge;
        else if (surge < 0) {
            double back = -surge;
            double mh = densityPH(p, hHot) * V_HOT;
            hHot = (hHot * mh + back * outH) / (mh + back);
            loopKg += back;
        }
        pressure = pzr.pressure();
        if (voidM3 > 0) {
            double psat = W.saturationPressure(Math.min(640, temperaturePH(p, hHot)));
            pressure = Math.min(pressure, Math.max(0.1e6, psat));
            pzr.initialize(pressure, Math.max(0, pzr.level()));
            loopKg = requiredLoopKg(pressure, hHot, hCold) - voidM3 * densityPH(pressure, hHot);
        }
        if (pressure > RUPTURE_PA && !ruptured) {
            ruptured = true;
            ruptureEvent = true;
            leakAreaM2 = Math.max(leakAreaM2, 0.01);
        }
        properties();
    }

    private double densityPH(double p, double h) {
        W.stateFromPressureEnthalpy(p, h, ts);
        return ts.density;
    }

    private double temperaturePH(double p, double h) {
        return W.temperaturePH(p, h);
    }

    private void properties() {
        hotK = temperaturePH(pressure, hHot);
        coldK = temperaturePH(pressure, hCold);
        double rho = densityPH(pressure, (hHot + hCold) / 2);
        double voidFrac = voidM3 / (V_HOT + V_COLD);
        coolantDensity = rho * (1 - 2 * voidFrac); // пар собирается в верхней части зоны и горячих нитках
    }

    private void dnb() {
        mdnbr = trueDnbr.calculate(pressure, coldK, flowKgS / NOMINAL_FLOW_KG_S, (fissionW * (1 - DECAY_SHARE) + decay.powerW()) / NOMINAL_W, Double.NaN);
        // пар заполнил весь горячий узел (верхняя камера, горячие нитки) — уровень смеси опускается в зону: кризис
        if (voidM3 >= 0.98 * V_HOT) mdnbr = Math.min(mdnbr, 0.8);
        double maxShare = 0;
        for (double s : share) maxShare = Math.max(maxShare, s);
        for (int i = 0; i < POSITIONS; i++) {
            if (hm0[i] <= 0 || failed[i]) continue;
            // горячий канал — у ТВС с наибольшей долей; у остальных запас больше на отношение долей
            double local = mdnbr * (maxShare > 0 ? maxShare / Math.max(1e-6, share[i]) : 1);
            dnbTimer[i] = local < 1 ? dnbTimer[i] + DT : 0;
            if (dnbTimer[i] >= DNB_FAIL_S) {
                failed[i] = true;
                newlyFailed++;
            }
        }
    }

    // ------------------------------------------------------------------ датчики защиты

    private final class Sensors implements ProtectionInputs {
        @Override
        public int channelCount(TripReason r) {
            return switch (r) {
                case HIGH_FLUX, SHORT_PERIOD, HIGH_PRIMARY_PRESSURE, LOW_PRIMARY_PRESSURE, HIGH_COOLANT_TEMP, LOW_DNBR -> 3;
                case LOW_COOLANT_FLOW, LOSS_OF_DRIVE_POWER -> 3 * LOOPS;
                case LOW_SG_LEVEL -> Double.isNaN(sgLevel) ? 0 : 3 * Math.max(1, sgCount);
                default -> 0;
            };
        }

        @Override
        public int groupCount(TripReason r) {
            return switch (r) {
                case LOW_COOLANT_FLOW, LOSS_OF_DRIVE_POWER -> LOOPS;
                case LOW_SG_LEVEL -> Math.max(1, sgCount);
                default -> 1;
            };
        }

        @Override
        public double channelValue(TripReason r, int ch) {
            return switch (r) {
                case HIGH_FLUX -> power();
                case SHORT_PERIOD -> period.period();
                case HIGH_PRIMARY_PRESSURE, LOW_PRIMARY_PRESSURE -> pressure;
                case LOW_COOLANT_FLOW -> loopFlowFrac(ch / 3);
                case HIGH_COOLANT_TEMP -> hotK;
                case LOW_SG_LEVEL -> sgLevel;
                case LOSS_OF_DRIVE_POWER -> pumps[ch / 3].speed();
                default -> Double.NaN;
            };
        }

        @Override
        public double measuredPowerFrac() {
            return power();
        }

        @Override
        public double measuredPeriod() {
            return period.period();
        }

        @Override
        public boolean luaTripRequested() {
            return externalTrip;
        }

        @Override
        public boolean manualTrip() {
            return manualTrip;
        }

        @Override
        public boolean driveTorqueLost() {
            return driveTorqueLost;
        }

        @Override
        public double dnbrSignal(DnbrSignal s, int calculator) {
            return switch (s) {
                case PRESSURE -> pressure;
                case INLET_TEMPERATURE -> coldK;
                case FLOW_FRAC -> flowKgS / NOMINAL_FLOW_KG_S;
                case POWER_FRAC -> power();
                case AXIAL_PEAKING -> Double.NaN;
            };
        }

        @Override
        public boolean pumpTripped() {
            for (double d : pumpDrive) if (d <= 0) return true;
            return false;
        }

        @Override
        public boolean rodsAtBottom() {
            return az > 0.99 && rg > 0.99;
        }

        @Override
        public boolean rodWithdrawalStarted() {
            return rodWithdrawal;
        }
    }

    /** Расход петли, доля её номинала (половина номинала зоны). */
    public double loopFlowFrac(int loop) {
        double natural = flowKgS / NOMINAL_FLOW_KG_S;
        return Math.max(pumps[loop].speed(), Math.min(natural, NATURAL_FLOW));
    }

    // ------------------------------------------------------------------ показания

    public double power() {
        return kinetics.power();
    }

    public double fissionW() {
        return fissionW;
    }

    public double decayHeatW() {
        return decay.powerW();
    }

    public double periodS() {
        return period.period();
    }

    public double reactivity() {
        return totalRho;
    }

    public double keffStatic() {
        return keff;
    }

    public double betaEff() {
        return betaEff;
    }

    public double generationTime() {
        return generation;
    }

    public DelayedGroups delayedGroups() {
        return groups;
    }

    /** Коэффициенты реактивности последнего пересчёта: Доплер, 1/К; по плотности воды, 1/(кг/м³); спектральный по T воды, 1/К; бор, 1/ppm. */
    public double dopplerCoefficient() {
        return alphaDoppler;
    }

    public double densityCoefficient() {
        return alphaDensity;
    }

    public double moderatorSpectralCoefficient() {
        return alphaModerator;
    }

    public double boronCoefficient() {
        return alphaBoron;
    }

    /** Температурный коэффициент реактивности теплоносителя (по плотности и спектру), 1/К. */
    public double moderatorTemperatureCoefficient() {
        double t = moderatorK(), h = (hHot + hCold) / 2;
        double cp = 5.5e3;
        double drho = densityPH(pressure, h + cp) - densityPH(pressure, h - cp);
        return alphaDensity * drho / 2 + alphaModerator;
    }

    public double xenonReactivity() {
        double tn = moderatorK() + LatticeModel.NEUTRON_GAS_OFFSET;
        return sigmaA > 0 ? -poisons.xenonAbs(sigmaXe(tn)) / sigmaA : 0;
    }

    public FissionPoisons poisons() {
        return poisons;
    }

    public double az() {
        return az;
    }

    public double rg() {
        return rg;
    }

    public double fuelK() {
        return fuelK;
    }

    public double hotLegK() {
        return hotK;
    }

    public double coldLegK() {
        return coldK;
    }

    public double coolantDensity() {
        return coolantDensity;
    }

    /** Давление первого контура (в КД), Па. */
    public double pressure() {
        return pressure;
    }

    public Pressurizer pressurizer() {
        return pzr;
    }

    public double pressurizerLevel() {
        return pzr.level();
    }

    public double heaterW() {
        return heaterW;
    }

    public double sprayKgS() {
        return sprayKgS;
    }

    public double chargingKgS() {
        return chargingKgS;
    }

    public double leakKgS() {
        return leakKgS;
    }

    public double safetyInjectionKgS() {
        return siKgS;
    }

    public boolean safetyInjection() {
        return siHold > 0;
    }

    public double flowFraction() {
        return flowKgS / NOMINAL_FLOW_KG_S;
    }

    public double pumpSpeed(int loop) {
        return pumps[loop].speed();
    }

    public CoolantPump pump(int loop) {
        return pumps[loop];
    }

    public double steamGeneratorW() {
        return qSgW;
    }

    public double coreHeatW() {
        return qCoreW;
    }

    /** Истинная MDNBR горячего канала (без погрешностей вычислителя). */
    public double mdnbr() {
        return mdnbr;
    }

    /** Расчётная DNBR первого вычислителя защиты. */
    public double calculatedDnbr() {
        return protection.calculatedDnbr(0);
    }

    public double boronPpm() {
        double total = loopKg + pzr.liquidKg();
        return total > 0 ? boronKg / total * 1e6 : 0;
    }

    /** Концентрация борной кислоты, г/кг H₃BO₃ (как на ВВЭР). */
    public double boronGkg() {
        return boronPpm() / PPM_B_PER_G_KG;
    }

    public void setBoronGkg(double g) {
        boronKg = Math.max(0, g) * PPM_B_PER_G_KG * (loopKg + pzr.liquidKg()) / 1e6;
        slowTimer = 1e9;
    }

    public double primaryKg() {
        return loopKg + pzr.liquidKg() + pzr.vaporKg();
    }

    public double voidM3() {
        return voidM3;
    }

    public double burnup(int pos) {
        return burnup[pos];
    }

    public double maxBurnup() {
        double m = 0;
        for (double b : burnup) m = Math.max(m, b);
        return m;
    }

    public boolean failed(int pos) {
        return failed[pos];
    }

    public int failedAssemblies() {
        int n = 0;
        for (boolean f : failed) if (f) n++;
        return n;
    }

    public int newlyFailed() {
        return newlyFailed;
    }

    /** Давление превысило 1,25 расчётного на этом шаге — разрыв контура. */
    public boolean ruptureEvent() {
        return ruptureEvent;
    }

    public boolean ruptured() {
        return ruptured;
    }

    // ------------------------------------------------------------------ тесты и горячий старт

    /** Номинальное равновесие: тепловое состояние, бор и РГ под мощность {@code n} с данной температурой второго контура. */
    public void setThermalState(double fuel, double hot, double cold) {
        fuelK = fuel;
        hHot = W.enthalpyPT(pressure, hot);
        hCold = W.enthalpyPT(pressure, cold);
        loopKg = requiredLoopKg(pressure, hHot, hCold);
        for (int i = 0; i < LOOPS; i++) pumps[i].setSpeed(pumpDrive[i]);
        properties();
        slowTimer = 1e9;
    }

    public void equilibrate(double n) {
        solveStatic();
        kinetics.resetToEquilibrium(n);
        decay.equilibrium(n * NOMINAL_W);
        lowFluxBlocked = n > 0.1;
        protection.restore(n, lowFluxBlocked);
    }

    /** Пересчитать ячейку и вернуть реактивность при текущем состоянии без шага (подбор критичности, тесты). */
    public double evaluateReactivity() {
        solveStatic();
        return reactivity0();
    }

    public void setRods(double az, double rg) {
        this.az = az;
        this.rg = rg;
    }

    // ------------------------------------------------------------------ сохранение

    public void save(StateWriter out) {
        out.putDoubles("hm0", hm0, POSITIONS);
        out.putDoubles("u5", u5, POSITIONS);
        out.putDoubles("u8", u8, POSITIONS);
        out.putDoubles("pu9", pu9, POSITIONS);
        out.putDoubles("fp", fp, POSITIONS);
        out.putDoubles("bu", burnup, POSITIONS);
        out.putDoubles("u6", u6, POSITIONS);
        out.putDoubles("pu0", pu0, POSITIONS);
        out.putDoubles("x234", x234, POSITIONS);
        int[] f = new int[POSITIONS];
        for (int i = 0; i < POSITIONS; i++) f[i] = failed[i] ? 1 : 0;
        out.putInts("failed", f, POSITIONS);
        out.putString("design", design == null ? "" : design.name());
        out.putDouble("az", az);
        out.putDouble("rg", rg);
        out.putDouble("sp", setpoint);
        out.putDouble("tf", fuelK);
        out.putDouble("hh", hHot);
        out.putDouble("hc", hCold);
        out.putDouble("loop", loopKg);
        out.putDouble("boron", boronKg);
        out.putDouble("xch", exchangeKgS);
        out.putDouble("xchg", exchangeGkg);
        out.putDouble("leak", leakAreaM2);
        out.putDouble("si", siHold);
        out.putDouble("n", power());
        out.putInts("lowflux", new int[] {protection.isTripped() ? 0 : lowFluxBlocked ? 1 : 0}, 1);
        out.putInts("tripped", new int[] {protection.isTripped() ? 1 : 0}, 1);
        out.putInts("ruptured", new int[] {ruptured ? 1 : 0}, 1);
        out.beginObject("pzr");
        pzr.save(out);
        out.endObject();
        for (int i = 0; i < LOOPS; i++) {
            out.beginObject("pump" + i);
            pumps[i].save(out);
            out.endObject();
        }
        out.beginObject("poisons");
        poisons.save(out);
        out.endObject();
        out.beginObject("decay");
        decay.save(out);
        out.endObject();
    }

    public void load(StateReader in) {
        copy(in, "hm0", hm0);
        copy(in, "u5", u5);
        copy(in, "u8", u8);
        copy(in, "pu9", pu9);
        copy(in, "fp", fp);
        copy(in, "bu", burnup);
        copy(in, "u6", u6);
        copy(in, "pu0", pu0);
        copy(in, "x234", x234);
        if (in.has("failed")) {
            int[] f = in.getInts("failed");
            for (int i = 0; i < Math.min(POSITIONS, f.length); i++) failed[i] = f[i] != 0;
        }
        String d = in.optString("design", "");
        design = null;
        for (FuelDesign x : FuelDesign.values()) if (x.name().equals(d)) design = x;
        az = in.optDouble("az", 1);
        rg = in.optDouble("rg", 1);
        setpoint = in.optDouble("sp", 1);
        fuelK = in.optDouble("tf", fuelK);
        hHot = in.optDouble("hh", hHot);
        hCold = in.optDouble("hc", hCold);
        exchangeKgS = in.optDouble("xch", 0);
        exchangeGkg = in.optDouble("xchg", 0);
        leakAreaM2 = in.optDouble("leak", 0);
        siHold = in.optDouble("si", 0);
        if (in.has("pzr")) {
            in.beginObject("pzr");
            pzr.load(in);
            in.endObject();
        }
        for (int i = 0; i < LOOPS; i++)
            if (in.has("pump" + i)) {
                in.beginObject("pump" + i);
                pumps[i].load(in);
                in.endObject();
            }
        if (in.has("poisons")) {
            in.beginObject("poisons");
            poisons.load(in);
            in.endObject();
        }
        if (in.has("decay")) {
            in.beginObject("decay");
            decay.load(in);
            in.endObject();
        }
        pressure = pzr.pressure();
        loopKg = in.optDouble("loop", requiredLoopKg(pressure, hHot, hCold));
        boronKg = in.optDouble("boron", boronKg);
        if (in.has("ruptured")) ruptured = in.getInts("ruptured")[0] != 0;
        countLoaded();
        properties();
        solveStatic();
        double n = Math.max(PointKinetics.MIN_POWER, in.optDouble("n", 0));
        kinetics.resetToEquilibrium(n);
        lowFluxBlocked = in.has("lowflux") && in.getInts("lowflux")[0] != 0;
        protection.restore(n, lowFluxBlocked);
        if (!in.has("tripped") || in.getInts("tripped")[0] != 0) protection.requestTrip(TripReason.MANUAL, TripSource.OPERATOR_PANEL);
    }

    private static void copy(StateReader in, String key, double[] to) {
        if (!in.has(key)) return;
        double[] a = in.getDoubles(key);
        System.arraycopy(a, 0, to, 0, Math.min(a.length, to.length));
    }
}
