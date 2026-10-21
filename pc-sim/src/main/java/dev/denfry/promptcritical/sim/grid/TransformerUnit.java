package dev.denfry.promptcritical.sim.grid;

import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * {@code distribution_transformer} (07 §7.4.3): ТМ-630/6 — 630 кВ·А, 6,3/0,4 кВ, u_k 5,5 %, P_k 7,6 кВт, P_0 1,3 кВт,
 * I_0 2 % [сверить по каталогу]; ПБВ ±2×2,5 % без нагрузки, ONAN распределительный. Вывод 0 — ВН, 1 — НН.
 */
public final class TransformerUnit extends GridUnit {
    public static final double RATED_VA = 630e3;
    static final TapChanger TAPS = new TapChanger(-2, 2, 0.025, 0, false);

    int tap;
    BranchId branch;
    double[] carried;

    TransformerUnit(SimId id) {
        super(id, 2);
    }

    @Override
    VoltageClass terminalClass(int terminal) {
        return terminal == 0 ? VoltageClass.MV_6_3 : VoltageClass.LV_0_4;
    }

    @Override
    boolean definesClass(int terminal) {
        return true;
    }

    public int tap() {
        return tap;
    }

    /** Ступень ПБВ (переключение без возбуждения): только на отключённом трансформаторе. */
    public boolean setTap(int step) {
        if (step < TAPS.minStep() || step > TAPS.maxStep()) return false;
        if (step == tap) return true;
        if (loadedNow()) return false;
        tap = step;
        if (branch != null && owner.ready()) owner.grid().setTap(branch, step);
        return true;
    }

    private boolean loadedNow() {
        return owner != null && owner.ready() && (owner.voltageV(node(0)) > 0 || owner.voltageV(node(1)) > 0);
    }

    /** Ток стороны ВН, А. */
    public double currentA() {
        return branch != null && owner.ready() ? owner.grid().branchCurrentA(branch) : 0;
    }

    public double powerW() {
        return branch != null && owner.ready() ? owner.grid().branchPowerFromW(branch) : 0;
    }

    /** Загрузка |S|/S_ном по стороне ВН. */
    public double loading() {
        double v = owner.voltageV(node(0));
        return v <= 0 ? 0 : Math.sqrt(3) * v * currentA() / RATED_VA;
    }

    /** Температура наиболее нагретой точки, °C. */
    public double hotSpotC() {
        return branch != null && owner.ready() ? owner.grid().branchTemperatureC(branch) : carried != null ? carried[4] : 20;
    }

    public double lossOfLifeH() {
        return branch != null && owner.ready() ? owner.grid().transformerLossOfLifeH(branch) : carried != null ? carried[8] : 0;
    }

    @Override
    void build(GridSubsystem.Build b) {
        if (node(0) == NO_NODE || node(1) == NO_NODE || node(0) == node(1)) return;
        branch = b.grid.addBranch(new Branch.Transformer(b.bus(node(0)), b.bus(node(1)), RATED_VA, 0.055, 7.6e3, 1.3e3,
                0.02, TAPS, TransformerThermal.onanDistribution()));
        b.branch(branch, this);
        if (carried != null) {
            b.grid.importBranch(branch, carried);
        } else {
            double[] s = new double[PowerGrid.BRANCH_STATE];
            s[0] = 1;
            s[1] = -1;
            s[4] = 20;
            b.grid.importBranch(branch, s);
        }
        if (tap != 0) b.grid.setTap(branch, tap);
    }

    @Override
    void capture(PowerGrid g) {
        if (branch == null) return;
        if (carried == null) carried = new double[PowerGrid.BRANCH_STATE];
        g.exportBranch(branch, carried);
    }

    @Override
    void unbind() {
        branch = null;
    }

    @Override
    void save(StateWriter out) {
        saveCommon(out);
        out.putInt("tap", tap);
        if (carried != null) out.putDoubles("branch", carried, carried.length);
    }

    @Override
    void load(StateReader in) {
        loadCommon(in);
        tap = in.getInt("tap");
        carried = in.has("branch") ? in.getDoubles("branch") : null;
        if (carried != null && carried.length != PowerGrid.BRANCH_STATE) carried = null;
    }
}
