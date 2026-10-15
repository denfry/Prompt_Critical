package dev.denfry.promptcritical.sim.metallurgy;

import dev.denfry.promptcritical.sim.core.FastContext;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Двойник {@code sand_mold} (08 §8.9): заформованная модель, заливка, затвердевание по Чворинову и остывание до
 * выбивки. Время — физическое SLOW (форма остывает вместе с печами, класс 1 §8.0.3). Форма под дождём набирает влагу:
 * выше 6 % отливка выйдет с газовой пористостью.
 */
public final class SandMoldUnit implements FoundryUnit {
    /** Влажность формы с формовочной машины. */
    public static final double DEFAULT_MOISTURE = 0.05;
    /** Набор влаги под дождём, доля в секунду реального времени [BALANCE: от 5 до 6 % за 20 с]. */
    public static final double RAIN_WETTING_PER_S = 5e-4;
    public static final double MAX_MOISTURE = 0.3;
    /** После затвердевания отливка остывает до выбивки ещё столько же времени ×2 [допущение]. */
    public static final double COOLING_FACTOR = 2;

    public enum Stage { EMPTY, LIQUID, SOLID }

    private CastingKind kind;
    private boolean riser = true;
    private double moisture = DEFAULT_MOISTURE;
    private Stage stage = Stage.EMPTY;
    private CastAlloy alloy;
    private double solidificationS, elapsedS;
    private CastingDefect defect = CastingDefect.NONE;
    private long serial;

    @Override
    public String deviceType() {
        return FoundrySubsystem.SAND_MOLD;
    }

    @Override
    public void step(FoundrySubsystem shop, FastContext ctx, double slowDt) {
        if (slowDt <= 0 || stage == Stage.EMPTY) return;
        elapsedS += slowDt;
        if (stage == Stage.LIQUID && elapsedS >= solidificationS) stage = Stage.SOLID;
    }

    /** Заформовать модель (форма с формовочной машины). */
    public void form(CastingKind kind, boolean riser, double moisture) {
        if (stage != Stage.EMPTY) throw new IllegalStateException("mold already poured");
        this.kind = kind;
        this.riser = riser;
        this.moisture = Math.max(0, Math.min(MAX_MOISTURE, moisture));
    }

    /** Форма под дождём {@code dtReal} секунд реального времени. */
    public void wet(double dtReal) {
        if (stage == Stage.EMPTY) moisture = Math.min(MAX_MOISTURE, moisture + RAIN_WETTING_PER_S * dtReal);
    }

    /** Форма ждёт металла: заформована и пуста. */
    public boolean acceptsPour() {
        return stage == Stage.EMPTY && kind != null;
    }

    /** Масса металла на заливку, кг. */
    public double pourMassKg() {
        return kind == null ? 0 : CastingGeometry.of(kind).massKg();
    }

    /** Залить; перегрев — над ликвидусом сплава, К. */
    void pour(Casting casting, CastAlloy alloy, double superheatK, long serial) {
        CastingGeometry g = CastingGeometry.of(kind);
        Casting.Result r = casting.pour(new Casting.Pour(kind, alloy, MoldKind.SAND, g.moduleCm(), g.riserModuleCm(riser),
                moisture, superheatK, true, g.thicknessRatio(), g.dn()));
        this.alloy = alloy;
        this.solidificationS = r.solidificationS();
        this.defect = r.defect();
        this.serial = serial;
        this.elapsedS = 0;
        this.stage = Stage.LIQUID;
    }

    /** Отливка остыла — можно выбивать. */
    public boolean readyForShakeout() {
        return stage == Stage.SOLID && elapsedS >= solidificationS * (1 + COOLING_FACTOR);
    }

    /** Паспорт отливки; {@code null} — форма не залита или металл ещё жидкий. */
    public CastingSpec casting() {
        if (stage != Stage.SOLID) return null;
        return new CastingSpec(kind, alloy.id, CastingGeometry.of(kind).dn(), defect, 0, serial);
    }

    public Stage stage() {
        return stage;
    }

    public CastingKind kind() {
        return kind;
    }

    public boolean riser() {
        return riser;
    }

    public double moisture() {
        return moisture;
    }

    public double solidificationS() {
        return solidificationS;
    }

    public double elapsedS() {
        return elapsedS;
    }

    @Override
    public void save(StateWriter out) {
        out.putString("kind", kind == null ? "" : kind.name());
        out.putBoolean("riser", riser);
        out.putDouble("moisture", moisture);
        out.putString("stage", stage.name());
        out.putString("alloy", alloy == null ? "" : alloy.id);
        out.putDouble("t_s", solidificationS);
        out.putDouble("elapsed", elapsedS);
        out.putString("defect", defect.name());
        out.putLong("serial", serial);
    }

    @Override
    public void load(StateReader in) {
        String k = in.getString("kind");
        kind = k.isEmpty() ? null : CastingKind.valueOf(k);
        riser = in.getBoolean("riser");
        moisture = in.getDouble("moisture");
        stage = Stage.valueOf(in.getString("stage"));
        alloy = CastAlloy.byId(in.getString("alloy"));
        solidificationS = in.getDouble("t_s");
        elapsedS = in.getDouble("elapsed");
        defect = CastingDefect.valueOf(in.getString("defect"));
        serial = in.getLong("serial");
        if (stage != Stage.EMPTY && (alloy == null || kind == null)) stage = Stage.EMPTY;
    }
}
