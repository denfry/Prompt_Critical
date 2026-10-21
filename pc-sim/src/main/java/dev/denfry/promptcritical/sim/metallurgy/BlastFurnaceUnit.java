package dev.denfry.promptcritical.sim.metallurgy;

import dev.denfry.promptcritical.sim.core.FastContext;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.mining.CoalAssay;
import java.util.List;

/**
 * Двойник домны {@code blast_furnace_controller} (08 §8.5): {@link BlastFurnace} по геометрии валидатора,
 * бункер колошника и дутьё от воздуходувок через воздухонагреватели.
 *
 * <p>Колоша (моё): кокса {@value #COKE_LAYER_KG_PER_M3} кг на м³ V_u, руды — рудная нагрузка × кокс, флюса — доля
 * от руды; засыпается, когда в бункере набралось на целую колошу и в шахте есть место. «Холостые колоши» — только
 * кокс (задувка, промывка горна).
 *
 * <p>Дутьё: воздуходувки дают холодное дутьё {@value #COLD_BLAST_K} К (после сжатия, π = 3); нагреватели «на дутье»
 * делят его поровну, температура — ожидаемая на выходе. Без нагревателя на дутье — холодное дутьё.
 */
public final class BlastFurnaceUnit implements IronmakingUnit {
    public static final double COLD_BLAST_K = 423.15, DEFAULT_HOT_BLAST_K = 1173.15;
    /** Подача воздуходувки при номинальных оборотах, нм³/с физ. [BALANCE]. */
    public static final double BLOWER_NM3S = 1.5;
    /** Мощность воздуходувки при номинале: π = 3, η_пол = 0,82 (§8.5.4), Вт. */
    public static final double BLOWER_RATED_W = BLOWER_NM3S * 1.293 * BlastFurnace.blowerWorkJPerKg(3, 293.15, 0.82);
    public static final double DEFAULT_ORE_LOAD = 3.5, COKE_LAYER_KG_PER_M3 = 6, HOPPER_LAYERS = 4;
    /** Переключение нагревателей: дутьё ниже уставки на столько, К; нагреватель готов, если купол выше уставки на. */
    private static final double SWITCH_DROP_K = 20, READY_MARGIN_K = 50;

    private final IronmakingLog log = new IronmakingLog();
    private BlastFurnace furnace;
    private BlastFurnaceSpec spec;
    // бункер колошника
    private double oreKg, oreFeKg, oreSiO2Kg, oreCaOKg, cokeKg, cokeAshKg, cokeSKg, cokeM25Kg, cokeWaterKg, limeKg;
    private double oreLoad = DEFAULT_ORE_LOAD, limePerOre;
    private boolean blanks;
    // дутьё
    private double blastNm3S, blastK = 293.15, hotBlastSetK = DEFAULT_HOT_BLAST_K;
    private boolean cooling = true;
    private double topGasNm3;

    @Override
    public String deviceType() {
        return IronmakingSubsystem.BLAST_FURNACE;
    }

    /** Валидатор нашёл печь; другая геометрия — новая печь (задувка заново). */
    public void form(IronmakingConfig cfg, BlastFurnaceSpec s) {
        if (furnace != null && s.equals(spec)) return;
        spec = s;
        furnace = new BlastFurnace(s, cfg);
        furnace.setEventSink(log);
    }

    @Override
    public void step(IronmakingSubsystem works, FastContext ctx, double slowDt) {
        if (furnace == null) return;
        chargeLayer();
        if (slowDt > 0) {
            furnace.stepSlow(slowDt, new BlastConditions(blastNm3S, Math.max(blastK, 293.15), BlastConditions.AIR_O2,
                    BlastConditions.TYPICAL_MOISTURE, cooling));
            topGasNm3 += furnace.topGasNm3S() * slowDt;
        }
    }

    /** Кокса в колоше, кг. */
    public double cokeLayerKg() {
        return spec == null ? 0 : COKE_LAYER_KG_PER_M3 * spec.usefulVolumeM3();
    }

    private void chargeLayer() {
        double coke = cokeLayerKg();
        if (coke <= 0 || cokeKg < coke) return;
        double ore = blanks ? 0 : coke * oreLoad;
        if (oreKg < ore) return;
        double lime = ore * limePerOre;
        if (limeKg < lime) lime = limeKg;
        double f = coke / cokeKg, g = oreKg > 0 ? ore / oreKg : 0;
        CoalAssay a = CokeBattery.cokeAssay(cokeAshKg / (cokeKg - cokeWaterKg), cokeSKg / (cokeKg - cokeWaterKg),
                cokeM25Kg / cokeKg, cokeWaterKg / cokeKg);
        BurdenLayer l = new BurdenLayer(ore, ore > 0 ? oreFeKg / oreKg : 0, ore > 0 ? oreSiO2Kg / oreKg : 0,
                ore > 0 ? oreCaOKg / oreKg : 0, coke, a, lime);
        if (!furnace.charge(l)) return;
        cokeKg -= coke;
        cokeAshKg -= cokeAshKg * f;
        cokeSKg -= cokeSKg * f;
        cokeM25Kg -= cokeM25Kg * f;
        cokeWaterKg -= cokeWaterKg * f;
        oreFeKg -= oreFeKg * g;
        oreSiO2Kg -= oreSiO2Kg * g;
        oreCaOKg -= oreCaOKg * g;
        oreKg -= ore;
        limeKg -= lime;
    }

    /** Место в бункере под руду, кокс, флюс, кг (на {@value #HOPPER_LAYERS} колоши). */
    public double oreRoomKg() {
        return Math.max(0, HOPPER_LAYERS * cokeLayerKg() * Math.max(oreLoad, 1) - oreKg);
    }

    public double cokeRoomKg() {
        return Math.max(0, HOPPER_LAYERS * cokeLayerKg() - cokeKg);
    }

    public double limeRoomKg() {
        return Math.max(0, HOPPER_LAYERS * cokeLayerKg() * Math.max(oreLoad, 1) * Math.max(limePerOre, 0.05) - limeKg);
    }

    /** Железосодержащее в бункер: масса и доли Fe, SiO₂, CaO. */
    public void addOre(double kg, double fe, double sio2, double cao) {
        if (!(kg > 0)) return;
        oreKg += kg;
        oreFeKg += kg * fe;
        oreSiO2Kg += kg * sio2;
        oreCaOKg += kg * cao;
    }

    /** Кокс в бункер (анализ — сухая зола и сера, влага, M₂₅). */
    public void addCoke(double kg, CoalAssay a) {
        if (!(kg > 0)) return;
        double dry = kg * (1 - a.moistureFrac());
        cokeKg += kg;
        cokeWaterKg += kg - dry;
        cokeAshKg += dry * a.ashDryFrac();
        cokeSKg += dry * a.sulfurDryFrac();
        cokeM25Kg += kg * (Double.isNaN(a.strengthM25()) ? 80 : a.strengthM25());
    }

    public void addLimestone(double kg) {
        if (kg > 0) limeKg += kg;
    }

    /**
     * Дутьё на этот тик: холодное от воздуходувок и нагреватели печи; при {@code autoCycle} домна сама держит один
     * нагреватель «на дутье» и переключает на самый горячий, когда дутьё проседает.
     */
    public void couple(double coldNm3S, List<HotBlastStoveUnit> stoves, boolean autoCycle) {
        blastNm3S = Math.max(0, coldNm3S);
        if (autoCycle) cycle(stoves);
        int on = 0;
        for (HotBlastStoveUnit s : stoves) if (s.stove().mode() == StoveMode.ON_BLAST) on++;
        double sumK = 0;
        for (HotBlastStoveUnit s : stoves) {
            boolean b = s.stove().mode() == StoveMode.ON_BLAST;
            s.assignBlast(b ? blastNm3S / on : 0, COLD_BLAST_K, hotBlastSetK);
            if (b) sumK += s.deliverableK();
        }
        blastK = on > 0 ? sumK / on : blastNm3S > 0 ? COLD_BLAST_K : 293.15;
    }

    private void cycle(List<HotBlastStoveUnit> stoves) {
        HotBlastStoveUnit current = null;
        for (HotBlastStoveUnit s : stoves) {
            if (s.stove().mode() != StoveMode.ON_BLAST) continue;
            if (current == null) current = s;
            else s.stove().setMode(StoveMode.ON_GAS);
        }
        if (current == null || current.deliverableK() < hotBlastSetK - SWITCH_DROP_K) {
            HotBlastStoveUnit best = null;
            for (HotBlastStoveUnit s : stoves) {
                if (s == current || s.stove().domeK() < hotBlastSetK + READY_MARGIN_K) continue;
                if (best == null || s.stove().domeK() > best.stove().domeK()) best = s;
            }
            if (best != null) {
                if (current != null) current.stove().setMode(StoveMode.ON_GAS);
                best.stove().setMode(StoveMode.ON_BLAST);
                current = best;
            }
        }
        for (HotBlastStoveUnit s : stoves) if (s != current) s.stove().setMode(StoveMode.ON_GAS);
    }

    public void setCooling(boolean on) {
        cooling = on;
    }

    public void setBurden(double oreLoadKgPerKg, double limestonePerOre, boolean blankCharges) {
        oreLoad = Math.max(0.5, Math.min(8, oreLoadKgPerKg));
        limePerOre = Math.max(0, Math.min(0.5, limestonePerOre));
        blanks = blankCharges;
    }

    public void setHotBlast(double setpointK) {
        hotBlastSetK = Math.max(COLD_BLAST_K, Math.min(1523.15, setpointK));
    }

    /** Забрать накопленный колошниковый газ, нм³ (не больше {@code maxNm3}). */
    public double takeTopGas(double maxNm3) {
        double t = Math.min(topGasNm3, Math.max(0, maxNm3));
        topGasNm3 -= t;
        return t;
    }

    /** Колошниковый газ, сгоревший на свечах без пылеуловителя: накопление не копится сверх 1000 нм³. */
    public double ventExcessTopGas() {
        double v = Math.max(0, topGasNm3 - 1000);
        topGasNm3 -= v;
        return v;
    }

    public BlastFurnace furnace() {
        return furnace;
    }

    public IronmakingLog log() {
        return log;
    }

    public double oreLoad() {
        return oreLoad;
    }

    public double limestonePerOre() {
        return limePerOre;
    }

    public boolean blankCharges() {
        return blanks;
    }

    public double hotBlastSetK() {
        return hotBlastSetK;
    }

    public double blastNm3S() {
        return blastNm3S;
    }

    public double blastK() {
        return blastK;
    }

    public boolean cooling() {
        return cooling;
    }

    public double hopperOreKg() {
        return oreKg;
    }

    public double hopperCokeKg() {
        return cokeKg;
    }

    public double hopperLimestoneKg() {
        return limeKg;
    }

    @Override
    public void save(StateWriter out) {
        out.putDoubles("hopper", new double[] {oreKg, oreFeKg, oreSiO2Kg, oreCaOKg, cokeKg, cokeAshKg, cokeSKg, cokeM25Kg,
                cokeWaterKg, limeKg, topGasNm3}, 11);
        out.putDoubles("settings", new double[] {oreLoad, limePerOre, hotBlastSetK}, 3);
        out.putBoolean("blanks", blanks);
        if (furnace == null) return;
        out.putDoubles("spec", new double[] {spec.usefulVolumeM3(), spec.tuyeres(), spec.hearthAreaM2(),
                spec.tapholeHeightM(), spec.tuyereLevelM(), spec.ratedBlastNm3S()}, 6);
        out.beginObject("furnace");
        furnace.save(out);
        out.endObject();
    }

    @Override
    public void load(StateReader in) {
        double[] h = in.getDoubles("hopper");
        oreKg = h[0];
        oreFeKg = h[1];
        oreSiO2Kg = h[2];
        oreCaOKg = h[3];
        cokeKg = h[4];
        cokeAshKg = h[5];
        cokeSKg = h[6];
        cokeM25Kg = h[7];
        cokeWaterKg = h[8];
        limeKg = h[9];
        topGasNm3 = h[10];
        double[] st = in.getDoubles("settings");
        oreLoad = st[0];
        limePerOre = st[1];
        hotBlastSetK = st[2];
        blanks = in.getBoolean("blanks");
        if (!in.has("furnace")) return;
        double[] s = in.getDoubles("spec");
        spec = new BlastFurnaceSpec(s[0], (int) s[1], s[2], s[3], s[4], s[5]);
        furnace = new BlastFurnace(spec, IronmakingConfig.defaults());
        furnace.setEventSink(log);
        in.beginObject("furnace");
        furnace.load(in);
        in.endObject();
    }
}
