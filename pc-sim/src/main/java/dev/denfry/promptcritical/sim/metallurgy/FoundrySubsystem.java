package dev.denfry.promptcritical.sim.metallurgy;

import dev.denfry.promptcritical.sim.core.DeviceSubsystem;
import dev.denfry.promptcritical.sim.core.FastContext;
import dev.denfry.promptcritical.sim.core.SimConfig;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.core.TimeDomain;

/**
 * Литейка и механический цех I0 (08 §8.9–8.10): вагранка, тигельная печь, песчаные формы, формовочная машина,
 * выбивная решётка, токарный и сверлильный станки. Печи и формы — класс 1 §8.0.3: их шаг SLOW дёшев (лумпед,
 * единицы флопов), поэтому выполняется на серверном потоке в FAST-тике с периодом SLOW, без задания воркеру.
 * Станки и машины — FAST, реальное время.
 *
 * <p>Обмен с миром: биндинг пишет скорость вала в {@code ShaftInput} двойника, кладёт и забирает материалы
 * методами двойников на серверном потоке между тиками (предметы — дело биндинга, домен знает килограммы).
 */
public final class FoundrySubsystem extends DeviceSubsystem<FoundryUnit> {
    public static final String ID = "foundry";
    public static final String CUPOLA = "promptcritical:cupola_furnace";
    public static final String CRUCIBLE = "promptcritical:crucible_furnace";
    public static final String SAND_MOLD = "promptcritical:sand_mold";
    public static final String MOLDING_STATION = "promptcritical:molding_station";
    public static final String SHAKEOUT_GRATE = "promptcritical:shakeout_grate";
    public static final String MACHINE_LATHE = "promptcritical:machine_lathe";
    public static final String DRILL_PRESS = "promptcritical:drill_press";
    /** Фаза SLOW подсистемы. */
    private static final int SLOW_PHASE = 3;

    private SimConfig config;
    private MetallurgyConfig metallurgy = MetallurgyConfig.defaults();
    private Casting casting = new Casting(metallurgy);
    private Machining machining = new Machining(metallurgy);
    private long nextSerial = 1;

    @Override
    public String id() {
        return ID;
    }

    @Override
    protected FoundryUnit create(String deviceType) {
        return switch (deviceType) {
            case CUPOLA -> new CupolaUnit();
            case CRUCIBLE -> new CrucibleUnit();
            case SAND_MOLD -> new SandMoldUnit();
            case MOLDING_STATION -> CyclicMachine.moldingStation();
            case SHAKEOUT_GRATE -> CyclicMachine.shakeoutGrate();
            case MACHINE_LATHE -> new MachineToolUnit(MachineToolUnit.Kind.LATHE);
            case DRILL_PRESS -> new MachineToolUnit(MachineToolUnit.Kind.DRILL_PRESS);
            default -> null;
        };
    }

    @Override
    public void stepFast(FastContext ctx) {
        if (ctx.config() != config) onConfigReload(ctx.config());
        sync(ctx.topology());
        double slowDt = ctx.clock().due(TimeDomain.SLOW, SLOW_PHASE) ? ctx.clock().slowDtPhys() : 0;
        for (int i = 0; i < size(); i++) unitAt(i).step(this, ctx, slowDt);
    }

    @Override
    public void onConfigReload(SimConfig cfg) {
        config = cfg;
        MetallurgyConfig m = cfg.section(MetallurgyConfig.class, MetallurgyConfig.defaults());
        if (!m.equals(metallurgy)) {
            metallurgy = m;
            casting = new Casting(m);
            machining = new Machining(m);
        }
    }

    public Casting casting() {
        return casting;
    }

    public Machining machining() {
        return machining;
    }

    /**
     * Выпуск из печи в форму: весь металл модели одной заливкой, перегрев — на момент выпуска.
     *
     * @return {@code true} — форма залита
     */
    public boolean pour(FoundryUnit.MeltSource from, SandMoldUnit into) {
        if (!into.acceptsPour()) return false;
        CastAlloy alloy = from.meltAlloy();
        double need = into.pourMassKg();
        if (alloy == null || from.liquidKg() < need) return false;
        double superheat = from.superheatK();
        if (from.tap(need) < need) return false;
        into.pour(casting, alloy, superheat, nextSerial++);
        return true;
    }

    @Override
    public void save(StateWriter out) {
        out.putLong("next_serial", nextSerial);
        out.beginObject("units");
        super.save(out);
        out.endObject();
    }

    @Override
    public void load(StateReader in) {
        nextSerial = in.getLong("next_serial");
        in.beginObject("units");
        super.load(in);
        in.endObject();
    }
}
