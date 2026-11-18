package dev.denfry.promptcritical.sim.automation.twin;

import dev.denfry.promptcritical.sim.gasprocessing.CentrifugalCompressor;
import java.util.List;

/**
 * Стенд «компрессорная станция» для задачи наладчика {@code compressor_startup_no_surge} (03d §3d.11.6 № 2): тот же
 * {@link CentrifugalCompressor}, что в мире, — газ из коллектора 5 бар в газопровод 8 бар, рецикл (антипомпажный
 * клапан) и линейный клапан. Привод — электродвигатель с плавным пуском: ротор разгоняется до номинала за
 * {@value #SPIN_UP_S} с и выбегает так же [BALANCE].
 *
 * <p>Механизмы: {@code drive} — пуск (1) и останов (0), {@code recycle} и {@code line_valve} — 0..1. Сигналы — частота,
 * расходы, давления, перепад на диафрагме, запас S_s, счётчик циклов помпажа, вибрация, повреждение, накопленные
 * объёмы рецикла и поставки и момент выхода на режим ({@code on_stream_s} — первый раз, когда в газопровод идёт
 * {@value #ON_STREAM} расчётного расхода; до этого — NaN). Разрушение от помпажа — авария стенда.
 */
public final class CompressorTwinPlant implements TwinPlant {
    public static final String ID = "compressor";
    public static final double SPIN_UP_S = 20, ON_STREAM = 0.9;
    public static final String SPEED = "speed", FLOW = "flow_m3s", DP = "dp_orifice_kpa", PS = "p_suction_bar",
            PD = "p_discharge_bar", LINE_FLOW = "line_flow_m3s", RECYCLE_FLOW = "recycle_flow_m3s", MARGIN = "surge_ratio",
            CYCLES = "surge_cycles", VIBRATION = "vibration_mm_s", DAMAGE = "damage", RECYCLED = "recycled_m3",
            DELIVERED = "delivered_m3", ON_STREAM_S = "on_stream_s", DRIVE = "drive", RECYCLE = "recycle", LINE = "line_valve";
    public static final List<String> SIGNALS = List.of(SPEED, FLOW, DP, PS, PD, LINE_FLOW, RECYCLE_FLOW, MARGIN, CYCLES,
            VIBRATION, DAMAGE, RECYCLED, DELIVERED, ON_STREAM_S), ACTUATORS = List.of(DRIVE, RECYCLE, LINE);
    private static final double[] UNIT = {0, 1};

    private final CentrifugalCompressor c = new CentrifugalCompressor();
    private double drive, recycle = 1, line, speed, recycled, delivered, t, onStream = Double.NaN;
    private String failure;

    /** Компрессор стоит, рецикл открыт, линейный клапан закрыт. */
    public CompressorTwinPlant() {
    }

    public CentrifugalCompressor compressor() {
        return c;
    }

    @Override
    public String template() {
        return ID;
    }

    @Override
    public List<String> signals() {
        return SIGNALS;
    }

    @Override
    public double read(String signal) {
        return switch (signal) {
            case SPEED -> speed;
            case FLOW -> c.flowM3s();
            case DP -> c.orificeDpKpa();
            case PS -> c.suctionPa() / 1e5;
            case PD -> c.dischargePa() / 1e5;
            case LINE_FLOW -> c.lineFlowM3s();
            case RECYCLE_FLOW -> c.recycleFlowM3s();
            case MARGIN -> c.surgeRatio();
            case CYCLES -> c.surgeCycles();
            case VIBRATION -> c.vibrationMmS();
            case DAMAGE -> c.damage();
            case RECYCLED -> recycled;
            case DELIVERED -> delivered;
            case ON_STREAM_S -> onStream;
            default -> Double.NaN;
        };
    }

    @Override
    public List<String> actuators() {
        return ACTUATORS;
    }

    @Override
    public double[] range(String actuator) {
        return UNIT;
    }

    @Override
    public boolean discrete(String actuator) {
        return DRIVE.equals(actuator);
    }

    @Override
    public double actuator(String actuator) {
        return switch (actuator) {
            case DRIVE -> drive;
            case RECYCLE -> c.recyclePosition();
            case LINE -> c.linePosition();
            default -> Double.NaN;
        };
    }

    @Override
    public void actuate(String actuator, double value) {
        switch (actuator) {
            case DRIVE -> drive = value >= 0.5 ? 1 : 0;
            case RECYCLE -> recycle = TwinPlant.clamp(value, UNIT);
            case LINE -> line = TwinPlant.clamp(value, UNIT);
            default -> throw new IllegalArgumentException("no actuator " + actuator);
        }
    }

    @Override
    public void step() {
        double ramp = DT / SPIN_UP_S;
        speed += Math.clamp(drive - speed, -ramp, ramp);
        c.step(DT, speed, recycle, line);
        t += DT;
        recycled += c.recycleFlowM3s() * DT;
        delivered += c.lineFlowM3s() * DT;
        if (Double.isNaN(onStream) && c.lineFlowM3s() >= ON_STREAM * CentrifugalCompressor.Q_DESIGN) onStream = t;
        if (failure == null && c.destroyed()) failure = "compressor_destroyed";
    }

    @Override
    public String failure() {
        return failure;
    }
}
