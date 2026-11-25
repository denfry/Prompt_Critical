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
    /** Обточка детали с паспортом (18 §18.6.6): кованый вал; {@code null} — нет. Взаимоисключается с {@link #work}. */
    private Turning turning;
    private double volumeLeft, spindleRpm, powerW;

    /**
     * Обточка детали: диаметр после обточки, мм, длина, мм, и материал резания. Кованый вал 18 §18.7 — Ø40 × 300 с
     * припуском {@value #FORGED_ALLOWANCE_MM} мм на диаметр.
     */
    public record Turning(double diameterMm, double lengthMm, Machining.WorkMaterial material) {
        public Turning {
            if (!(diameterMm > 0) || !(lengthMm > 0) || material == null) throw new IllegalArgumentException("turning");
        }

        /** Объём припуска, мм³: кольцо от d + 2·z/2 до d по длине. */
        double volumeMm3() {
            double outer = diameterMm + FORGED_ALLOWANCE_MM;
            return Math.PI / 4 * (outer * outer - diameterMm * diameterMm) * lengthMm;
        }
    }

    /** Припуск поковки на диаметр под чистовую обточку, мм (ГОСТ 7505, поковки до 50 мм, класс точности Т4–Т5) [сверить]. */
    public static final double FORGED_ALLOWANCE_MM = 3;
    /**
     * Твёрже этого быстрорежущий резец сталь не точит: закалённый вал доводят шлифованием (Справочник технолога-
     * машиностроителя: точение Р6М5 — стали до HB 300 ≈ HRC 32; с запасом на мягкие резы [BALANCE: 35]).
     */
    public static final int MAX_TURN_HRC = 35;
    /** Радиус при вершине токарного резца, мм (как у строгального, {@link Shaping#NOSE_RADIUS_MM}). */
    public static final double LATHE_NOSE_RADIUS_MM = Shaping.NOSE_RADIUS_MM;

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
        if (spindleRpm <= 0 || volumeLeft <= 0 || tool == null || tool.dull()) return;
        if (turning != null) {
            double pc = shop.machining().cut(ctx.dt(), tool, turning.material(), turning.diameterMm(), spindleRpm,
                    kind.depthMm, kind.feedMmRev);
            if (pc > 0) {
                volumeLeft -= Machining.removalRateMm3S(kind.depthMm, kind.feedMmRev,
                        Machining.cuttingSpeed(turning.diameterMm(), spindleRpm)) * ctx.dt();
                powerW += pc;
            }
            return;
        }
        if (work == null) return;
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

    /** Поставить деталь на обточку (только токарный); {@code false} — станок занят. */
    public boolean loadTurning(Turning t) {
        if (kind != Kind.LATHE || work != null || turning != null || t == null) return false;
        turning = t;
        volumeLeft = t.volumeMm3();
        return true;
    }

    public Turning turning() {
        return turning;
    }

    /** Обточка закончена. */
    public boolean turningDone() {
        return turning != null && volumeLeft <= 0;
    }

    /**
     * Снять обточенную деталь: Ra по подаче и износу резца в момент съёма, мкм; NaN — обточка не закончена или её не
     * было (незаконченная деталь снимается без изменений).
     */
    public double unloadTurning() {
        if (turning == null) return Double.NaN;
        boolean finished = volumeLeft <= 0;
        turning = null;
        volumeLeft = 0;
        return finished ? SurfaceFinish.singlePointRaUm(kind.feedMmRev, LATHE_NOSE_RADIUS_MM, tool == null ? 0 : tool.wear())
                : Double.NaN;
    }

    /** Поставить заготовку; {@code false} — станок занят или отливка не подходит. */
    public boolean load(CastingSpec c) {
        if (work != null || turning != null || !accepts(c)) return false;
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
        if (turning != null) return Math.max(0, Math.min(1, 1 - volumeLeft / turning.volumeMm3()));
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
        return spindleRpm > 0 && (work != null || turning != null) && volumeLeft > 0 && tool != null && !tool.dull();
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
        if (turning != null) {
            out.putDouble("turn_d", turning.diameterMm());
            out.putDouble("turn_l", turning.lengthMm());
            out.putString("turn_m", turning.material().name());
            out.putDouble("turn_left", volumeLeft);
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
        turning = null;
        if (in.has("turn_d")) {
            turning = new Turning(in.getDouble("turn_d"), in.getDouble("turn_l"),
                    Machining.WorkMaterial.valueOf(in.getString("turn_m")));
            volumeLeft = in.getDouble("turn_left");
        }
    }
}
