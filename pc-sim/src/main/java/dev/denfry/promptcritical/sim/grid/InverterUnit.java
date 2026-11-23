package dev.denfry.promptcritical.sim.grid;

import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Солнечный или батарейный инвертор в сети ({@link InverterSource}): одна нагрузка {@link LoadKind#FE_IMPORT} с
 * мощностью −P. Шина 0,4 кВ до {@value FeInterfaceUnit#LV_MAX_W} Вт, выше — 6,3 кВ. Сколько отдать и сколько может
 * источник за инвертором (панели, батарея), задаёт мир каждый тик.
 */
public final class InverterUnit extends GridUnit {
    InverterSource source;
    LoadId load;
    private double demandW, maxOutW, maxInW;
    private double savedRated = Double.NaN;
    private InverterSource.Carry pending;

    InverterUnit(SimId id) {
        super(id, 1);
    }

    @Override
    VoltageClass terminalClass(int terminal) {
        double rated = source != null ? source.ratedW() : savedRated;
        return !Double.isNaN(rated) && rated <= FeInterfaceUnit.LV_MAX_W ? VoltageClass.LV_0_4 : VoltageClass.MV_6_3;
    }

    /** Номинал инвертора от мира, Вт; при смене номинала инвертор начинает с отключённого состояния. */
    public void configure(double ratedW, double efficiency) {
        if (source != null && source.ratedW() == ratedW && source.efficiency() == efficiency) return;
        boolean lvBefore = terminalClass(0) == VoltageClass.LV_0_4;
        InverterSource s = new InverterSource(ratedW, efficiency);
        if (source == null && pending != null) s.restore(pending);
        pending = null;
        source = s;
        if (lvBefore != (terminalClass(0) == VoltageClass.LV_0_4) || Double.isNaN(savedRated)) changed();
        savedRated = ratedW;
    }

    /** Нагрузка в собранной сети есть. */
    public boolean bound() {
        return load != null;
    }

    public InverterSource source() {
        return source;
    }

    /** Задание и возможности источника на стороне переменного тока, Вт. */
    public void setDispatch(double demandW, double maxOutW, double maxInW) {
        this.demandW = Double.isFinite(demandW) ? demandW : 0;
        this.maxOutW = Double.isFinite(maxOutW) ? Math.max(0, maxOutW) : 0;
        this.maxInW = Double.isFinite(maxInW) ? Math.max(0, maxInW) : 0;
    }

    /** Мощность в сеть, Вт (минус — из сети). */
    public double powerW() {
        return source == null ? 0 : source.powerW();
    }

    public double voltagePu() {
        return node(0) == NO_NODE || owner == null || !owner.ready() ? 0 : owner.voltageV(node(0)) / terminalClass(0).volts;
    }

    public double frequencyHz() {
        return node(0) == NO_NODE || owner == null || !owner.ready() ? 0 : owner.frequencyHz(node(0));
    }

    @Override
    void build(GridSubsystem.Build b) {
        if (node(0) == NO_NODE) return;
        load = b.grid.addLoad(LoadModel.typical(LoadKind.FE_IMPORT, -powerW()), b.bus(node(0)));
        b.grid.setLoadConnected(load, true);
    }

    @Override
    void unbind() {
        load = null;
    }

    void beforeTick(PowerGrid g, double dt) {
        if (load == null) return;
        if (source == null) {
            g.setLoadPowerW(load, 0);
            return;
        }
        // первый тик после пересборки сети: шины ещё не посчитаны, нули — не пропажа сети; держим прежнюю мощность
        if (!owner.solved()) {
            g.setLoadPowerW(load, -source.powerW());
            return;
        }
        g.setLoadPowerW(load, -source.step(dt, voltagePu(), frequencyHz(), demandW, maxOutW, maxInW));
    }

    @Override
    void save(StateWriter out) {
        saveCommon(out);
        double rated = source != null ? source.ratedW() : savedRated;
        if (!Double.isNaN(rated)) out.putDouble("rated", rated);
        out.putDouble("demand", demandW);
        out.putDouble("max_out", maxOutW);
        out.putDouble("max_in", maxInW);
        if (source != null) {
            out.beginObject("inverter");
            source.save(out);
            out.endObject();
        } else if (pending != null) {
            out.beginObject("inverter");
            pending.write(out);
            out.endObject();
        }
    }

    @Override
    void load(StateReader in) {
        loadCommon(in);
        savedRated = in.optDouble("rated", Double.NaN);
        setDispatch(in.optDouble("demand", 0), in.optDouble("max_out", 0), in.optDouble("max_in", 0));
        source = null;
        pending = null;
        if (in.has("inverter")) {
            in.beginObject("inverter");
            pending = InverterSource.Carry.read(in);
            in.endObject();
        }
    }
}
