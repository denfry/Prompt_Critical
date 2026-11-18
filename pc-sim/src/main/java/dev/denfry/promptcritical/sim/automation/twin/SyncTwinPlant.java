package dev.denfry.promptcritical.sim.automation.twin;

import dev.denfry.promptcritical.sim.grid.Branch;
import dev.denfry.promptcritical.sim.grid.BranchId;
import dev.denfry.promptcritical.sim.grid.BreakerSpec;
import dev.denfry.promptcritical.sim.grid.BreakerState;
import dev.denfry.promptcritical.sim.grid.BusId;
import dev.denfry.promptcritical.sim.grid.ExciterSpec;
import dev.denfry.promptcritical.sim.grid.GridConfig;
import dev.denfry.promptcritical.sim.grid.GridEventSink;
import dev.denfry.promptcritical.sim.grid.GridInputs;
import dev.denfry.promptcritical.sim.grid.GridOutputs;
import dev.denfry.promptcritical.sim.grid.MachineId;
import dev.denfry.promptcritical.sim.grid.MachineParams;
import dev.denfry.promptcritical.sim.grid.PowerGrid;
import dev.denfry.promptcritical.sim.grid.PrimeMover;
import dev.denfry.promptcritical.sim.grid.SyncClosing;
import dev.denfry.promptcritical.sim.grid.SyncResult;
import dev.denfry.promptcritical.sim.grid.SyncScope;
import dev.denfry.promptcritical.sim.grid.SynchronousMachine;
import dev.denfry.promptcritical.sim.grid.TapChanger;
import dev.denfry.promptcritical.sim.grid.VoltageClass;
import java.util.List;

/**
 * Учебная установка «энергоблок и сеть» для урока {@code out_of_phase_sync} (11 §11.3.4): тот же {@link PowerGrid}, что
 * в мире. Сеть 6,3 кВ — шины бесконечной мощности; {@code shaft_generator} 1 МВ·А 0,4 кВ на валу Create через
 * трансформатор 1 МВ·А (u_k 5,5 %) и масляный выключатель (07 §7.3.5, §7.4.7), муфта со срезным штифтом.
 *
 * <p>Механизмы: {@code breaker} — команда включить (1) или отключить (0) выключатель; {@code speeder} — задатчик
 * оборотов: мощность на валу {@value #SPEEDER_W} Вт на единицу, одиночный импульс ±1 меняет скольжение примерно на
 * 0,005 Гц. Сигналы — показания синхроноскопа: скольжение, угол, разность напряжений; после включения — момент
 * толчка и повреждение вала.
 */
public final class SyncTwinPlant implements TwinPlant {
    public static final String ID = "generator_sync";
    public static final double SPEEDER_W = 2_500;
    public static final String SLIP = "slip_hz", ANGLE = "angle_deg", DV = "dv_pu", BREAKER_CLOSED = "breaker_closed",
            TORQUE = "torque_pu", SHAFT_DAMAGE = "shaft_damage", BREAKER = "breaker", SPEEDER = "speeder";
    public static final List<String> SIGNALS = List.of(SLIP, ANGLE, DV, BREAKER_CLOSED, TORQUE, SHAFT_DAMAGE),
            ACTUATORS = List.of(BREAKER, SPEEDER);
    private static final double[] BREAKER_RANGE = {0, 1}, SPEEDER_RANGE = {-10, 10};

    private final PowerGrid g;
    private final BranchId cb;
    private final MachineId id;
    private final GridOutputs out = new GridOutputs();
    private final GridEventSink sink = new GridEventSink() {
        @Override
        public void syncClosed(SyncClosing c) {
            lastClosing = c;
        }
    };
    private final GridInputs in = new GridInputs() {
        @Override
        public double createShaftPowerW(int machine) {
            return machine == id.index() ? speeder * SPEEDER_W : Double.NaN;
        }
    };
    private double breaker, speeder;
    private SyncClosing lastClosing;
    private String failure;

    /** Генератор на холостом ходу со скольжением ≈ {@code initialSlipHz} относительно сети, выключатель отключён. */
    public SyncTwinPlant(double initialSlipHz) {
        g = new PowerGrid(GridConfig.defaults());
        BusId net = g.addBus(VoltageClass.MV_6_3, "net");
        BusId gen = g.addBus(VoltageClass.LV_0_4, "gen");
        g.addMachine(new SynchronousMachine(new MachineParams(1e9, 6300, 1, 1e-4, 1e-4, 1e-4, 1e-4, 1e9, 1e9, 1e7, 0, 0, 1),
                ExciterSpec.manual(), new PrimeMover.CreateShaft(1, 1e9)), net);
        cb = g.addBranch(new Branch.Transformer(net, gen, 1e6, 0.055, 10e3, 2e3, 0.01, TapChanger.NONE, null));
        g.attachBreaker(cb, BreakerSpec.oil());
        g.setBreaker(cb, false);
        SynchronousMachine m = new SynchronousMachine(MachineParams.shaftGenerator(1e6, 400), ExciterSpec.rotating(),
                new PrimeMover.CreateShaft(20, 0.8e6));
        m.setVoltageReference(1.02);
        m.setShearPinCoupling(true);
        id = g.addMachine(m, gen);
        g.initializePowerFlow();
        // разгон до заданного скольжения импульсами задатчика, затем установившийся холостой ход
        for (int k = 0; k < 400 && Math.abs(g.syncScope(cb).slipHz() - initialSlipHz) > 0.01; k++) {
            speeder = Math.signum(initialSlipHz - g.syncScope(cb).slipHz()) * SPEEDER_RANGE[1];
            stepGrid();
        }
        speeder = 0;
        for (int k = 0; k < 40; k++) stepGrid();
    }

    private void stepGrid() {
        g.stepFast(DT, in, out);
        g.drainEvents(sink);
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
            case SLIP -> scope().slipHz();
            case ANGLE -> Math.toDegrees(Math.IEEEremainder(scope().angleRad(), 2 * Math.PI));
            case DV -> scope().dvPu();
            case BREAKER_CLOSED -> g.breakerState(cb) == BreakerState.CLOSED ? 1 : 0;
            case TORQUE -> lastClosing == null ? 0 : lastClosing.torquePu();
            case SHAFT_DAMAGE -> lastClosing == null ? 0 : lastClosing.shaftDamage();
            default -> Double.NaN;
        };
    }

    private SyncScope scope() {
        return g.syncScope(cb);
    }

    @Override
    public List<String> actuators() {
        return ACTUATORS;
    }

    @Override
    public double[] range(String actuator) {
        return switch (actuator) {
            case BREAKER -> BREAKER_RANGE;
            case SPEEDER -> SPEEDER_RANGE;
            default -> throw new IllegalArgumentException("no actuator " + actuator);
        };
    }

    @Override
    public boolean discrete(String actuator) {
        return BREAKER.equals(actuator);
    }

    @Override
    public double actuator(String actuator) {
        return switch (actuator) {
            case BREAKER -> breaker;
            case SPEEDER -> speeder;
            default -> Double.NaN;
        };
    }

    @Override
    public void actuate(String actuator, double value) {
        switch (actuator) {
            case BREAKER -> {
                double v = value >= 0.5 ? 1 : 0;
                if (v != breaker) g.operateBreaker(cb, v > 0);
                breaker = v;
            }
            case SPEEDER -> speeder = TwinPlant.clamp(value, SPEEDER_RANGE);
            default -> throw new IllegalArgumentException("no actuator " + actuator);
        }
    }

    @Override
    public void step() {
        stepGrid();
        if (failure == null && lastClosing != null && lastClosing.result() != SyncResult.OK)
            failure = "out_of_phase_" + lastClosing.result().name().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public String failure() {
        return failure;
    }
}
