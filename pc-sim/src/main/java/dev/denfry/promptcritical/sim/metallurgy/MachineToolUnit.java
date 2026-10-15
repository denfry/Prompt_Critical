package dev.denfry.promptcritical.sim.metallurgy;

import dev.denfry.promptcritical.sim.core.FastContext;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;

/**
 * Двойник станка I0 на валу Create (08 §8.10): {@code machine_lathe} точит, {@code drill_press} сверлит отливку.
 * Шпиндель — вал × передаточное число (у сверлильного 4: Create даёт до 256 об/мин, сверлу нужно до 1000).
 * Резание по {@link Machining}: мощность P_c = k_c·a_p·f·v_c → стресс, износ резца по Тейлору — на высоких
 * оборотах резец тупится в разы быстрее. Сверление — две кромки: a_p = d/2, подача на кромку f/2.
 * Отбелённый чугун не режется — станок его не берёт.
 */
public final class MachineToolUnit implements FoundryUnit {
    /** Станок: передаточное число шпинделя, глубина резания (NaN — половина диаметра), подача, мощность. */
    public enum Kind {
        LATHE(FoundrySubsystem.MACHINE_LATHE, 1, 2.0, 0.25, 7_500, CastingSpec.TURNED),
        DRILL_PRESS(FoundrySubsystem.DRILL_PRESS, 4, Double.NaN, 0.20, 4_000, CastingSpec.DRILLED);

        public final String deviceType;
        public final double gear, depthMm, feedMmRev, ratedW;
        public final int operation;

        Kind(String deviceType, double gear, double depthMm, double feedMmRev, double ratedW, int operation) {
            this.deviceType = deviceType;
            this.gear = gear;
            this.depthMm = depthMm;
            this.feedMmRev = feedMmRev;
            this.ratedW = ratedW;
            this.operation = operation;
        }
    }

    /** Холостой ход привода — доля номинала (03a §3a.2.4). */
    public static final double IDLE_SHARE = 0.02;
    public static final double MIN_RPM = 8;

    private final Kind kind;
    public final ShaftInput shaft = new ShaftInput();
    private Machining.CuttingTool tool;
    private CastingSpec work;
    private double volumeLeft, spindleRpm, powerW;

    public MachineToolUnit(Kind kind) {
        this.kind = kind;
    }

    @Override
    public String deviceType() {
        return kind.deviceType;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public void step(FoundrySubsystem shop, FastContext ctx, double slowDt) {
        double rpm = shaft.rpm(ctx.tick());
        spindleRpm = rpm < MIN_RPM ? 0 : rpm * kind.gear;
        powerW = kind.ratedW * IDLE_SHARE * Math.min(1, spindleRpm / (64 * kind.gear));
        if (spindleRpm <= 0 || work == null || volumeLeft <= 0 || tool == null || tool.dull()) return;
        CastingGeometry g = CastingGeometry.of(work.kind());
        Machining.WorkMaterial m = Machining.WorkMaterial.of(CastAlloy.byId(work.alloyId()));
        double d = diameterMm(g);
        double depth = Double.isNaN(kind.depthMm) ? d / 2 : kind.depthMm;
        double feed = Double.isNaN(kind.depthMm) ? kind.feedMmRev / 2 : kind.feedMmRev;
        double pc = shop.machining().cut(ctx.dt(), tool, m, d, spindleRpm, depth, feed);
        if (pc > 0) {
            volumeLeft -= Machining.removalRateMm3S(depth, feed, Machining.cuttingSpeed(d, spindleRpm)) * ctx.dt();
            powerW += pc;
        }
    }

    private double diameterMm(CastingGeometry g) {
        return kind == Kind.LATHE ? g.turnDiameterMm() : g.drillDiameterMm();
    }

    private double volumeMm3(CastingGeometry g) {
        return kind == Kind.LATHE ? g.turnVolumeMm3() : g.drillVolumeMm3();
    }

    /** Отливку можно поставить: эта операция ей нужна и ещё не сделана, чугун не отбелён. */
    public boolean accepts(CastingSpec c) {
        CastAlloy a = CastAlloy.byId(c.alloyId());
        return a != null && c.defect() != CastingDefect.CHILL && (c.machinedMask() & kind.operation) == 0
                && volumeMm3(CastingGeometry.of(c.kind())) > 0;
    }

    /** Поставить заготовку; {@code false} — станок занят или отливка не подходит. */
    public boolean load(CastingSpec c) {
        if (work != null || !accepts(c)) return false;
        work = c;
        volumeLeft = volumeMm3(CastingGeometry.of(c.kind()));
        return true;
    }

    /** Заготовка обработана. */
    public boolean done() {
        return work != null && volumeLeft <= 0;
    }

    /** Снять заготовку: обработанную — с отметкой операции. {@code null} — станок пуст. */
    public CastingSpec unload() {
        CastingSpec c = work;
        if (c == null) return null;
        boolean finished = volumeLeft <= 0;
        work = null;
        volumeLeft = 0;
        return finished ? c.machined(kind.operation) : c;
    }

    /** Поставить резец с износом {@code wear}; {@code false} — резец уже стоит. */
    public boolean insertTool(double wear) {
        if (tool != null) return false;
        tool = new Machining.CuttingTool(Machining.ToolMaterial.HSS, wear);
        return true;
    }

    /** Снять резец; NaN — резца нет. */
    public double removeTool() {
        if (tool == null) return Double.NaN;
        double w = tool.wear();
        tool = null;
        return w;
    }

    /** Износ резца; NaN — резца нет. */
    public double toolWear() {
        return tool == null ? Double.NaN : tool.wear();
    }

    public CastingSpec work() {
        return work;
    }

    /** Доля выполненной операции. */
    public double progress() {
        if (work == null) return 0;
        double v = volumeMm3(CastingGeometry.of(work.kind()));
        return v > 0 ? Math.max(0, Math.min(1, 1 - volumeLeft / v)) : 1;
    }

    public double spindleRpm() {
        return spindleRpm;
    }

    /** Мощность на валу, Вт. */
    public double powerW() {
        return powerW;
    }

    /** Режет сейчас. */
    public boolean running() {
        return spindleRpm > 0 && work != null && volumeLeft > 0 && tool != null && !tool.dull();
    }

    @Override
    public void save(StateWriter out) {
        out.putBoolean("has_tool", tool != null);
        if (tool != null) out.putDouble("tool_wear", tool.wear());
        out.putBoolean("has_work", work != null);
        if (work != null) {
            out.putString("work_kind", work.kind().name());
            out.putString("work_alloy", work.alloyId());
            out.putInt("work_dn", work.dn());
            out.putString("work_defect", work.defect().name());
            out.putInt("work_inspected", work.inspectedMask());
            out.putLong("work_serial", work.serial());
            out.putInt("work_machined", work.machinedMask());
            out.putDouble("volume_left", volumeLeft);
        }
    }

    @Override
    public void load(StateReader in) {
        tool = in.getBoolean("has_tool") ? new Machining.CuttingTool(Machining.ToolMaterial.HSS, in.getDouble("tool_wear")) : null;
        work = null;
        volumeLeft = 0;
        if (in.getBoolean("has_work")) {
            work = new CastingSpec(CastingKind.valueOf(in.getString("work_kind")), in.getString("work_alloy"),
                    in.getInt("work_dn"), CastingDefect.valueOf(in.getString("work_defect")), in.getInt("work_inspected"),
                    in.getLong("work_serial"), in.getInt("work_machined"));
            volumeLeft = in.getDouble("volume_left");
        }
    }
}
