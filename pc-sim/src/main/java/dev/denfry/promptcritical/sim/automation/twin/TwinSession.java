package dev.denfry.promptcritical.sim.automation.twin;

import dev.denfry.promptcritical.sim.automation.plc.IlCompiler;
import dev.denfry.promptcritical.sim.automation.plc.IlProgram;
import dev.denfry.promptcritical.sim.automation.plc.PlcCpu;
import dev.denfry.promptcritical.sim.automation.twin.Fault.ForcedInput;
import dev.denfry.promptcritical.sim.automation.twin.Fault.IndicationCommand;
import dev.denfry.promptcritical.sim.automation.twin.Fault.PlcWatchdog;
import dev.denfry.promptcritical.sim.automation.twin.Fault.SensorFault;
import dev.denfry.promptcritical.sim.automation.twin.Fault.StuckActuator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Сеанс цифрового двойника (03d §3d.9.1): установка-копия, ПЛК двойника с программой из мира или сценария, отказы,
 * сценарий возмущений по времени, показатели контура и журнал для разбора. Время двойника — свои шаги
 * {@link TwinPlant#DT}: скорость (шагов за тик сервера, 0 — пауза) и бюджет считает владелец сеанса. Детерминизм: шум
 * датчиков — из {@link SplittableRandom} с зерном сеанса и тянется только в {@link #step()}, так что одинаковые
 * команды в те же шаги дают тот же прогон (повтор по журналу команд).
 */
public final class TwinSession implements AutoCloseable {
    /** {@code twin.max_speed}: шагов двойника за тик сервера. */
    public static final int MAX_SPEED = 10;
    /** Длина журнала разбора. */
    public static final int LOG_MAX = 64;

    private final TwinPlant plant;
    private final IoMap io;
    private final SplittableRandom rnd;
    private final Map<String, Double> command = new LinkedHashMap<>(), commanded = new LinkedHashMap<>();
    private final Map<String, Double> pulse = new LinkedHashMap<>();
    private final List<ActiveFault> faults = new ArrayList<>();
    private final List<Scenario.Timed> pending = new ArrayList<>();
    private final List<LoopMetrics> loops = new ArrayList<>();
    private final List<String> log = new ArrayList<>();
    private final boolean[] di = new boolean[IlCompiler.IX];
    private final double[] ai = new double[IlCompiler.IW];
    private PlcCpu plc;
    private long tick;
    private int speed = 1;
    private String loggedFailure;

    private static final class ActiveFault {
        final Fault fault;
        final double t0, captured;
        double noise;

        ActiveFault(Fault fault, double t0, double captured) {
            this.fault = fault;
            this.t0 = t0;
            this.captured = captured;
        }
    }

    public TwinSession(TwinPlant plant, IoMap io, long seed) {
        this.plant = plant;
        this.io = io;
        this.rnd = new SplittableRandom(seed);
        for (String a : plant.actuators()) {
            command.put(a, plant.actuator(a));
            commanded.put(a, plant.actuator(a));
        }
    }

    public TwinPlant plant() {
        return plant;
    }

    public IoMap io() {
        return io;
    }

    public long tick() {
        return tick;
    }

    public double timeS() {
        return tick * TwinPlant.DT;
    }

    public int speed() {
        return speed;
    }

    /** Шагов за тик сервера, 0 — пауза; не больше {@link #MAX_SPEED}. */
    public void setSpeed(int stepsPerServerTick) {
        speed = Math.max(0, Math.min(MAX_SPEED, stepsPerServerTick));
    }

    // ------------------------------------------------------------------ ПЛК

    /** Загрузить проект в ПЛК двойника (тот же {@link PlcCpu}, что в мире, — программа ведёт себя одинаково). */
    public IlProgram loadPlc(String source) {
        if (plc == null) plc = new PlcCpu();
        IlProgram p = plc.load(source);
        log("plc loaded: " + (p.ok() ? "ok" : p.errors().size() + " errors"));
        return p;
    }

    /** ПЛК двойника, {@code null} — без ПЛК (механизмы слушаются оператора). */
    public PlcCpu plc() {
        return plc;
    }

    private boolean plcRuns() {
        return plc != null && plc.mode() == PlcCpu.Mode.RUN;
    }

    // ------------------------------------------------------------------ оператор и инструктор

    /** Команда оператора механизму (кнопка пульта, Lua через стенд). */
    public void command(String actuator, double value) {
        if (!command.containsKey(actuator)) throw new IllegalArgumentException("no actuator " + actuator);
        command.put(actuator, TwinPlant.clamp(value, plant.range(actuator)));
    }

    /** Команда оператора: что он поставил ключом или задатчиком. */
    public double operatorCommand(String actuator) {
        Double v = command.get(actuator);
        return v == null ? Double.NaN : v;
    }

    /** Импульс на один шаг (задатчик «прибавить/убавить»). */
    public void pulse(String actuator, double value) {
        if (!command.containsKey(actuator)) throw new IllegalArgumentException("no actuator " + actuator);
        pulse.merge(actuator, value, Double::sum);
    }

    public void inject(Fault fault) {
        double captured = Double.NaN;
        switch (fault) {
            case SensorFault s -> captured = measured(s.signal());
            case StuckActuator s -> captured = plant.actuator(s.actuator());
            default -> {
            }
        }
        faults.add(new ActiveFault(fault, timeS(), captured));
        log("fault " + fault.label());
    }

    public List<Fault> faults() {
        List<Fault> out = new ArrayList<>(faults.size());
        for (ActiveFault f : faults) out.add(f.fault);
        return out;
    }

    public void clearFaults() {
        if (!faults.isEmpty()) log("faults cleared");
        faults.clear();
    }

    /** Снять форсировки входов ПЛК (оператор нашёл забытый форс). */
    public boolean clearForces() {
        boolean removed = faults.removeIf(f -> f.fault instanceof ForcedInput);
        if (removed) log("forces removed");
        return removed;
    }

    public int forceCount() {
        int n = 0;
        for (ActiveFault f : faults) if (f.fault instanceof ForcedInput) n++;
        return n;
    }

    /** Действие на момент {@code tS} двойника (сценарий возмущений). */
    public void schedule(double tS, ScenarioAction action) {
        pending.add(new Scenario.Timed(tS, action));
        pending.sort((a, b) -> Double.compare(a.tS(), b.tS()));
    }

    /** Записывать показатели контура {@code pv} к заданию {@code sp}, ход механизма {@code mv}. */
    public LoopMetrics track(String pv, double sp, String mv) {
        LoopMetrics m = new LoopMetrics(pv, sp, mv);
        loops.add(m);
        return m;
    }

    public List<LoopMetrics> loops() {
        return loops;
    }

    public List<String> log() {
        return log;
    }

    private void log(String msg) {
        if (log.size() == LOG_MAX) log.removeFirst();
        log.add(String.format(Locale.ROOT, "%7.1f s %s", timeS(), msg));
    }

    // ------------------------------------------------------------------ показания

    /** Показание прибора: истинное значение под отказами датчиков; NaN — нет показания (обрыв). */
    public double measured(String signal) {
        double v = plant.read(signal);
        for (ActiveFault f : faults) {
            switch (f.fault) {
                case IndicationCommand c when c.signal().equals(signal) -> {
                    Double cmd = commanded.get(c.actuator());
                    v = cmd == null ? v : cmd;
                }
                case SensorFault s when s.signal().equals(signal) -> v = switch (s.mode()) {
                    case FROZEN -> f.captured;
                    case DRIFT -> v + s.param() * (timeS() - f.t0);
                    case OPEN_LOOP -> Double.NaN;
                    case NOISY -> v + f.noise;
                };
                default -> {
                }
            }
        }
        return v;
    }

    /** Команда, ушедшая на механизм в последнем шаге (после ПЛК и разрешений, до заклинивания). */
    public double commanded(String actuator) {
        Double v = commanded.get(actuator);
        return v == null ? Double.NaN : v;
    }

    // ------------------------------------------------------------------ шаг

    public void step() {
        while (!pending.isEmpty() && pending.getFirst().tS() <= timeS() + 1e-9) apply(pending.removeFirst().action());
        for (ActiveFault f : faults)
            if (f.fault instanceof SensorFault s && s.mode() == Fault.SensorMode.NOISY) f.noise = s.param() * rnd.nextGaussian();
        scanPlc();
        for (String a : plant.actuators()) {
            double cmd = command.get(a) + pulse.getOrDefault(a, 0.0);
            if (plcRuns() && io.commands(a)) cmd = plcCommand(a);
            if (plc != null) {
                for (IoMap.Do d : io.dout())
                    if (d.mode() == IoMap.DoMode.PERMISSIVE && d.actuator().equals(a) && !plc.output(d.ch())) cmd = d.off();
            }
            cmd = TwinPlant.clamp(cmd, plant.range(a));
            commanded.put(a, cmd);
            plant.actuate(a, stuck(a, cmd));
        }
        pulse.clear();
        plant.step();
        tick++;
        for (LoopMetrics m : loops) m.record(TwinPlant.DT, plant.read(m.pv()), m.mv() == null ? Double.NaN : plant.actuator(m.mv()));
        String failure = plant.failure();
        if (failure != null && loggedFailure == null) {
            loggedFailure = failure;
            log("plant failure: " + failure);
        }
    }

    private void apply(ScenarioAction action) {
        switch (action) {
            case ScenarioAction.Inject i -> inject(i.fault());
            case ScenarioAction.Set s -> {
                command(s.actuator(), s.value());
                log("event " + s.actuator() + " = " + s.value());
            }
            case ScenarioAction.ClearFaults c -> clearFaults();
        }
    }

    private void scanPlc() {
        if (plc == null) return;
        Arrays.fill(di, false);
        Arrays.fill(ai, 0);
        for (IoMap.Di d : io.di()) di[d.ch()] = d.eval(measured(d.signal()));
        for (IoMap.Ai a : io.ai()) ai[a.ch()] = a.volts(measured(a.signal()));
        boolean powered = true;
        for (ActiveFault f : faults) {
            switch (f.fault) {
                case ForcedInput fi when fi.analog() -> ai[fi.channel()] = fi.value();
                case ForcedInput fi -> di[fi.channel()] = fi.value() >= 0.5;
                case PlcWatchdog w -> powered = false;
                default -> {
                }
            }
        }
        plc.scan(TwinPlant.DT, powered, di, ai);
    }

    private double plcCommand(String actuator) {
        double sum = 0;
        for (IoMap.Do d : io.dout())
            if (d.mode() == IoMap.DoMode.COMMAND && d.actuator().equals(actuator)) sum += plc.output(d.ch()) ? d.on() : d.off();
        for (IoMap.Ao a : io.ao()) if (a.actuator().equals(actuator)) sum = a.position(plc.analogOutput(a.ch()));
        return sum;
    }

    private double stuck(String actuator, double cmd) {
        double[] r = plant.range(actuator);
        for (ActiveFault f : faults) {
            if (f.fault instanceof StuckActuator s && s.actuator().equals(actuator)) {
                return switch (s.mode()) {
                    case STUCK_AT -> Double.isNaN(s.at()) ? f.captured : s.at();
                    case STUCK_OPEN -> r[1];
                    case STUCK_CLOSED, TRIP -> r[0];
                };
            }
        }
        return cmd;
    }

    @Override
    public void close() {
        plant.close();
    }
}
