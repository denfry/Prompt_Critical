package dev.denfry.promptcritical.sim.grid;

import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.wind.RotorPerformance;
import dev.denfry.promptcritical.sim.wind.WindTurbine;
import dev.denfry.promptcritical.sim.wind.WindTurbineSpec;

/**
 * {@code wind_turbine}: сетевая ВЭУ Д-30 ({@link WindTurbineSpec#balaklavaD30}) с асинхронным генератором 0,4 кВ
 * 100 кВт на одном выводе. Генератор — та же асинхронная машина сети, что у двигателя ({@link InductionMotorSpec}):
 * колесо через мультипликатор крутит её выше синхронной скорости, скольжение отрицательно, и по формуле Клосса
 * машина отдаёт активную мощность в сеть, потребляя из неё реактивную на намагничивание — как асинхронный
 * генератор Балаклавской ВЭС, работавший параллельно с ГРЭС. Своей ЭДС у машины нет: без синхронных машин
 * в острове шина обесточена, генератор не возбуждается и колесо идёт вразнос, пока его не остановит флюгер.
 *
 * <p>Момент нагрузки двигателя здесь — момент колеса на валу генератора со знаком минус ({@link #loadTorquePu}),
 * его сеть берёт на каждом подшаге при текущей скорости ротора. Пока вывод ни с чем не соединён, ротор крутит
 * сама модель колеса ({@link WindTurbine#stepFree}).
 */
public final class WindTurbineUnit extends GridUnit {
    public static final WindTurbineSpec SPEC = WindTurbineSpec.balaklavaD30();
    /** Генератор: T_max/T_н = 2,2, s_k = 0,1 (номинальное скольжение ≈ 2 %), пусковой ток 6 [каталожный порядок]. */
    static final double GEN_TMAX = 2.2, GEN_SK = 0.1, GEN_ISTART = 6;
    /** Напряжение на выводах, ниже которого сеть считается потерянной, о.е. */
    static final double LIVE_VOLTAGE = 0.7;

    final WindTurbine turbine;
    MotorId motor;
    /** Состояние машины между пересборками ({@link PowerGrid#MOTOR_STATE} чисел). */
    double[] carried;
    /** Контактор, которым управляет автоматика; отличие от сети — его отключила защита. */
    boolean appliedContactor;
    double energyJ, exportW;

    WindTurbineUnit(SimId id) {
        super(id, 1);
        turbine = new WindTurbine(SPEC, Performance.TABLE, 50);
    }

    /** Таблица C_Q, C_T колеса Д-30 строится один раз на JVM. */
    private static final class Performance {
        static final RotorPerformance TABLE = new RotorPerformance(SPEC.geometry(), SPEC.airfoil());
    }

    @Override
    VoltageClass terminalClass(int terminal) {
        return VoltageClass.LV_0_4;
    }

    @Override
    boolean definesClass(int terminal) {
        return true;
    }

    // --- входы мира ---

    /** Ветер у оси колеса (мод берёт его из слоя environment на высоте {@link WindTurbineSpec#hubHeightM}). */
    public void setWind(double speedMs, double fromRad, double densityKgM3) {
        turbine.setWind(speedMs, fromRad, densityKgM3);
    }

    /** Ключ автоматики: пуск по ветру разрешён или штатная остановка. */
    public void setEnabled(boolean on) {
        turbine.setEnabled(on);
    }

    public boolean enabled() {
        return turbine.enabled();
    }

    /** Сбросить защёлку аварии (разнос, перегрузка) на остановленном колесе. */
    public boolean resetFault() {
        return turbine.resetFault();
    }

    // --- показания ---

    public WindTurbine turbine() {
        return turbine;
    }

    public double windMs() {
        return turbine.windMs();
    }

    public double rotorRpm() {
        return turbine.rotorRpm();
    }

    public double generatorRpm() {
        return turbine.rotorRpm() * SPEC.gearRatio();
    }

    /** Активная мощность в сеть, Вт (отрицательная — генератор берёт из сети как двигатель). */
    public double exportW() {
        return exportW;
    }

    /** Реактивная мощность, которую генератор берёт из сети на намагничивание, вар. */
    public double reactiveVar() {
        return online() ? owner.grid().motorReactiveVar(motor) : 0;
    }

    public double currentA() {
        return online() ? owner.grid().motorCurrentA(motor) : 0;
    }

    /** Скольжение генератора (отрицательное — генераторный режим); 0 — нет сети. */
    public double slip() {
        return online() ? owner.grid().motorSlip(motor) : 0;
    }

    public double windingTemperatureC() {
        return motor != null && owner.ready() ? owner.grid().motorWindingTemperatureC(motor) : carried != null ? carried[1] : 20;
    }

    public boolean contactorClosed() {
        return online();
    }

    /** Чистая выработка с постройки, кВт·ч. */
    public double energyKWh() {
        return energyJ / 3.6e6;
    }

    boolean online() {
        return motor != null && owner.ready() && owner.grid().motorConnected(motor);
    }

    double syncOmegaGenerator() {
        return SPEC.syncOmegaGenerator(owner != null ? owner.config().nominalFrequencyHz() : 50);
    }

    /** Момент нагрузки асинхронной машины, о.е. на P_ном/ω_с: минус момент колеса на валу генератора. */
    double loadTorquePu() {
        double ws = syncOmegaGenerator();
        double w = (motor != null ? owner.grid().motorSpeedPu(motor) : turbine.omegaRotor() * SPEC.gearRatio() / ws) * ws;
        double shaft = turbine.shaftTorqueNm(w / SPEC.gearRatio()) / SPEC.gearRatio();
        return -shaft / (SPEC.ratedPowerW() / ws);
    }

    // --- сборка ---

    InductionMotorSpec generatorSpec(double frequencyHz) {
        return new InductionMotorSpec(SPEC.ratedPowerW(), VoltageClass.LV_0_4.volts, GEN_TMAX, GEN_SK, GEN_ISTART,
                SPEC.inertiaAtGeneratorKgM2(), SPEC.syncRpm(frequencyHz));
    }

    @Override
    void build(GridSubsystem.Build b) {
        turbine.setFrequencyHz(b.cfg.nominalFrequencyHz());
        if (node(0) == NO_NODE) return;
        motor = b.grid.addMotor(generatorSpec(b.cfg.nominalFrequencyHz()), b.bus(node(0)));
        b.motor(motor, this);
        double[] s = carried != null ? carried.clone() : new double[PowerGrid.MOTOR_STATE];
        if (carried == null) s[1] = 20;
        s[0] = turbine.omegaRotor() * SPEC.gearRatio() / SPEC.syncOmegaGenerator(b.cfg.nominalFrequencyHz());
        s[3] = appliedContactor ? 1 : 0;
        b.grid.importMotor(motor, s);
    }

    @Override
    void capture(PowerGrid g) {
        if (motor == null) return;
        if (carried == null) carried = new double[PowerGrid.MOTOR_STATE];
        g.exportMotor(motor, carried);
        turbine.setOmegaRotor(carried[0] * syncOmegaGenerator() / SPEC.gearRatio());
        appliedContactor = carried[3] != 0;
    }

    @Override
    void unbind() {
        motor = null;
    }

    /** Перед тиком сети: автоматика по ветру и по мощности прошлого тика, команда контактору. */
    void beforeTick(PowerGrid g, double dt) {
        turbine.setFrequencyHz(owner.config().nominalFrequencyHz());
        if (motor == null) {
            exportW = 0;
            turbine.control(dt, 0, false);
            turbine.stepFree(dt);
            return;
        }
        double ws = syncOmegaGenerator();
        turbine.setOmegaRotor(g.motorSpeedPu(motor) * ws / SPEC.gearRatio());
        boolean connected = g.motorConnected(motor);
        exportW = connected ? -g.motorPowerW(motor) : 0;
        energyJ += exportW * dt;
        double v = owner.voltageV(node(0)) / VoltageClass.LV_0_4.volts;
        boolean tripped = appliedContactor && !connected; // контактор отключила защита
        boolean live = v > LIVE_VOLTAGE && !tripped && g.motorState(motor) != MotorState.FAULTED;
        turbine.control(dt, exportW, live);
        boolean cmd = turbine.contactorCommand();
        if (tripped) appliedContactor = false;
        if (cmd != connected) g.setMotorConnected(motor, cmd);
        appliedContactor = cmd;
    }

    // --- сохранение ---

    @Override
    void save(StateWriter out) {
        saveCommon(out);
        out.beginObject("turbine");
        turbine.save(out);
        out.endObject();
        out.putBoolean("contactor", appliedContactor);
        out.putDouble("energy", energyJ);
        if (carried != null) out.putDoubles("motor", carried, carried.length);
    }

    @Override
    void load(StateReader in) {
        loadCommon(in);
        if (in.has("turbine")) {
            in.beginObject("turbine");
            turbine.load(in);
            in.endObject();
        }
        appliedContactor = in.has("contactor") && in.getBoolean("contactor");
        energyJ = in.optDouble("energy", 0);
        carried = in.has("motor") ? in.getDoubles("motor") : null;
        if (carried != null && carried.length != PowerGrid.MOTOR_STATE) carried = null;
    }
}
