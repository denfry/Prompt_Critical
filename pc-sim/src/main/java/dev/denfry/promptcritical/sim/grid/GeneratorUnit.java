package dev.denfry.promptcritical.sim.grid;

import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;

/**
 * {@code shaft_generator} (07 §7.3.1): синхронная машина 500 кВ·А на валу Create с машинным возбудителем
 * {@code generator_exciter}. Вал Create через встроенный мультипликатор задаёт скорость ω_cmd = n/n_ном·(1 + подстройка);
 * момент привода — K_c·(ω_cmd − ω) с насыщением (03a §3a.2.2, обгонная муфта), K_c = 25 даёт статизм 4 %, как у
 * регулятора турбины. Мощность вала объявляется стрессом Create; срыв сети Create (overstress) снимает момент.
 *
 * <p>Ниже 0,45 ω₀ машина выходит из сети (ЭДС нет, шина без источника), выше 0,5 ω₀ — входит; вне сети ротор
 * крутит эта же модель привода.
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
    public static final int[] GEARS = {16, 32, 64, 128, 256};
    /** Подстройка мультипликатора (вариатор), ‰: ±50 с шагом 1 [моё]. */
    public static final int TRIM_LIMIT = 50;

    public final ShaftInput shaft = new ShaftInput();
    VoltageClass voltageClass = VoltageClass.LV_0_4;
    int gear = 2, trimPermil;
    boolean exciter, fieldOn, pinInstalled;
    double vrefPu = 1.0;

    MachineId machine;
    SynchronousMachine model;
    /** Состояние машины между сборками сети и в сохранении; {@code null} — машина новая (стоит). */
    double[] carried;
    double speedPu, shaftPowerW;
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
        return Math.max(0, Math.min(DRIVE_MAX_TORQUE, DRIVE_STIFFNESS * (commandSpeed() - w)));
    }

    /**
     * Мощность на роторе при скорости ω (вход {@link GridInputs#createShaftPowerW}), Вт: привод от вала Create минус
     * трение подшипников и вентиляция. Create отдаёт только мощность привода ({@link #shaftPowerW}).
     */
    double shaftPower(double w) {
        double ws = Math.max(0, w);
        double t = driveTorque(ws);
        shaftPowerW = t * ws * RATED_VA;
        return (t - friction(ws)) * ws * RATED_VA;
    }

    private static double friction(double w) {
        return w > 0 ? FRICTION_PU + VISCOUS_PU * w : 0;
    }

    /** Ротор вне сети: полунеявный шаг привода (жёсткий член K_c), трение тормозит до нуля. */
    void stepOffline(double dt) {
        double h2 = 2 * INERTIA_S;
        double w = speedPu, cmd = commandSpeed();
        double fr = friction(w);
        double wi = (h2 * w + dt * (DRIVE_STIFFNESS * cmd - fr)) / (h2 + dt * DRIVE_STIFFNESS);
        double t = DRIVE_STIFFNESS * (cmd - wi);
        if (t > DRIVE_MAX_TORQUE) wi = w + dt * (DRIVE_MAX_TORQUE - fr) / h2;
        else if (t < 0) wi = Math.max(0, w - dt * fr / h2);
        speedPu = Math.max(0, wi);
        shaftPowerW = driveTorque(speedPu) * speedPu * RATED_VA;
    }

    // --- сборка ---

    @Override
    void build(GridSubsystem.Build b) {
        if (node(0) == NO_NODE) return;
        MachineParams p = MachineParams.shaftGenerator(RATED_VA, voltageClass.volts);
        model = new SynchronousMachine(p, ExciterSpec.rotating(), new PrimeMover.CreateShaft(1, RATED_POWER_W));
        machine = b.grid.addMachine(model, b.bus(node(0)));
        b.machine(machine, this);
        double[] s = carried != null ? carried : coldState();
        b.grid.importMachine(machine, s);
        if (pinInstalled) model.setShearPinCoupling(true);
        speedPu = s[1];
        boolean on = speedPu >= ONLINE_SPEED;
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
        if (machine == null) {
            stepOffline(dt);
            return;
        }
        if (g.machineOnline(machine)) {
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
        if (!g.machineOnline(machine)) shaftPowerW = driveTorque(speedPu) * speedPu * RATED_VA;
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
        if (carried != null && carried.length != PowerGrid.MACHINE_STATE) carried = null;
    }
}
