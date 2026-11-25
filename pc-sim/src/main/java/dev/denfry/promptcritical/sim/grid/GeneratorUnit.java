package dev.denfry.promptcritical.sim.grid;

import dev.denfry.promptcritical.sim.combustion.DieselEngine;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.manufacturing.assembly.MachinePassport;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;

/**
 * {@code shaft_generator} (07 §7.3.1): синхронная машина 500 кВ·А на валу Create с машинным возбудителем
 * {@code generator_exciter}. Вал Create через встроенный мультипликатор задаёт скорость ω_cmd = n/n_ном·(1 + подстройка);
 * момент привода — K_c·(ω_cmd − ω) с насыщением (03a §3a.2.2, обгонная муфта), K_c = 25 даёт статизм 4 %, как у
 * регулятора турбины. Мощность вала объявляется стрессом Create; срыв сети Create (overstress) снимает момент.
 *
 * <p>Ниже 0,45 ω₀ машина выходит из сети (ЭДС нет, шина без источника), выше 0,5 ω₀ — входит; вне сети ротор
 * крутит эта же модель привода.
 *
 * <p>{@code diesel_generator} (§7.11) — та же машина с явнополюсным генератором 750 об/мин и своим первичным
 * двигателем {@link DieselEngine} вместо вала Create. У него встроенный генераторный выключатель: агрегат в сети
 * только при включённом выключателе. В режиме «автомат» потеря напряжения шины на 1 с — сигнал пуска (LOOP), а
 * готовый агрегат сам включается на обесточенную шину (без синхронизации, §7.11.3). На шину под напряжением
 * выключатель не включается — параллельная работа только через ячейку КРУ с синхронизацией.
 */
public final class GeneratorUnit extends GridUnit {
    public static final double RATED_VA = 500e3, RATED_POWER_W = 400e3;
    /** Жёсткость привода от вала Create, о.е. момента на о.е. скорости (статизм 1/K_c = 4 %) [BALANCE]. */
    public static final double DRIVE_STIFFNESS = 25;
    /** Предел момента привода, о.е.: пусковой момент 2× номинального (03a §3a.2.2). */
    public static final double DRIVE_MAX_TORQUE = 2;
    /** Трение вне сети: постоянная и вязкая части, о.е. момента. */
    static final double FRICTION_PU = 0.005, VISCOUS_PU = 0.01;
    public static final double ONLINE_SPEED = 0.5, OFFLINE_SPEED = 0.45;
    /** Оператор возбуждения раз в 5 с подтягивает уставку, если напряжение ушло дальше 0,3 % (статизм АРВ K = 20). */
    static final int VREF_TRIM_TICKS = 100;
    static final double VREF_TRIM_PU = 0.003;
    /** H агрегата, с (табл. §7.3.1, без маховика Create). */
    static final double INERTIA_S = 0.6;
    /** H дизель-генератора вместе с дизелем, с (§7.11.2). */
    static final double DIESEL_INERTIA_S = 1.0;
    /** Шина считается обесточенной ниже этого напряжения, о.е. */
    public static final double DEAD_BUS_PU = 0.1;
    /** Пуск ДГ по потере напряжения: ниже 0,7 о.е. дольше 1 с [моё: защита минимального напряжения 27]. */
    static final double LOOP_PU = 0.7, LOOP_DELAY_S = 1.0;
    /** Скорость готового агрегата — в пределах ±2 % (IEEE 387, §7.11.4). */
    static final double READY_SPEED_BAND = 0.02;
    /** Интервал ступеней секвенсора, с (§7.11.4). */
    public static final double SEQUENCE_STEP_S = 5;
    public static final int[] GEARS = {16, 32, 64, 128, 256};
    /** Подстройка мультипликатора (вариатор), ‰: ±50 с шагом 1 [моё]. */
    public static final int TRIM_LIMIT = 50;

    public final ShaftInput shaft = new ShaftInput();
    VoltageClass voltageClass = VoltageClass.LV_0_4;
    int gear = 2, trimPermil;
    boolean exciter, fieldOn, pinInstalled;
    double vrefPu = 1.0;

    /** Первичный двигатель ДГ; {@code null} — привод от вала Create. */
    DieselEngine diesel;
    double dieselVa;
    boolean breakerClosed, autoStart, sequencer;
    double undervoltageS, stepS;
    /** Потребители, снятые секвенсором и ещё не включённые обратно, — по порядку ступеней. */
    long[] pending = new long[0];

    MachineId machine;
    SynchronousMachine model;
    /** Состояние машины между сборками сети и в сохранении; {@code null} — машина новая (стоит). */
    double[] carried;
    double speedPu, shaftPowerW;
    /** КПД по паспорту сборки к номиналу (18 §18.8.3); 1 — номинал. Задаёт мод, в сохранение не идёт. */
    double passportEfficiency = 1;
    long tick;
    /** Скачок нагрузки на сеть Create от несинхронного включения, Вт; мод забирает его один раз. */
    double pendingShockW;
    SyncClosing lastSync;
    int syncCount;
    boolean appliedField;
    double appliedVref = Double.NaN;

    GeneratorUnit(SimId id) {
        super(id, 1);
    }

    /** Сделать машину дизель-генератором мощностью {@code ratedVa} (один раз, до первой сборки). */
    void makeDiesel(double ratedVa) {
        if (diesel != null) return;
        dieselVa = ratedVa;
        diesel = new DieselEngine(ratedVa, id.raw());
        exciter = true; // возбуждение агрегата встроенное, начальное — от АКБ (§7.11.5)
    }

    /** Паспорт машины при текущем классе напряжения: генератор на валу или ДГ (07 §7.3.1). */
    public MachineParams params() {
        return diesel != null ? MachineParams.dieselGenerator(dieselVa, voltageClass.volts)
                : MachineParams.shaftGenerator(RATED_VA, voltageClass.volts);
    }

    /** Номинальная полная мощность машины, В·А. */
    public double ratedVa() {
        return diesel != null ? dieselVa : RATED_VA;
    }

    private double inertiaS() {
        return diesel != null ? DIESEL_INERTIA_S : INERTIA_S;
    }

    // --- дизель-генератор ---

    /** Двигатель ДГ; {@code null} у генератора на валу Create. */
    public DieselEngine diesel() {
        return diesel;
    }

    public boolean breakerClosed() {
        return breakerClosed;
    }

    public boolean autoStart() {
        return autoStart;
    }

    /** Ключ режима ДГ: «автомат» — пуск по потере напряжения шины и включение на обесточенную шину. */
    public void setAutoStart(boolean auto) {
        autoStart = auto;
        undervoltageS = 0;
    }

    /** Напряжение шины выводов, о.е. (0 — шины нет или она без источника). */
    public double busVoltagePu() {
        return owner == null ? 0 : owner.voltageV(node(0)) / voltageClass.volts;
    }

    /** Агрегат готов принять нагрузку: на топливе, скорость и возбуждение в допуске (§7.11.3). */
    public boolean dieselReady() {
        return diesel != null && diesel.status() == DieselEngine.Status.RUNNING
                && Math.abs(speedPu() - 1) <= READY_SPEED_BAND && fieldOn;
    }

    /**
     * Включить генераторный выключатель вручную: только готовый агрегат и только на обесточенную шину.
     *
     * @return выключатель включён
     */
    public boolean closeBreaker() {
        if (!dieselReady() || busVoltagePu() > DEAD_BUS_PU) return false;
        breakerClosed = true;
        return true;
    }

    public void openBreaker() {
        breakerClosed = false;
    }

    public boolean sequencerInstalled() {
        return sequencer;
    }

    /** Секвенсор нагрузки {@code load_sequencer} (§7.11.4) в шкафу агрегата. */
    public void setSequencer(boolean installed) {
        sequencer = installed;
        if (!installed) pending = new long[0];
    }

    /** Ступеней секвенсора, ещё не включённых. */
    public int pendingSteps() {
        return pending.length;
    }

    /**
     * Секвенсор перед включением на обесточенную шину снимает двигатели и потребители острова; потом включает их по
     * одному через {@link #SEQUENCE_STEP_S} — сначала самые мощные (насосы безопасности).
     */
    private void stripLoads() {
        java.util.List<GridUnit> loads = owner.loadsOnIsland(node(0));
        loads.removeIf(u -> u instanceof MotorUnit m ? !m.contactorClosed() : !((ConsumerUnit) u).switchedOn());
        loads.sort(java.util.Comparator.comparingDouble(GeneratorUnit::stepPowerW).reversed());
        pending = new long[loads.size()];
        for (int i = 0; i < pending.length; i++) {
            GridUnit u = loads.get(i);
            pending[i] = u.id.raw();
            if (u instanceof MotorUnit m) m.setContactor(false);
            else ((ConsumerUnit) u).setSwitchedOn(false);
        }
        stepS = 0;
    }

    private static double stepPowerW(GridUnit u) {
        return u instanceof MotorUnit m ? m.ratedPowerW() : ((ConsumerUnit) u).demandW();
    }

    private void sequence(double dt) {
        if (pending.length == 0) return;
        if (!online()) {
            pending = new long[0]; // агрегат отключился: секвенсор сброшен, включение — оператором
            return;
        }
        stepS += dt;
        if (stepS < SEQUENCE_STEP_S) return;
        stepS = 0;
        GridUnit u = owner.unitById(new SimId(pending[0]));
        pending = java.util.Arrays.copyOfRange(pending, 1, pending.length);
        if (u instanceof MotorUnit m) m.setContactor(true);
        else if (u instanceof ConsumerUnit c) c.setSwitchedOn(true);
    }

    @Override
    VoltageClass terminalClass(int terminal) {
        return voltageClass;
    }

    @Override
    boolean definesClass(int terminal) {
        return true;
    }

    // --- уставки игрока ---

    public VoltageClass voltageClass() {
        return voltageClass;
    }

    /** Номинальное напряжение статора: 0,4 или 6,3 кВ. */
    public void setVoltageClass(VoltageClass vc) {
        if (vc != VoltageClass.LV_0_4 && vc != VoltageClass.MV_6_3) throw new IllegalArgumentException("class " + vc);
        if (vc == voltageClass) return;
        voltageClass = vc;
        changed();
    }

    public int gearRpm() {
        return GEARS[gear];
    }

    public void cycleGear() {
        gear = (gear + 1) % GEARS.length;
    }

    public int trimPermil() {
        return trimPermil;
    }

    public void setTrimPermil(int permil) {
        trimPermil = Math.max(-TRIM_LIMIT, Math.min(TRIM_LIMIT, permil));
    }

    public boolean exciterPresent() {
        return exciter;
    }

    /** Возбудитель стоит рядом с генератором (мир проверяет соседство). */
    public void setExciterPresent(boolean present) {
        exciter = present;
    }

    public boolean fieldOn() {
        return fieldOn;
    }

    /** Автомат гашения поля: включённый — АРВ держит напряжение, отключённый — поле гасится. */
    public void setFieldOn(boolean on) {
        fieldOn = on;
    }

    public double voltageReferencePu() {
        return vrefPu;
    }

    public void setVoltageReferencePu(double pu) {
        vrefPu = Math.max(0.9, Math.min(1.1, pu));
    }

    public boolean shearPinInstalled() {
        return pinInstalled;
    }

    /** Поставить муфту со срезным штифтом; срезанная заменяется новой. */
    public void installShearPin() {
        pinInstalled = true;
        if (model != null) model.setShearPinCoupling(true);
    }

    // --- показания ---

    public double speedPu() {
        return model != null && owner.ready() && owner.grid().machineOnline(machine) ? model.speedPu() : speedPu;
    }

    public double frequencyHz() {
        return speedPu() * owner.config().nominalFrequencyHz();
    }

    public double terminalVoltageV() {
        return online() ? owner.grid().busVoltageV(owner.busOf(node(0))) : 0;
    }

    public double electricalPowerW() {
        return online() ? model.electricalPowerW() : 0;
    }

    public double reactivePowerVar() {
        return online() ? model.reactivePowerVar() : 0;
    }

    /** Мощность, которую машина берёт с вала Create, Вт. */
    public double shaftPowerW() {
        return shaftPowerW;
    }

    public MachineState state() {
        return model != null && owner.ready() ? model.state() : MachineState.STOPPED;
    }

    public double shaftDamage() {
        return model != null ? model.shaftDamage() : carried != null ? carried[12] : 0;
    }

    public double statorDamage() {
        return model != null ? model.statorDamage() : carried != null ? carried[13] : 0;
    }

    public boolean couplingSheared() {
        return model != null ? model.couplingSheared() : carried != null && carried[14] != 0;
    }

    public SyncClosing lastSync() {
        return lastSync;
    }

    /** Сколько раз машину включали на параллельную работу (для тестов и журнала). */
    public int syncCount() {
        return syncCount;
    }

    /** Забрать скачок нагрузки на сеть Create после несинхронного включения, Вт (0 — его не было). */
    public double takeShockW() {
        double w = pendingShockW;
        pendingShockW = 0;
        return w;
    }

    boolean online() {
        return model != null && owner.ready() && owner.grid().machineOnline(machine);
    }

    // --- привод от вала Create ---

    /** Заданная валом Create скорость, о.е. */
    double commandSpeed() {
        return shaft.rpm(tick) / GEARS[gear] * (1 + trimPermil / 1000.0);
    }

    /** Момент обгонной муфты привода при скорости ω, о.е. на S_ном. */
    double driveTorque(double w) {
        if (diesel != null) return diesel.torquePu(w);
        return Math.max(0, Math.min(DRIVE_MAX_TORQUE, DRIVE_STIFFNESS * (commandSpeed() - w)));
    }

    /**
     * Мощность на роторе при скорости ω (вход {@link GridInputs#createShaftPowerW}), Вт: привод от вала Create минус
     * трение подшипников и вентиляция. Create отдаёт только мощность привода ({@link #shaftPowerW}).
     */
    double shaftPower(double w) {
        double ws = Math.max(0, w);
        double t = driveTorque(ws);
        shaftPowerW = t * ws * ratedVa();
        return (t - friction(ws) - passportLoss(t, ws)) * ws * ratedVa();
    }

    /**
     * Добавочные потери в пакете стали от паспорта сборки (18 §18.8.3), о.е. момента: η_ном·(1 − k) от момента
     * привода. Машина лучше номинала теряет меньше, но не меньше нуля: выигрыш не больше трения.
     */
    double passportLoss(double t, double w) {
        return Math.max(-friction(w), t * MachinePassport.NOMINAL_WINDING_EFFICIENCY * (1 - passportEfficiency));
    }

    /** КПД по паспорту к номиналу, {@code MachinePassport.efficiencyFactor()}. */
    public double passportEfficiency() {
        return passportEfficiency;
    }

    public void setPassportEfficiency(double k) {
        if (!(k > 0 && k <= 1.05)) throw new IllegalArgumentException("efficiency factor " + k);
        passportEfficiency = k;
    }

    private static double friction(double w) {
        return w > 0 ? FRICTION_PU + VISCOUS_PU * w : 0;
    }

    /** Ротор вне сети: полунеявный шаг привода (жёсткий член K_c), трение тормозит до нуля. */
    void stepOffline(double dt) {
        if (diesel != null) {
            double w = speedPu, t = diesel.torquePu(w);
            speedPu = Math.max(0, w + dt * (t - friction(w)) / (2 * inertiaS()));
            shaftPowerW = t * speedPu * ratedVa();
            return;
        }
        double h2 = 2 * INERTIA_S;
        double w = speedPu, cmd = commandSpeed();
        double fr = friction(w);
        double wi = (h2 * w + dt * (DRIVE_STIFFNESS * cmd - fr)) / (h2 + dt * DRIVE_STIFFNESS);
        double t = DRIVE_STIFFNESS * (cmd - wi);
        if (t > DRIVE_MAX_TORQUE) wi = w + dt * (DRIVE_MAX_TORQUE - fr) / h2;
        else if (t < 0) wi = Math.max(0, w - dt * fr / h2);
        speedPu = Math.max(0, wi);
        shaftPowerW = driveTorque(speedPu) * speedPu * ratedVa();
    }

    // --- сборка ---

    @Override
    void build(GridSubsystem.Build b) {
        if (node(0) == NO_NODE) return;
        MachineParams p = params();
        double ratedW = diesel != null ? DieselEngine.RATED_TORQUE_PU * dieselVa : RATED_POWER_W;
        model = new SynchronousMachine(p, ExciterSpec.rotating(), new PrimeMover.CreateShaft(1, ratedW));
        machine = b.grid.addMachine(model, b.bus(node(0)));
        b.machine(machine, this);
        double[] s = carried != null ? carried : coldState();
        b.grid.importMachine(machine, s);
        if (pinInstalled) model.setShearPinCoupling(true);
        speedPu = s[1];
        boolean on = speedPu >= ONLINE_SPEED && (diesel == null || breakerClosed);
        if (!on) b.grid.setMachineOnline(machine, false);
        appliedField = s[15] != ExcitationMode.MANUAL.ordinal();
        appliedVref = carried != null ? vrefPu : Double.NaN;
    }

    private double[] coldState() {
        double[] s = new double[PowerGrid.MACHINE_STATE];
        s[1] = speedPu;
        s[15] = ExcitationMode.MANUAL.ordinal();
        s[16] = 1;
        return s;
    }

    @Override
    void capture(PowerGrid g) {
        if (machine == null) return;
        if (carried == null) carried = new double[PowerGrid.MACHINE_STATE];
        g.exportMachine(machine, carried);
        speedPu = carried[1];
    }

    @Override
    void unbind() {
        machine = null;
        model = null;
    }

    /** Перед тиком сети: выход в сеть и из неё по скорости, возбуждение по уставкам. */
    void beforeTick(PowerGrid g, double dt, long tick) {
        this.tick = tick;
        if (diesel != null) dieselLogic(dt);
        if (machine == null) {
            stepOffline(dt);
            return;
        }
        if (diesel != null) {
            if (g.machineOnline(machine)) {
                if (!breakerClosed || model.speedPu() < OFFLINE_SPEED) {
                    speedPu = model.speedPu();
                    g.setMachineOnline(machine, false);
                }
            } else {
                stepOffline(dt);
                g.setMachineSpeed(machine, speedPu);
                if (breakerClosed && speedPu >= ONLINE_SPEED) g.setMachineOnline(machine, true);
            }
        } else if (g.machineOnline(machine)) {
            if (model.speedPu() < OFFLINE_SPEED) {
                speedPu = model.speedPu();
                g.setMachineOnline(machine, false);
            }
        } else {
            stepOffline(dt);
            g.setMachineSpeed(machine, speedPu);
            if (speedPu >= ONLINE_SPEED) g.setMachineOnline(machine, true);
        }
        boolean field = exciter && fieldOn;
        if (field != appliedField) {
            appliedField = field;
            if (field) {
                model.setExcitationMode(ExcitationMode.VOLTAGE);
                model.setVoltageReference(vrefPu);
                appliedVref = vrefPu;
            } else {
                model.setExcitationMode(ExcitationMode.MANUAL);
                model.setFieldVoltage(0);
            }
        } else if (field && (vrefPu != appliedVref || tick % VREF_TRIM_TICKS == 0 && g.machineOnline(machine)
                && Math.abs(model.terminalVoltagePu() - vrefPu) > VREF_TRIM_PU)) {
            model.setVoltageReference(vrefPu); // уставка с поправкой на нынешнее E_fd снимает статизм АРВ
            appliedVref = vrefPu;
        }
        if (!g.machineOnline(machine)) shaftPowerW = driveTorque(speedPu) * speedPu * ratedVa();
    }

    /**
     * Автоматика ДГ раз в тик: шаг двигателя, возбуждение по скорости, пуск по LOOP, включение на обесточенную
     * шину, отключение выключателя при останове двигателя (обратная мощность, 32R).
     */
    private void dieselLogic(double dt) {
        double w = speedPu();
        diesel.step(dt, w);
        fieldOn = diesel.fired() && w >= DieselEngine.RUN_SPEED;
        if (!diesel.fired()) breakerClosed = false;
        if (sequencer) sequence(dt);
        if (!autoStart || breakerClosed) {
            undervoltageS = 0;
            return;
        }
        double v = busVoltagePu();
        undervoltageS = v < LOOP_PU ? undervoltageS + dt : 0;
        if (undervoltageS >= LOOP_DELAY_S && diesel.status() == DieselEngine.Status.STANDBY) diesel.start();
        if (dieselReady() && v <= DEAD_BUS_PU) {
            if (sequencer) stripLoads();
            breakerClosed = true;
        }
    }

    // --- сохранение ---

    @Override
    void save(StateWriter out) {
        saveCommon(out);
        out.putString("class", voltageClass.name());
        out.putInt("gear", gear);
        out.putInt("trim", trimPermil);
        out.putBoolean("exciter", exciter);
        out.putBoolean("field", fieldOn);
        out.putBoolean("pin", pinInstalled);
        out.putDouble("vref", vrefPu);
        out.putDouble("speed", speedPu);
        if (carried != null) out.putDoubles("machine", carried, carried.length);
        out.putBoolean("is_diesel", diesel != null);
        if (diesel != null) {
            out.beginObject("diesel");
            out.putDouble("va", dieselVa);
            out.putBoolean("breaker", breakerClosed);
            out.putBoolean("auto", autoStart);
            out.putDouble("uv", undervoltageS);
            out.putBoolean("sequencer", sequencer);
            out.putDouble("step", stepS);
            out.putLongs("pending", pending, pending.length);
            diesel.save(out);
            out.endObject();
        }
    }

    @Override
    void load(StateReader in) {
        loadCommon(in);
        voltageClass = VoltageClass.valueOf(in.getString("class"));
        gear = Math.floorMod(in.getInt("gear"), GEARS.length);
        trimPermil = in.getInt("trim");
        exciter = in.getBoolean("exciter");
        fieldOn = in.getBoolean("field");
        pinInstalled = in.getBoolean("pin");
        vrefPu = in.getDouble("vref");
        speedPu = in.getDouble("speed");
        carried = in.has("machine") ? in.getDoubles("machine") : null;
        if (in.optInt("is_diesel", 0) != 0) {
            in.beginObject("diesel");
            makeDiesel(in.getDouble("va"));
            breakerClosed = in.getBoolean("breaker");
            autoStart = in.getBoolean("auto");
            undervoltageS = in.getDouble("uv");
            sequencer = in.getBoolean("sequencer");
            stepS = in.getDouble("step");
            pending = in.getLongs("pending");
            diesel.load(in);
            in.endObject();
        }
        // сохранение до двухосной модели — 17 чисел без E'_d; E'_d = 0, машина наберёт его за T'_q0
        if (carried != null && carried.length == PowerGrid.MACHINE_STATE - 1)
            carried = java.util.Arrays.copyOf(carried, PowerGrid.MACHINE_STATE);
        if (carried != null && carried.length != PowerGrid.MACHINE_STATE) carried = null;
    }
}
