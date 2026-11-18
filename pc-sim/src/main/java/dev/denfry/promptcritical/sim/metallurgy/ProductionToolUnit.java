package dev.denfry.promptcritical.sim.metallurgy;

import dev.denfry.promptcritical.sim.core.FastContext;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import java.util.Objects;

/**
 * Двойник станка станочного парка на валу Create: консольно-фрезерный (типа 6Р82), поперечно-строгальный (типа 7305)
 * и плоскошлифовальный (типа 3Г71). Привод — вал Create (от паровой машины или электродвигателя сети) через коробку
 * скоростей станка; мощность резания уходит в стресс вала, как у токарного ({@link MachineToolUnit}).
 *
 * <p>Режим: скорость — от оборотов вала, подача — одна из четырёх ступеней коробки подач ({@link #cycleFeed}). Сила и
 * мощность — по Кинцле ({@link Milling}, {@link Shaping}) и Малкину ({@link Grinding}), износ фрезы и резца — по Тейлору
 * с долей времени в резании, засаливание круга — по снятому объёму. Чистота последнего прохода даёт Ra и квалитет
 * детали ({@link SurfaceFinish}); грубее допуска, прижог или литейный дефект — брак.
 */
public final class ProductionToolUnit implements FoundryUnit {
    /** Станок: передаточное число от вала, номинальная мощность главного привода, ступени подачи. */
    public enum Kind {
        /** Шпиндель = вал × 2 (64 → 128 об/мин, v_c ≈ 32 м/мин фрезой Ø80); подача на зуб f_z, мм. */
        MILLING(FoundrySubsystem.MILLING_MACHINE, 2, 7_500, 0.04, 0.08, 0.12, 0.20),
        /** Двойные ходы = вал × 0,5 (64 → 32 дв. ход/мин, v_c ≈ 24 м/мин); подача на двойной ход f, мм; привод 4 кВт [сверить]. */
        SHAPER(FoundrySubsystem.SHAPING_MACHINE, 0.5, 4_000, 0.2, 0.3, 0.5, 0.8),
        /** Круг = вал × 42 (64 → 2688 об/мин, v_s ≈ 35 м/с); вертикальная подача на ход a, мм; привод круга 2,2 кВт [сверить]. */
        GRINDER(FoundrySubsystem.SURFACE_GRINDER, 42, 2_200, 0.005, 0.01, 0.02, 0.04);

        public final String deviceType;
        public final double gear, ratedW;
        private final double[] feeds;

        Kind(String deviceType, double gear, double ratedW, double... feeds) {
            this.deviceType = deviceType;
            this.gear = gear;
            this.ratedW = ratedW;
            this.feeds = feeds;
        }

        public int feedSteps() {
            return feeds.length;
        }

        public double feed(int step) {
            return feeds[Math.floorMod(step, feeds.length)];
        }
    }

    /** Заготовка: какая деталь из неё выйдет, материал, без литейных дефектов ли. */
    public record Workpiece(MachinedPart part, Machining.WorkMaterial material, boolean sound) {
        public Workpiece {
            Objects.requireNonNull(part, "part");
            Objects.requireNonNull(material, "material");
        }
    }

    /** Почему деталь в брак. */
    public enum Reject { NONE, DEFECT, ROUGH, BURN }

    /** Снятая со станка деталь: Ra последнего прохода и квалитет; {@code reject != NONE} — брак. */
    public record Result(MachinedPart part, Machining.WorkMaterial material, double raUm, int itGrade, Reject reject) {
        public boolean accepted() {
            return reject == Reject.NONE;
        }
    }

    public static final double IDLE_SHARE = MachineToolUnit.IDLE_SHARE, MIN_RPM = MachineToolUnit.MIN_RPM;
    /** Номинальная скорость вала, об/мин. */
    public static final double NOMINAL_SHAFT_RPM = 64;

    private final Kind kind;
    public final ShaftInput shaft = new ShaftInput();
    /** Фреза, резец или круг; у круга «износ» — засаливание (0 — после правки, 1 — пора править). */
    private Machining.CuttingTool tool;
    private Workpiece work;
    private int feedStep = 1;
    private double volumeLeft, speed, powerW, raUm = Double.NaN;
    private boolean burnt, wheelBurst;

    public ProductionToolUnit(Kind kind) {
        this.kind = Objects.requireNonNull(kind, "kind");
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
        speed = rpm < MIN_RPM ? 0 : rpm * kind.gear;
        powerW = kind.ratedW * IDLE_SHARE * Math.min(1, speed / (NOMINAL_SHAFT_RPM * kind.gear));
        if (kind == Kind.GRINDER && tool != null && Grinding.wheelSpeedMS(speed) > Grinding.BURST_SPEED_MS) {
            tool = null;                                              // круг разорвало
            wheelBurst = true;
            return;
        }
        if (!running()) return;
        double dt = ctx.dt();
        double removal = switch (kind) {
            case MILLING -> mill(shop.machining(), dt);
            case SHAPER -> shape(shop.machining(), dt);
            case GRINDER -> grind(dt);
        };
        volumeLeft -= removal * dt;
    }

    private double mill(Machining machining, double dt) {
        MachinedPart p = work.part();
        double fz = feed(), d = Milling.CUTTER_D_MM;
        double vc = Machining.cuttingSpeed(d, speed);
        double q = Milling.removalRateMm3S(p.depthMm(), p.widthMm(), Milling.tableFeedMmMin(fz, Milling.CUTTER_TEETH, speed));
        double kc = Machining.kienzleKc(work.material(), Milling.meanChipThicknessMm(fz, p.depthMm(), d))
                * Machining.wearForceFactor(tool.wear());
        powerW += Milling.cuttingPowerW(q, kc);
        tool.wear(dt, Milling.engagementShare(p.depthMm(), d), machining.toolLifeMin(tool.material(), work.material(), vc));
        raUm = SurfaceFinish.millingRaUm(fz, Milling.CUTTER_TEETH, d, tool.wear());
        return q;
    }

    private double shape(Machining machining, double dt) {
        MachinedPart p = work.part();
        double f = feed();
        double vc = Shaping.cuttingSpeedMMin(Shaping.STROKE_MM, speed);
        double force = Shaping.cuttingForceN(work.material(), f, p.depthMm(), tool.wear());
        powerW += Shaping.strokePowerW(force, vc) * Shaping.CUT_SHARE;
        tool.wear(dt, Shaping.CUT_SHARE, machining.toolLifeMin(tool.material(), work.material(), vc));
        raUm = SurfaceFinish.singlePointRaUm(f, Shaping.NOSE_RADIUS_MM, tool.wear());
        return Shaping.removalRateMm3S(Shaping.STROKE_MM, f, p.depthMm(), speed);
    }

    private double grind(double dt) {
        double a = feed();
        double vs = Grinding.wheelSpeedMS(speed);
        double vw = Grinding.TABLE_SPEED_M_MIN * speed / (NOMINAL_SHAFT_RPM * kind.gear);
        double q = Grinding.removalRateMm3S(a, vw);
        double u = Grinding.specificEnergy(a, vs, vw, tool.wear());
        if (u > Grinding.burnLimit(a, vw)) burnt = true;
        powerW += u * q;
        // засаливание по снятому объёму: «стойкость» круга — время, за которое он снимет объём до правки
        tool.wear(dt, 1, Grinding.DRESS_VOLUME_MM3 / q / 60);
        raUm = Grinding.raUm(a, vs, vw);
        return q;
    }

    /** Заготовка подходит станку. */
    public boolean accepts(Workpiece w) {
        return w != null && w.part().machine() == kind;
    }

    /** Поставить заготовку; {@code false} — занят или не тот станок. */
    public boolean load(Workpiece w) {
        if (work != null || !accepts(w)) return false;
        work = w;
        volumeLeft = w.part().volumeMm3();
        raUm = Double.NaN;
        burnt = false;
        return true;
    }

    public boolean done() {
        return work != null && volumeLeft <= 0;
    }

    /** Что выйдет со станка сейчас, не снимая детали; {@code null} — не готова или станок пуст. */
    public Result peek() {
        if (!done()) return null;
        // профилометр показывает Ra с точностью 0,01 мкм — по показанию и принимают деталь
        double ra = Math.round((Double.isNaN(raUm) ? SurfaceFinish.RA_FLOOR_UM : raUm) * 100) / 100.0;
        Reject reject = !work.sound() ? Reject.DEFECT
                : burnt ? Reject.BURN
                : ra > work.part().maxRaUm() ? Reject.ROUGH : Reject.NONE;
        return new Result(work.part(), work.material(), ra, SurfaceFinish.itGrade(ra), reject);
    }

    /** Снять готовую деталь; {@code null} — не готова или станок пуст. */
    public Result unload() {
        Result r = peek();
        if (r == null) return null;
        work = null;
        volumeLeft = 0;
        burnt = false;
        return r;
    }

    /** Следующая ступень подачи; возвращает новую подачу, мм. */
    public double cycleFeed() {
        feedStep = (feedStep + 1) % kind.feedSteps();
        return feed();
    }

    public void setFeedStep(int step) {
        feedStep = Math.floorMod(step, kind.feedSteps());
    }

    public int feedStep() {
        return feedStep;
    }

    /** Подача текущей ступени: f_z, f или a — мм. */
    public double feed() {
        return kind.feed(feedStep);
    }

    /** Поставить фрезу, резец или круг с износом {@code wear}; {@code false} — инструмент уже стоит. */
    public boolean insertTool(double wear) {
        if (tool != null) return false;
        tool = new Machining.CuttingTool(Machining.ToolMaterial.HSS, wear);
        return true;
    }

    /** Снять инструмент; NaN — его нет. */
    public double removeTool() {
        if (tool == null) return Double.NaN;
        double w = tool.wear();
        tool = null;
        return w;
    }

    /** Износ фрезы или резца, засаливание круга; NaN — инструмента нет. */
    public double toolWear() {
        return tool == null ? Double.NaN : tool.wear();
    }

    /** Круг разорвало с прошлого вызова (флаг сбрасывается). */
    public boolean takeWheelBurst() {
        boolean b = wheelBurst;
        wheelBurst = false;
        return b;
    }

    public Workpiece work() {
        return work;
    }

    public double progress() {
        if (work == null) return 0;
        return Math.max(0, Math.min(1, 1 - volumeLeft / work.part().volumeMm3()));
    }

    /** Шпиндель, об/мин; у строгального — двойные ходы в минуту. */
    public double speed() {
        return speed;
    }

    /** Скорость резания: м/мин у фрезы и резца, м/с у круга; 0 — стоит. */
    public double cuttingSpeed() {
        return switch (kind) {
            case MILLING -> Machining.cuttingSpeed(Milling.CUTTER_D_MM, speed);
            case SHAPER -> Shaping.cuttingSpeedMMin(Shaping.STROKE_MM, speed);
            case GRINDER -> Grinding.wheelSpeedMS(speed);
        };
    }

    /** Ra последнего прохода, мкм; NaN — ещё не резал. */
    public double raUm() {
        return raUm;
    }

    /** Прижог в текущей детали. */
    public boolean burnt() {
        return burnt;
    }

    public double powerW() {
        return powerW;
    }

    public boolean running() {
        return speed > 0 && work != null && volumeLeft > 0 && tool != null && !tool.dull();
    }

    @Override
    public void save(StateWriter out) {
        out.putInt("feed_step", feedStep);
        out.putBoolean("has_tool", tool != null);
        if (tool != null) out.putDouble("tool_wear", tool.wear());
        out.putBoolean("has_work", work != null);
        if (work != null) {
            out.putString("work_part", work.part().itemId());
            out.putString("work_material", work.material().name());
            out.putBoolean("work_sound", work.sound());
            out.putDouble("volume_left", volumeLeft);
            out.putDouble("ra_um", raUm);
            out.putBoolean("burnt", burnt);
        }
    }

    @Override
    public void load(StateReader in) {
        feedStep = Math.floorMod(in.getInt("feed_step"), kind.feedSteps());
        tool = in.getBoolean("has_tool") ? new Machining.CuttingTool(Machining.ToolMaterial.HSS, in.getDouble("tool_wear")) : null;
        work = null;
        volumeLeft = 0;
        raUm = Double.NaN;
        burnt = false;
        if (in.getBoolean("has_work")) {
            MachinedPart part = MachinedPart.byItemId(in.getString("work_part"));
            if (part != null) {
                work = new Workpiece(part, Machining.WorkMaterial.valueOf(in.getString("work_material")), in.getBoolean("work_sound"));
                volumeLeft = in.getDouble("volume_left");
                raUm = in.getDouble("ra_um");
                burnt = in.getBoolean("burnt");
            }
        }
    }
}
