package dev.denfry.promptcritical.sim.grid;

import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.hydraulics.PumpedStorage;

/**
 * {@code pumped_storage_unit}: обратимый агрегат ГАЭС ({@link PumpedStorage}) на шине 6,3 кВ. В сеть он входит одной
 * нагрузкой постоянной мощности {@link LoadKind#FE_IMPORT}: в насосном режиме мощность положительная (двигатель
 * берёт из сети), в турбинном — отрицательная (генератор отдаёт). Инерции и качаний ротора у такой модели нет —
 * агрегат держит заданную автоматикой мощность, как обратимый агрегат с регулятором мощности; частоту сети он
 * видит на своей шине и по ней выбирает режим ({@link PumpedStorage.Mode#AUTO}). Паспорт (напор, бассейн, водовод)
 * задаёт мир; при смене паспорта уровень верхнего бассейна и счётчики переносятся.
 */
public final class PumpedStorageUnit extends GridUnit {
    PumpedStorage plant;
    LoadId load;
    /** Уровень и режим до первого паспорта после загрузки или пока бассейна нет. */
    private double savedUpper = Double.NaN;
    private PumpedStorage.Mode savedMode;

    PumpedStorageUnit(SimId id) {
        super(id, 1);
    }

    @Override
    VoltageClass terminalClass(int terminal) {
        return VoltageClass.MV_6_3;
    }

    /** Паспорт станции от мира; {@code null} — нет верхнего бассейна (агрегат стоит). */
    public void configure(PumpedStorage.Spec spec) {
        if (spec == null) {
            if (plant != null) {
                savedUpper = plant.upperLevelM();
                savedMode = plant.mode();
            }
            plant = null;
            return;
        }
        if (plant != null && plant.spec().equals(spec)) return;
        PumpedStorage next = new PumpedStorage(spec);
        if (plant != null) {
            next.setMode(plant.mode());
            next.setTurbineSetpoint(plant.turbineSetpoint());
            next.setUpperLevelM(plant.upperLevelM());
        } else {
            if (!Double.isNaN(savedUpper)) next.setUpperLevelM(savedUpper);
            if (savedMode != null) next.setMode(savedMode);
        }
        plant = next;
    }

    public PumpedStorage plant() {
        return plant;
    }

    /** Мощность из сети, Вт (отрицательная — в сеть). */
    public double powerW() {
        return load != null && owner.ready() ? owner.grid().loadPowerW(load) : 0;
    }

    public double frequencyHz() {
        return node(0) == NO_NODE || owner == null || !owner.ready() ? 0 : owner.frequencyHz(node(0));
    }

    @Override
    void build(GridSubsystem.Build b) {
        if (node(0) == NO_NODE) return;
        load = b.grid.addLoad(LoadModel.typical(LoadKind.FE_IMPORT, plant != null ? plant.powerW() : 0), b.bus(node(0)));
        b.grid.setLoadConnected(load, true);
    }

    @Override
    void unbind() {
        load = null;
    }

    /** Перед тиком сети: автоматика по частоте шины, мощность агрегата — в нагрузку. */
    void beforeTick(PowerGrid g, double dt) {
        if (plant == null) {
            if (load != null) g.setLoadPowerW(load, 0);
            return;
        }
        double v = owner.voltageV(node(0)) / VoltageClass.MV_6_3.volts;
        double f = load != null && v > 0.7 ? frequencyHz() : 0;
        plant.step(dt, f, owner.config().nominalFrequencyHz());
        if (load != null) g.setLoadPowerW(load, plant.powerW());
    }

    @Override
    void save(StateWriter out) {
        saveCommon(out);
        if (plant != null) {
            out.beginObject("plant");
            plant.save(out);
            out.endObject();
        } else if (!Double.isNaN(savedUpper)) {
            out.putDouble("saved_upper", savedUpper);
        }
    }

    @Override
    void load(StateReader in) {
        loadCommon(in);
        if (in.has("plant")) {
            in.beginObject("plant");
            savedUpper = in.optDouble("upper", Double.NaN);
            try {
                savedMode = PumpedStorage.Mode.valueOf(in.optString("mode", PumpedStorage.Mode.AUTO.name()));
            } catch (IllegalArgumentException e) {
                savedMode = PumpedStorage.Mode.AUTO;
            }
            in.endObject();
        } else {
            savedUpper = in.optDouble("saved_upper", Double.NaN);
        }
    }
}
