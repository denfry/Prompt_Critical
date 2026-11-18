package dev.denfry.promptcritical.sim.grid;

import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.thermal.ElectrodeBoiler;

/**
 * {@code electrode_boiler}: электродный котёл 6,3 кВ ({@link ElectrodeBoiler}) — нагрузка {@link LoadKind#HEATER}
 * (постоянное сопротивление, P ∝ U²). Уровень воды на электродах задаёт мощность при номинальном напряжении, сеть —
 * фактическую; тепло в пар мир передаёт котлу, которого касается электродный котёл.
 */
public final class ElectrodeBoilerUnit extends GridUnit {
    /** Мощность котла, Вт [BALANCE: под генераторы 0,5–1 МВт мира; КЭВ бывают от 1 до 25 МВт]. */
    public static final double RATED_W = 1e6;

    final ElectrodeBoiler boiler = new ElectrodeBoiler(RATED_W);
    LoadId load;
    boolean steamSink;

    ElectrodeBoilerUnit(SimId id) {
        super(id, 1);
    }

    @Override
    VoltageClass terminalClass(int terminal) {
        return VoltageClass.MV_6_3;
    }

    public ElectrodeBoiler boiler() {
        return boiler;
    }

    /** Котёл на коллекторе принимает пар (мир проверяет соседство и целость). */
    public void setSteamSink(boolean sink) {
        steamSink = sink;
    }

    public boolean steamSink() {
        return steamSink;
    }

    /** Мощность из сети, Вт. */
    public double powerW() {
        return load != null && owner.ready() ? owner.grid().loadPowerW(load) : 0;
    }

    /** Тепло в пар, Вт. */
    public double heatW() {
        return ElectrodeBoiler.heatW(powerW());
    }

    public double frequencyHz() {
        return node(0) == NO_NODE || owner == null || !owner.ready() ? 0 : owner.frequencyHz(node(0));
    }

    @Override
    void build(GridSubsystem.Build b) {
        if (node(0) == NO_NODE) return;
        load = b.grid.addLoad(LoadModel.typical(LoadKind.HEATER, boiler.powerAtRatedVoltageW()), b.bus(node(0)));
        b.grid.setLoadConnected(load, true);
    }

    @Override
    void unbind() {
        load = null;
    }

    void beforeTick(PowerGrid g, double dt) {
        double v = owner.voltageV(node(0)) / VoltageClass.MV_6_3.volts;
        double f = load != null && v > 0.7 ? frequencyHz() : 0;
        boiler.step(dt, f, owner.config().nominalFrequencyHz(), steamSink);
        if (load != null) g.setLoadPowerW(load, boiler.powerAtRatedVoltageW());
    }

    @Override
    void save(StateWriter out) {
        saveCommon(out);
        out.beginObject("boiler");
        boiler.save(out);
        out.endObject();
    }

    @Override
    void load(StateReader in) {
        loadCommon(in);
        if (in.has("boiler")) {
            in.beginObject("boiler");
            boiler.load(in);
            in.endObject();
        }
    }
}
