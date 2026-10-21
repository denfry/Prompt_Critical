package dev.denfry.promptcritical.sim.grid;

import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * {@code electric_motor} (07 §7.6.3): асинхронный двигатель — источник вращения Create. Исполнение 0,4 кВ 100 кВт
 * или 6 кВ 1 МВт; синхронная скорость 1500 об/мин, выходной редуктор 16…256 об/мин. Нагрузка — доля стресса сети
 * Create, которую мод пересчитывает в момент T_load = P_Create/(P_ном·ω); ёмкость Create при напряжении ниже
 * 0,9 о.е. падает как v².
 */
public final class MotorUnit extends GridUnit {
    public static final double LV_RATED_W = 100e3, MV_RATED_W = 1e6;
    public static final int[] GEARS = {16, 32, 64, 128, 256};

    boolean mediumVoltage;
    int gear = 2;
    boolean contactor;
    double loadPowerW;
    MotorId motor;
    double[] carried;

    MotorUnit(SimId id) {
        super(id, 1);
    }

    @Override
    VoltageClass terminalClass(int terminal) {
        return mediumVoltage ? VoltageClass.MV_6_3 : VoltageClass.LV_0_4;
    }

    public boolean mediumVoltage() {
        return mediumVoltage;
    }

    /** Исполнение 6 кВ 1 МВт или 0,4 кВ 100 кВт (блок {@code voltage=mv/lv}). */
    public void setMediumVoltage(boolean mv) {
        if (mv == mediumVoltage) return;
        mediumVoltage = mv;
        carried = null;
        changed();
    }

    public double ratedPowerW() {
        return mediumVoltage ? MV_RATED_W : LV_RATED_W;
    }

    public int gearRpm() {
        return GEARS[gear];
    }

    public void cycleGear() {
        gear = (gear + 1) % GEARS.length;
    }

    public boolean contactorClosed() {
        if (motor != null && owner.ready()) return owner.grid().motorConnected(motor);
        return contactor;
    }

    /** Пускатель: «пуск» — прямой пуск, «стоп» — выбег. */
    public void setContactor(boolean on) {
        contactor = on;
        if (motor != null && owner.ready()) owner.grid().setMotorConnected(motor, on);
        else if (carried != null) carried[3] = on ? 1 : 0;
    }

    /** Мощность, которую забирает сеть Create, Вт (порт пишет каждый тик). */
    public void setLoadPowerW(double watts) {
        loadPowerW = Math.max(0, watts);
    }

    public double speedPu() {
        return motor != null && owner.ready() ? owner.grid().motorSpeedPu(motor) : carried != null ? carried[0] : 0;
    }

    public MotorState state() {
        return motor != null && owner.ready() ? owner.grid().motorState(motor) : MotorState.OFF;
    }

    public double currentA() {
        return motor != null && owner.ready() ? owner.grid().motorCurrentA(motor) : 0;
    }

    public double powerW() {
        return motor != null && owner.ready() ? owner.grid().motorPowerW(motor) : 0;
    }

    public double reactiveVar() {
        return motor != null && owner.ready() ? owner.grid().motorReactiveVar(motor) : 0;
    }

    public double windingTemperatureC() {
        return motor != null && owner.ready() ? owner.grid().motorWindingTemperatureC(motor) : carried != null ? carried[1] : 20;
    }

    /** Мощность, которую двигатель может отдать в Create при нынешнем напряжении, Вт (C_SU·W_PER_SU). */
    public double capacityW() {
        if (motor == null || !owner.ready() || state() == MotorState.OFF || state() == MotorState.FAULTED) return 0;
        double v = owner.voltageV(node(0)) / (mediumVoltage ? 6300 : 400);
        return ratedPowerW() * Math.min(1, v * v / 0.81);
    }

    double loadTorquePu() {
        double w = speedPu();
        return w < 0.05 ? 0 : loadPowerW / (ratedPowerW() * w);
    }

    @Override
    void build(GridSubsystem.Build b) {
        if (node(0) == NO_NODE) return;
        double vr = mediumVoltage ? 6300 : 400;
        motor = b.grid.addMotor(InductionMotorSpec.standard(ratedPowerW(), vr), b.bus(node(0)));
        b.motor(motor, this);
        double[] s = carried;
        if (s == null) {
            s = new double[PowerGrid.MOTOR_STATE];
            s[1] = 20;
            s[3] = contactor ? 1 : 0;
        }
        b.grid.importMotor(motor, s);
    }

    @Override
    void capture(PowerGrid g) {
        if (motor == null) return;
        if (carried == null) carried = new double[PowerGrid.MOTOR_STATE];
        g.exportMotor(motor, carried);
        contactor = carried[3] != 0;
    }

    @Override
    void unbind() {
        motor = null;
    }

    @Override
    void save(StateWriter out) {
        saveCommon(out);
        out.putBoolean("mv", mediumVoltage);
        out.putInt("gear", gear);
        out.putBoolean("contactor", contactor);
        if (carried != null) out.putDoubles("motor", carried, carried.length);
    }

    @Override
    void load(StateReader in) {
        loadCommon(in);
        mediumVoltage = in.getBoolean("mv");
        gear = Math.floorMod(in.getInt("gear"), GEARS.length);
        contactor = in.getBoolean("contactor");
        carried = in.has("motor") ? in.getDoubles("motor") : null;
        if (carried != null && carried.length != PowerGrid.MOTOR_STATE) carried = null;
    }
}
