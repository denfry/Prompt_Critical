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
    // машины коксохима и домны на валу Create (этап 10 M3): цикл и мощность, остальное — в мире
    public static final String MAGNETIC_SEPARATOR = "promptcritical:magnetic_separator";
    public static final String SINTER_STRAND = "promptcritical:sinter_strand";
    public static final String ELECTRODE_EXTRUDER = "promptcritical:electrode_extruder";
    public static final String BLAST_BLOWER = "promptcritical:blast_blower";
    public static final String GAS_EXHAUSTER = "promptcritical:gas_exhauster";
    /** Дутьевой вентилятор и дымосос котла I1 (07 §7.9.5): машины на валу, расход — в {@code WaterTubeUnit}. */
    public static final String FORCED_DRAFT_FAN = "promptcritical:forced_draft_fan", INDUCED_DRAFT_FAN = "promptcritical:induced_draft_fan";
    /**
     * Мощность вентиляторов на номинальных оборотах, Вт: дутьевой 3 кг/с воздуха при 2 кПа, дымосос 3,5 кг/с газов
     * 150 °C при 2,5 кПа, КПД 0,7.
     */
    public static final double FORCED_DRAFT_FAN_W = 7_000, INDUCED_DRAFT_FAN_W = 15_000;
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
            // [BALANCE]: сепаратор 6 с / 11 кВт, агломашина 20 с / 30 кВт, пресс 15 с / 22 кВт; воздуходувка и
            // эксгаустер — по расходу (BlastBlower.RATED_W, GasMain.EXHAUSTER_RATED_W), цикл не используется
            case MAGNETIC_SEPARATOR -> new CyclicMachine(MAGNETIC_SEPARATOR, 6, 11_000);
            case SINTER_STRAND -> new CyclicMachine(SINTER_STRAND, 20, 30_000);
            case ELECTRODE_EXTRUDER -> new CyclicMachine(ELECTRODE_EXTRUDER, 15, 22_000);
            case BLAST_BLOWER -> new CyclicMachine(BLAST_BLOWER, 1, BlastFurnaceUnit.BLOWER_RATED_W);
            case GAS_EXHAUSTER -> new CyclicMachine(GAS_EXHAUSTER, 1, GasMain.EXHAUSTER_RATED_W);
            case FORCED_DRAFT_FAN -> new CyclicMachine(FORCED_DRAFT_FAN, 1, FORCED_DRAFT_FAN_W);
            case INDUCED_DRAFT_FAN -> new CyclicMachine(INDUCED_DRAFT_FAN, 1, INDUCED_DRAFT_FAN_W);
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
