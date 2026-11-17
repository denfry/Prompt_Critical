package dev.denfry.promptcritical.sim.grid;

import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Батарея статических конденсаторов 6 кВ ({@code capacitor_bank}): {@link #MAX_STEPS} ступеней по {@link #STEP_VAR}.
 * Шунт постоянного сопротивления: Q = −n·Q_ст·v², отдаёт реактивную мощность в сеть — разгружает генераторы и линии
 * от тока намагничивания двигателей и поднимает напряжение в конце линии. Пересборка сети при смене ступени.
 */
public final class CapacitorBankUnit extends GridUnit {
    public static final int MAX_STEPS = 4;
    public static final double STEP_VAR = 150e3;

    int steps;
    LoadId load;

    CapacitorBankUnit(SimId id) {
        super(id, 1);
    }

    @Override
    VoltageClass terminalClass(int terminal) {
        return VoltageClass.MV_6_3;
    }

    public int steps() {
        return steps;
    }

    /** Следующая ступень (после последней — все отключены). */
    public void cycleSteps() {
        steps = (steps + 1) % (MAX_STEPS + 1);
        changed();
    }

    public void setSteps(int n) {
        int s = Math.max(0, Math.min(MAX_STEPS, n));
        if (s == steps) return;
        steps = s;
        changed();
    }

    /** Номинальная мощность включённых ступеней, вар. */
    public double ratedVar() {
        return steps * STEP_VAR;
    }

    /** Отдаваемая реактивная мощность при нынешнем напряжении, вар (Q ∝ v²). */
    public double reactiveVar() {
        if (load == null || !owner.ready() || node(0) == NO_NODE) return 0;
        double v = owner.voltageV(node(0)) / VoltageClass.MV_6_3.volts;
        return ratedVar() * v * v;
    }

    @Override
    void build(GridSubsystem.Build b) {
        if (node(0) == NO_NODE || steps == 0) return;
        load = b.grid.addLoad(LoadModel.typical(LoadKind.CAPACITOR, ratedVar()), b.bus(node(0)));
    }

    @Override
    void unbind() {
        load = null;
    }

    @Override
    void save(StateWriter out) {
        saveCommon(out);
        out.putInt("steps", steps);
    }

    @Override
    void load(StateReader in) {
        loadCommon(in);
        steps = Math.max(0, Math.min(MAX_STEPS, in.getInt("steps")));
    }
}
