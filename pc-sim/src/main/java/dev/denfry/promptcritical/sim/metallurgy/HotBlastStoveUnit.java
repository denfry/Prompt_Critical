package dev.denfry.promptcritical.sim.metallurgy;

import dev.denfry.promptcritical.sim.combustion.Combustor;
import dev.denfry.promptcritical.sim.core.FastContext;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Двойник воздухонагревателя Каупера {@code hot_blast_stove} (08 §8.5.2): {@link HotBlastStove} 3×9×3, насадка
 * 60 т [BALANCE]. Режим и дутьё назначает домна ({@link BlastFurnaceUnit#couple}); газ «на газе» — по клапану,
 * сколько мир подал из труб.
 */
public final class HotBlastStoveUnit implements IronmakingUnit {
    public static final double CHECKER_KG = 60_000, DEFAULT_VALVE_NM3S = 0.6, AIR_EXCESS = 1.1;

    private final HotBlastStove stove = new HotBlastStove(CHECKER_KG, 293.15);
    private ProcessGas gas;
    private double gasNm3S, valveNm3S = DEFAULT_VALVE_NM3S;
    private double blastNm3S, coldK = 293.15, setK = BlastFurnaceUnit.DEFAULT_HOT_BLAST_K;
    private Combustor combustor;
    private ProcessGas combustorGas;

    @Override
    public String deviceType() {
        return IronmakingSubsystem.HOT_BLAST_STOVE;
    }

    @Override
    public void step(IronmakingSubsystem works, FastContext ctx, double slowDt) {
        if (slowDt <= 0) return;
        Combustor c = combustor();
        double fuel = gas == null ? 0 : gasNm3S * gas.densityKgNm3;
        stove.setHeating(fuel, c == null ? 0 : c.airFor(fuel, AIR_EXCESS));
        stove.stepSlow(slowDt, c, stove.mode() == StoveMode.ON_BLAST ? blastNm3S : 0, coldK, setK);
    }

    private Combustor combustor() {
        if (gas == null) return null;
        if (combustorGas != gas) {
            combustorGas = gas;
            combustor = gas.combustor(1);
        }
        return combustor;
    }

    /** Дутьё через нагреватель «на дутье»; назначает домна. */
    void assignBlast(double nm3S, double coldBlastK, double setpointK) {
        blastNm3S = Math.max(0, nm3S);
        coldK = coldBlastK;
        setK = setpointK;
    }

    /** Ожидаемая температура дутья на выходе «на дутье», К: верх насадки − 30 К, не выше уставки (смесительный клапан). */
    double deliverableK() {
        return Math.max(coldK, Math.min(setK, stove.domeK() - HotBlastStove.APPROACH_K));
    }

    /** Заявка на газ, нм³/с физ.: клапан открыт только «на газе». */
    public double gasDemandNm3S() {
        return stove.mode() == StoveMode.ON_GAS ? valveNm3S : 0;
    }

    public void setGasSupply(ProcessGas g, double nm3S) {
        gas = nm3S > 0 ? g : null;
        gasNm3S = Math.max(0, nm3S);
    }

    public void setValve(double nm3S) {
        valveNm3S = Math.max(0, Math.min(3, nm3S));
    }

    public double valveNm3S() {
        return valveNm3S;
    }

    public HotBlastStove stove() {
        return stove;
    }

    public double gasSupplyNm3S() {
        return gas == null ? 0 : gasNm3S;
    }

    @Override
    public void save(StateWriter out) {
        out.putDouble("valve_nm3s", valveNm3S);
        stove.save(out);
    }

    @Override
    public void load(StateReader in) {
        valveNm3S = in.getDouble("valve_nm3s");
        stove.load(in);
    }
}
