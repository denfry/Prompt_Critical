package dev.denfry.promptcritical.sim.npp;

import dev.denfry.promptcritical.sim.accident.ReactivityInitiatedAccident;
import dev.denfry.promptcritical.sim.reactor.pwr.CoolantPump;
import dev.denfry.promptcritical.sim.reactor.pwr.Pressurizer;
import dev.denfry.promptcritical.sim.reactor.pwr.SecondarySide;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.fuel.FuelDesign;
import dev.denfry.promptcritical.sim.nuclide.PlutoniumCharge;
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
    /**
     * Ejected cluster as a share of the control group's worth (04c §4c.5.1): ПР-300 loses 0.7–1.0β with one cluster of a
     * fully inserted 2000 pcm bank at hot zero power and 0.1–0.2β at power; β ≈ 680 pcm there, so one cluster of four is
     * worth up to 0.34 of the bank with the field tilt. The upper end is taken: on this 2500 pcm group about 1.3β fully
     * in, about 0.2β at the working position 0.3.
     */
    public static final double EJECTED_RG_SHARE = 0.34;
    /**
     * Rod-housing nozzle torn open with the cluster: about 20 cm² on ПР-300 (04c §4c.5.1), scaled by power 100/300 as the
     * accident layer's breaks are.
     */
    public static final double ROD_HOUSING_BREAK_M2 = 20e-4 / 3;
    /** UO₂ melting point, K: the lumped fuel does not go above it on a burst (04c §4c.5.3). */
    public static final double UO2_MELT_K = 3120;

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

    // ----------------------------------------------------------------- зона
    private final LightWaterCore core = new LightWaterCore(NOMINAL_W, DT);
    private final double[] dnbTimer = new double[POSITIONS];

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
    /** ТЭНы КД: мощность, которую просит регулятор, Вт, и доля, которую даёт сеть собственных нужд 0,4 кВ. */
    private double heaterDemandW, heaterSupply = 1;
    private double flowKgS, qCoreW, qSgW, voidM3;
    private double hotK, coldK, coolantDensity, pressure;
    private double secondaryK = Double.NaN, sgLevel = Double.NaN;
    private int sgCount;
    private boolean ruptured, ruptureEvent;
    /** Share of the control group's worth missing with an ejected cluster (0 — none). */
    private double ejectedShare;
    private ReactivityInitiatedAccident.Pulse lastPulse;
    private ReactivityInitiatedAccident.Damage lastRiaDamage = ReactivityInitiatedAccident.Damage.NONE;
    /** Steam-generator tube rupture: break area to the secondary and the back pressure of the damaged SG. */
    private double tubeLeakAreaM2, tubeBackPa = SecondarySide.P_NOMINAL, tubeLeakKgS;
    /** Pressurizer relief valve held open from the panel: the operator's depressurisation (04c §4c.6.4, §4c.10.3). */
    private boolean porvOpen;
    private int newlyFailed;
    private double mdnbr = Double.POSITIVE_INFINITY;
    private final DnbrCalculator trueDnbr;
    private final HotChannelModel hotChannel;
    private final ThermoState ts = new ThermoState();
    private final double[] satBuf = new double[17];
    private final SaturationState sat = new SaturationState();

    public PressurizedWaterReactor() {
        for (int i = 0; i < LOOPS; i++) pumps[i] = new CoolantPump(CoolantPump.INERTIA_KGM2, PUMP_RPM, PUMP_SHAFT_W);
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

    /** Шаг ТВС под ключ, м — {@link LightWaterCore#pitch}. */
    public static double pitch(FuelDesign d) {
        return LightWaterCore.pitch(d);
    }

    /** Конструкции ТВС, которые принимает корпус. */
    public static boolean accepts(FuelDesign d) {
        return LightWaterCore.accepts(d);
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
        return load(pos, d, hmKg, u235, u234, PlutoniumCharge.NONE);
    }

    /** Загрузка ТВС МОКС: уран {@code uKg} (доли U-235 и U-234 в уране) и плутоний {@code pu} ({@link LightWaterCore}). */
    public boolean load(int pos, FuelDesign d, double uKg, double u235, double u234, PlutoniumCharge pu) {
        if (!core.load(shutDown(), pos, d, uKg, u235, u234, pu)) return false;
        dnbTimer[pos] = 0;
        return true;
    }

    public LightWaterCore.AssemblyFuel fuel(int pos) {
        return core.fuel(pos);
    }

    public LightWaterCore.AssemblyFuel unload(int pos) {
        dnbTimer[pos] = 0;
        return core.unload(shutDown(), pos);
    }

    public boolean loaded(int pos) {
        return core.loaded(pos);
    }

    public int loadedAssemblies() {
        return core.loadedAssemblies();
    }

    public FuelDesign design() {
        return core.design();
    }

    public LightWaterCore core() {
        return core;
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
        protectionStep();
        moveRods();
        core.step(gameDt, feedbackState(), rodReactivity());
        hydraulics();
        dnb();
    }

    private double coreHeight() {
        return core.coreHeight();
    }

    private double moderatorK() {
        return (hotK + coldK) / 2;
    }

    private CellFeedbackState feedbackState() {
        return core.state(fuelK, coolantDensity, moderatorK(), boronPpm());
    }

    private static double sCurve(double x) {
        return x - Math.sin(2 * Math.PI * x) / (2 * Math.PI);
    }

    /** Реактивность стержней, Δk/k (отрицательная). */
    public double rodReactivity() {
        return -(WORTH_AZ * sCurve(az) + WORTH_RG * (1 - ejectedShare) * sCurve(rg));
    }

    // ------------------------------------------------------------------ RIA (04c §4c.5)

    /** A cluster of the control group blown out of the core by the primary pressure: {@link #EJECTED_RG_SHARE}. */
    public ReactivityInitiatedAccident.Pulse ejectRodCluster() {
        return ejectRodCluster(EJECTED_RG_SHARE);
    }

    /**
     * Ejects a cluster worth {@code share} of the control group (1 — a cell with a single cluster for the whole group,
     * the ~3β design mistake of §4c.5.1). Above prompt criticality the burst is not resolved on the 50 ms step: its
     * Nordheim–Fuchs energy goes straight into the fuel, whose Doppler feedback then leaves the core at β − (ρ₀ − β),
     * and the burst's power trips the reactor on high flux. Below it the reactor's own kinetics carry the excursion.
     * Failed cladding of the burst counts into {@link #newlyFailed()} until the next {@link #step}.
     *
     * @return the burst, or null when no cluster could be ejected (one is already out, or the core is empty)
     */
    public ReactivityInitiatedAccident.Pulse ejectRodCluster(double share) {
        if (ejectedShare > 0 || core.loadedAssemblies() == 0 || !(share > 0)) return null;
        double tf = fuelK;
        ejectedShare = Math.min(1, share);
        CellFeedbackState s = feedbackState();
        double rho0 = core.reactivity(s, rodReactivity());
        double cf = fuelHeatCapacity();
        double gamma = Math.max(1e-15, Math.abs(core.dopplerCoefficient())) / cf;
        double beta = core.betaEff() > 0 ? core.betaEff() : 0.0065;
        double lambda = core.generationTime() > 0 ? core.generationTime() : 2e-5;
        ReactivityInitiatedAccident.Pulse p = ReactivityInitiatedAccident.nordheimFuchs(rho0, beta, lambda, gamma);
        lastPulse = p;
        lastRiaDamage = ReactivityInitiatedAccident.assess(p, core.heavyMetalKg() * ReactivityInitiatedAccident.UO2_PER_HM, tf,
                core.maxBurnup());
        if (p.prompt()) {
            fuelK = Math.min(UO2_MELT_K, fuelK + p.energyJ() / cf);
            protection.requestTrip(TripReason.HIGH_FLUX, TripSource.AUTOMATIC);
            int n = (int) Math.ceil(lastRiaDamage.cladFailedFraction() * core.loadedAssemblies() - 1e-9);
            if (n > 0) newlyFailed += core.failHottest(n);
        }
        return p;
    }

    /** A cluster is out of the core (until {@link #reseatRodCluster}). */
    public boolean rodClusterEjected() {
        return ejectedShare > 0;
    }

    /** The drill is over: the cluster and its drive are back — only on a shut-down reactor. */
    public boolean reseatRodCluster() {
        if (ejectedShare <= 0 || !shutDown()) return false;
        ejectedShare = 0;
        return true;
    }

    /** Last burst of {@link #ejectRodCluster} (null — none since load). */
    public ReactivityInitiatedAccident.Pulse lastPulse() {
        return lastPulse;
    }

    public ReactivityInitiatedAccident.Damage lastRiaDamage() {
        return lastRiaDamage;
    }

    private double fuelHeatCapacity() {
        return Math.max(1e5, FUEL_C_J_PER_KG_HM * core.heavyMetalKg());
    }

    // ------------------------------------------------------------------ SGTR (04c §4c.6)

    /**
     * Steam-generator tubes torn open to the secondary: area in m² and the pressure of the damaged SG. The flow is the
     * subcooled break flow of the cold side of the bundle, {@code G = 0.6·A·√(2ρ(P − max(P_sat(T_cold), P_SG)))}
     * (§4c.6.1: C_D = 0.6 of a short sharp-edged orifice; the cold water flashes in the hole), and it stops once the
     * primary is down at the damaged SG's pressure or the cold leg's saturation.
     */
    public void setTubeRupture(double areaM2, double sgPressurePa) {
        tubeLeakAreaM2 = Math.max(0, areaM2);
        tubeBackPa = Math.max(0.1e6, sgPressurePa);
    }

    /**
     * Opens the pressurizer relief valve by hand (or closes it back to its 17 MPa setpoint): the primary blows steam to
     * the relief tank at the valve's capacity, proportional to pressure — how the operator brings the primary down to the
     * damaged generator's pressure in an SGTR, or below the vessel's high-pressure failure range in a core melt.
     */
    public void setPorvOpen(boolean open) {
        porvOpen = open;
    }

    public boolean porvOpen() {
        return porvOpen;
    }

    public double tubeRuptureArea() {
        return tubeLeakAreaM2;
    }

    /** Primary-to-secondary leak through ruptured tubes, kg/s. */
    public double tubeLeakKgS() {
        return tubeLeakKgS;
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
        double heat = core.heatW();
        double natural = NATURAL_FLOW * Math.cbrt(Math.max(0, heat) / NOMINAL_W);
        double flowFrac = Math.max(Math.max(0.005, natural), speedSum / LOOPS);
        flowKgS = NOMINAL_FLOW_KG_S * flowFrac;
        // топливо
        double hmTotal = 0, rodLength = 0;
        hmTotal = core.heavyMetalKg();
        rodLength = core.rodLengthM();
        double cFuel = Math.max(1e5, FUEL_C_J_PER_KG_HM * hmTotal);
        double uaFuel = Math.max(1e3, rodLength * 1000 / FUEL_R_K_PER_KW_M);
        double tCoolCore = coldK > 0 ? (hotK + coldK) / 2 : 573.15;
        qCoreW = uaFuel * (fuelK - tCoolCore);
        double toFuel = core.loadedAssemblies() > 0 ? heat : 0;
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
        tubeLeakKgS = tubeLeakAreaM2 > 0 ? 0.6 * tubeLeakAreaM2 * Math.sqrt(2 * rhoCold * Math.max(0, p - Math.max(pSatCold, tubeBackPa))) : 0;
        siKgS = siHold > 0 ? SI_MAX_KG_S * Math.max(0, Math.min(1, (SI_SHUTOFF_PA - p) / (SI_SHUTOFF_PA - 3e6))) : 0;
        double hCharge = W.enthalpyPT(p, CHARGING_K), hSi = W.enthalpyPT(p, SI_K);
        double inKg = (chargingKgS + exchangeKgS + siKgS) * DT;
        double outKg = (letdown + leakKgS + tubeLeakKgS) * DT;
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
        heaterDemandW = pzr.level() < 0.1 ? 0
                : HEATER_PROP_W * Math.max(0, Math.min(1, (0.1e6 - pErr) / 0.2e6)) + (pErr < -0.15e6 ? HEATER_BACKUP_W : 0);
        heaterW = heaterDemandW * heaterSupply;
        // ИПУ ключом оператора: открыт при любом давлении, расход — как у ИПУ на уставке, ∝ P (critical flow)
        double reliefPa = porvOpen ? 0.1e6 : RELIEF_PA, reliefKgS = porvOpen ? RELIEF_KG_S * 0.1e6 / RELIEF_PA : RELIEF_KG_S;
        double outH = pzr.step(DT, surge, hHot, sprayKgS, hCold, heaterW - PZR_LOSS_W, reliefPa, reliefKgS, SAFETY_PA, SAFETY_KG_S);
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
        mdnbr = trueDnbr.calculate(pressure, coldK, flowKgS / NOMINAL_FLOW_KG_S, core.heatW() / NOMINAL_W, Double.NaN);
        // пар заполнил весь горячий узел (верхняя камера, горячие нитки) — уровень смеси опускается в зону: кризис
        if (voidM3 >= 0.98 * V_HOT) mdnbr = Math.min(mdnbr, 0.8);
        double maxShare = core.maxShare();
        for (int i = 0; i < POSITIONS; i++) {
            if (!core.loaded(i) || core.failed(i)) continue;
            // горячий канал — у ТВС с наибольшей долей; у остальных запас больше на отношение долей
            double local = mdnbr * (maxShare > 0 ? maxShare / Math.max(1e-6, core.share(i)) : 1);
            dnbTimer[i] = local < 1 ? dnbTimer[i] + DT : 0;
            if (dnbTimer[i] >= DNB_FAIL_S) {
                core.fail(i);
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
                case SHORT_PERIOD -> core.periodS();
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
            return core.periodS();
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
        return core.power();
    }

    public double fissionW() {
        return core.fissionW();
    }

    public double decayHeatW() {
        return core.decayHeatW();
    }

    public double periodS() {
        return core.periodS();
    }

    public double reactivity() {
        return core.reactivity();
    }

    public double keffStatic() {
        return core.keffStatic();
    }

    public double betaEff() {
        return core.betaEff();
    }

    public double generationTime() {
        return core.generationTime();
    }

    public DelayedGroups delayedGroups() {
        return core.delayedGroups();
    }

    /** Коэффициенты реактивности последнего пересчёта: Доплер, 1/К; по плотности воды, 1/(кг/м³); спектральный по T воды, 1/К; бор, 1/ppm. */
    public double dopplerCoefficient() {
        return core.dopplerCoefficient();
    }

    public double densityCoefficient() {
        return core.densityCoefficient();
    }

    public double moderatorSpectralCoefficient() {
        return core.moderatorSpectralCoefficient();
    }

    public double boronCoefficient() {
        return core.boronCoefficient();
    }

    /** Температурный коэффициент реактивности теплоносителя (по плотности и спектру), 1/К. */
    public double moderatorTemperatureCoefficient() {
        double t = moderatorK(), h = (hHot + hCold) / 2;
        double cp = 5.5e3;
        double drho = densityPH(pressure, h + cp) - densityPH(pressure, h - cp);
        return core.densityCoefficient() * drho / 2 + core.moderatorSpectralCoefficient();
    }

    public double xenonReactivity() {
        double tn = moderatorK() + LatticeModel.NEUTRON_GAS_OFFSET;
        return core.xenonReactivity(moderatorK());
    }

    public FissionPoisons poisons() {
        return core.poisons();
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

    /** Мощность ТЭНов, которую просит регулятор давления, Вт (до ограничения сетью). */
    public double heaterDemandW() {
        return heaterDemandW;
    }

    /** Доля мощности ТЭНов, которую даёт сеть собственных нужд, 0…1 (без питания — 0). */
    public void setHeaterSupply(double f) {
        heaterSupply = Double.isFinite(f) ? Math.max(0, Math.min(1, f)) : 0;
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
        core.invalidate();
    }

    public double primaryKg() {
        return loopKg + pzr.liquidKg() + pzr.vaporKg();
    }

    public double voidM3() {
        return voidM3;
    }

    public double burnup(int pos) {
        return core.burnup(pos);
    }

    public double maxBurnup() {
        return core.maxBurnup();
    }

    public boolean failed(int pos) {
        return core.failed(pos);
    }

    public int failedAssemblies() {
        return core.failedAssemblies();
    }

    public int newlyFailed() {
        return newlyFailed;
    }

    /**
     * Cladding breached from outside the DNB check — a core heat-up after uncovery in an accident model. Counts into
     * {@link #newlyFailed()} until the next {@link #step}.
     */
    public int failCladding(int n) {
        int f = core.failHottest(n);
        newlyFailed += f;
        return f;
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
        core.invalidate();
    }

    public void equilibrate(double n) {
        core.equilibrate(feedbackState(), n);
        lowFluxBlocked = n > 0.1;
        protection.restore(n, lowFluxBlocked);
    }

    /** Пересчитать ячейку и вернуть реактивность при текущем состоянии без шага (подбор критичности, тесты). */
    public double evaluateReactivity() {
        CellFeedbackState s = feedbackState();
        core.solveStatic(s);
        return core.reactivity(s, rodReactivity());
    }

    public void setRods(double az, double rg) {
        this.az = az;
        this.rg = rg;
    }

    // ------------------------------------------------------------------ сохранение

    public void save(StateWriter out) {
        core.save(out);
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
        out.putInts("lowflux", new int[] {protection.isTripped() ? 0 : lowFluxBlocked ? 1 : 0}, 1);
        out.putInts("tripped", new int[] {protection.isTripped() ? 1 : 0}, 1);
        out.putInts("ruptured", new int[] {ruptured ? 1 : 0}, 1);
        out.putDouble("ejected", ejectedShare);
        if (lastPulse != null) {
            ReactivityInitiatedAccident.Damage d = lastRiaDamage;
            out.putDoubles("ria", new double[] {lastPulse.rho0(), lastPulse.beta(), lastPulse.generationS(), lastPulse.gammaPerJ(),
                    d.peakCalG(), d.averageRiseCalG(), d.cladFailedFraction(), d.fragmentedFraction(), d.failure().ordinal()}, 9);
        }
        out.putDouble("tube", tubeLeakAreaM2);
        out.putDouble("tubeP", tubeBackPa);
        out.putBoolean("porv", porvOpen);
        out.beginObject("pzr");
        pzr.save(out);
        out.endObject();
        for (int i = 0; i < LOOPS; i++) {
            out.beginObject("pump" + i);
            pumps[i].save(out);
            out.endObject();
        }
    }

    public void load(StateReader in) {
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
        pressure = pzr.pressure();
        loopKg = in.optDouble("loop", requiredLoopKg(pressure, hHot, hCold));
        boronKg = in.optDouble("boron", boronKg);
        if (in.has("ruptured")) ruptured = in.getInts("ruptured")[0] != 0;
        ejectedShare = Math.max(0, Math.min(1, in.optDouble("ejected", 0)));
        lastPulse = null;
        lastRiaDamage = ReactivityInitiatedAccident.Damage.NONE;
        if (in.has("ria")) {
            double[] a = in.getDoubles("ria");
            if (a.length >= 9 && a[1] > 0 && a[2] > 0 && a[3] > 0) {
                lastPulse = ReactivityInitiatedAccident.nordheimFuchs(a[0], a[1], a[2], a[3]);
                ReactivityInitiatedAccident.Failure[] modes = ReactivityInitiatedAccident.Failure.values();
                lastRiaDamage = new ReactivityInitiatedAccident.Damage(a[4], a[5], a[6], a[7], modes[Math.max(0, Math.min(modes.length - 1, (int) a[8]))]);
            }
        }
        tubeLeakAreaM2 = Math.max(0, in.optDouble("tube", 0));
        tubeBackPa = in.optDouble("tubeP", SecondarySide.P_NOMINAL);
        porvOpen = in.optInt("porv", 0) != 0;
        properties();
        core.load(in, this::feedbackState);
        double n = LightWaterCore.savedPower(in);
        lowFluxBlocked = in.has("lowflux") && in.getInts("lowflux")[0] != 0;
        protection.restore(n, lowFluxBlocked);
        if (!in.has("tripped") || in.getInts("tripped")[0] != 0) protection.requestTrip(TripReason.MANUAL, TripSource.OPERATOR_PANEL);
    }

}
