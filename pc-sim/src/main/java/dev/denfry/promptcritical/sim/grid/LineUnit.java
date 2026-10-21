package dev.denfry.promptcritical.sim.grid;

import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import java.util.Objects;

/**
 * Пролёт ВЛ между двумя коннекторами (07 §7.4.1–7.4.2): ветвь {@link Branch.Line} из провода каталога, длина —
 * расстояние между точками подвеса. Голый провод, коснувшийся блока, замыкает на землю; изолированный НН
 * ({@code wire_coil_lv}) — нет. Перегрев выше 90 °C отжигает провод до обрыва.
 */
public final class LineUnit {
    final long key;
    final SimId a, b;
    final String conductor;
    final boolean insulated;
    final double lengthM;
    BranchId branch;
    double[] carried;
    /** Блок в габарите пролёта: точка касания вдоль пролёта (0…1); NaN — касания нет. */
    double touchAt = Double.NaN;
    boolean broken;
    /** Классы узлов концов не совпали: пролёт не собран. */
    boolean classMismatch;

    LineUnit(long key, SimId a, SimId b, String conductor, boolean insulated, double lengthM) {
        this.key = key;
        this.a = Objects.requireNonNull(a, "a");
        this.b = Objects.requireNonNull(b, "b");
        this.conductor = Objects.requireNonNull(conductor, "conductor");
        spec(conductor); // проверка id
        this.insulated = insulated;
        if (!(lengthM > 0)) throw new IllegalArgumentException("lengthM");
        this.lengthM = lengthM;
    }

    /** Провод каталога §7.4.1 по id: {@code m35}, {@code m70}, {@code ac70}, {@code ac120}, {@code ac240}. */
    public static ConductorSpec spec(String id) {
        return switch (id) {
            case "m35" -> ConductorSpec.m35();
            case "m70" -> ConductorSpec.m70();
            case "ac70" -> ConductorSpec.ac70();
            case "ac120" -> ConductorSpec.ac120();
            case "ac240" -> ConductorSpec.ac240();
            default -> throw new IllegalArgumentException("unknown conductor " + id);
        };
    }

    /** x₀ провода на опоре I1 по таблице §7.4.1, Ом/км. */
    static double x0(String id) {
        return switch (id) {
            case "m35" -> 0.40;
            case "m70" -> 0.39;
            case "ac70" -> 0.44;
            case "ac120" -> 0.43;
            default -> 0.41;
        };
    }

    public long key() {
        return key;
    }

    public SimId endA() {
        return a;
    }

    public SimId endB() {
        return b;
    }

    public String conductor() {
        return conductor;
    }

    public boolean insulated() {
        return insulated;
    }

    public double lengthM() {
        return lengthM;
    }

    public boolean broken() {
        return broken;
    }

    public boolean classMismatch() {
        return classMismatch;
    }

    public boolean touching() {
        return !Double.isNaN(touchAt);
    }

    void build(GridSubsystem.Build bld, VoltageClass vc, BusId from, BusId to) {
        branch = bld.grid.addBranch(Branch.Line.overhead(from, to, spec(conductor), x0(conductor), lengthM, vc,
                bld.cfg.baseVa()));
        bld.branch(branch, this);
        double[] s = carried;
        if (s == null) {
            s = new double[PowerGrid.BRANCH_STATE];
            s[0] = 1;
            s[1] = -1;
            s[4] = 20;
        }
        bld.grid.importBranch(branch, s);
        if (touching() && !insulated)
            bld.grid.applyBranchFault(branch, touchAt, FaultType.SINGLE_LINE_GROUND, 0, false);
    }

    void capture(PowerGrid g) {
        if (branch == null) return;
        if (carried == null) carried = new double[PowerGrid.BRANCH_STATE];
        g.exportBranch(branch, carried);
    }

    void save(StateWriter out) {
        out.putLong("key", key);
        out.putLong("a", a.raw());
        out.putLong("b", b.raw());
        out.putString("conductor", conductor);
        out.putBoolean("insulated", insulated);
        out.putDouble("length_m", lengthM);
        out.putDouble("touch_at", touchAt);
        out.putBoolean("broken", broken);
        if (carried != null) out.putDoubles("branch", carried, carried.length);
    }

    static LineUnit load(StateReader in) {
        LineUnit l = new LineUnit(in.getLong("key"), new SimId(in.getLong("a")), new SimId(in.getLong("b")),
                in.getString("conductor"), in.getBoolean("insulated"), in.getDouble("length_m"));
        l.touchAt = in.getDouble("touch_at");
        l.broken = in.getBoolean("broken");
        l.carried = in.has("branch") ? in.getDoubles("branch") : null;
        if (l.carried != null && l.carried.length != PowerGrid.BRANCH_STATE) l.carried = null;
        return l;
    }
}
