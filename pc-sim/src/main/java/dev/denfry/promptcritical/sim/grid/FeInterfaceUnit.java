package dev.denfry.promptcritical.sim.grid;

import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * {@code grid_fe_interface} в сети (07 §7.7.1): одна нагрузка постоянной мощности {@link LoadKind#FE_IMPORT}, как
 * обратимый агрегат ГАЭС. Импорт — положительная мощность, экспорт — отрицательная: источник без инерции, частоту
 * острова он не держит. Шина — 0,4 кВ у малого интерфейса и 6,3 кВ у остальных; номинал задаёт мир.
 */
public final class FeInterfaceUnit extends GridUnit {
    /** До этой мощности интерфейс включается в сеть 0,4 кВ, Вт. */
    public static final double LV_MAX_W = 200e3;

    FeInterface iface;
    LoadId load;
    /** Номинал и состояние до первого паспорта после загрузки. */
    private double savedRated = Double.NaN;
    private StateCarry carry;

    FeInterfaceUnit(SimId id) {
        super(id, 1);
    }

    private record StateCarry(String mode, double set, double buffer, boolean on, double inS, double onS) {}

    @Override
    VoltageClass terminalClass(int terminal) {
        double rated = iface != null ? iface.spec().ratedW() : savedRated;
        return !Double.isNaN(rated) && rated <= LV_MAX_W ? VoltageClass.LV_0_4 : VoltageClass.MV_6_3;
    }

    /** Паспорт от мира и ключей конфигурации. */
    public void configure(FeInterface.Spec spec) {
        if (iface == null) {
            iface = new FeInterface(spec);
            if (carry != null) {
                try {
                    iface.setMode(FeInterface.Mode.valueOf(carry.mode));
                } catch (IllegalArgumentException e) {
                    iface.setMode(FeInterface.Mode.AUTO);
                }
                iface.setSetpointW(carry.set);
                iface.restore(carry.buffer, carry.on, carry.inS, carry.onS);
                carry = null;
            }
            changed();
        } else if (!iface.spec().equals(spec)) {
            boolean lvBefore = terminalClass(0) == VoltageClass.LV_0_4;
            iface.respec(spec);
            if (lvBefore != (terminalClass(0) == VoltageClass.LV_0_4)) changed();
        }
    }

    public FeInterface iface() {
        return iface;
    }

    /** Мощность из сети, Вт (минус — в сеть). */
    public double powerW() {
        return load != null && owner.ready() ? owner.grid().loadPowerW(load) : 0;
    }

    public double frequencyHz() {
        return node(0) == NO_NODE || owner == null || !owner.ready() ? 0 : owner.frequencyHz(node(0));
    }

    public double voltagePu() {
        return node(0) == NO_NODE || owner == null || !owner.ready() ? 0 : owner.voltageV(node(0)) / terminalClass(0).volts;
    }

    @Override
    void build(GridSubsystem.Build b) {
        if (node(0) == NO_NODE) return;
        load = b.grid.addLoad(LoadModel.typical(LoadKind.FE_IMPORT, iface != null ? iface.gridW() : 0), b.bus(node(0)));
        b.grid.setLoadConnected(load, true);
    }

    @Override
    void unbind() {
        load = null;
    }

    /** Перед тиком сети: окно по своей шине, обмен буфера с сетью — в нагрузку. */
    void beforeTick(PowerGrid g, double dt) {
        if (load == null) return;
        if (iface == null) {
            g.setLoadPowerW(load, 0);
            return;
        }
        // первый тик после пересборки сети: шины ещё не посчитаны, нули — не пропажа сети
        if (!owner.solved()) {
            g.setLoadPowerW(load, iface.gridW());
            return;
        }
        g.setLoadPowerW(load, iface.step(dt, voltagePu(), frequencyHz()));
    }

    @Override
    void save(StateWriter out) {
        saveCommon(out);
        if (iface != null) {
            out.putDouble("rated", iface.spec().ratedW());
            out.beginObject("iface");
            iface.save(out);
            out.endObject();
        } else {
            // паспорта от мира ещё нет: состояние из сохранения переносится как было, чтобы сохранение не теряло его
            if (!Double.isNaN(savedRated)) out.putDouble("rated", savedRated);
            if (carry != null) {
                out.beginObject("iface");
                out.putString("mode", carry.mode);
                out.putDouble("set", carry.set);
                out.putDouble("buffer", carry.buffer);
                out.putBoolean("on", carry.on);
                out.putDouble("in_s", carry.inS);
                out.putDouble("on_s", carry.onS);
                out.endObject();
            }
        }
    }

    @Override
    void load(StateReader in) {
        loadCommon(in);
        savedRated = in.optDouble("rated", Double.NaN);
        if (in.has("iface")) {
            in.beginObject("iface");
            carry = new StateCarry(in.optString("mode", FeInterface.Mode.AUTO.name()), in.optDouble("set", 0),
                    in.optDouble("buffer", 0), in.has("on") && in.getBoolean("on"), in.optDouble("in_s", 0),
                    in.optDouble("on_s", 0));
            in.endObject();
        }
    }
}
