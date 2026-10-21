package dev.denfry.promptcritical.sim.metallurgy;

import dev.denfry.promptcritical.sim.combustion.Combustor;
import dev.denfry.promptcritical.sim.core.FastContext;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Двойник обжиговой печи электродов {@code baking_furnace} (08 §8.4.5): {@link BakingFurnace} на 16 электродов и
 * регулятор: уставка и наибольшая скорость нагрева, К/ч физ. (моё; печь сама не знает о зоне трещин 300–600 °C —
 * скорость выбирает игрок).
 */
public final class BakingFurnaceUnit implements IronmakingUnit {
    public static final int CAPACITY = 16;
    public static final double DEFAULT_SETPOINT_K = 1173.15, DEFAULT_RAMP_K_H = 8, AIR_EXCESS = 1.15;
    private static final double T0 = 293.15, REG_TAU_S = 3_600;

    private final BakingFurnace furnace = new BakingFurnace(CAPACITY);
    private double setpointK = DEFAULT_SETPOINT_K, rampKH = DEFAULT_RAMP_K_H, demandW;
    private ProcessGas gas;
    private double gasNm3S;
    private Combustor combustor;
    private ProcessGas combustorGas;

    @Override
    public String deviceType() {
        return IronmakingSubsystem.BAKING_FURNACE;
    }

    @Override
    public void step(IronmakingSubsystem works, FastContext ctx, double slowDt) {
        if (slowDt <= 0) return;
        double t = furnace.tempK();
        double rate = Math.max(-rampKH / 3600, Math.min(rampKH / 3600, (setpointK - t) / REG_TAU_S));
        demandW = Math.max(0, BakingFurnace.HEAT_CAPACITY_J_K * rate + BakingFurnace.LOSS_W_K * (t - T0));
        Combustor c = combustor();
        double fuel = gas == null ? 0 : gasNm3S * gas.densityKgNm3;
        furnace.setHeating(fuel, c == null ? 0 : c.airFor(fuel, AIR_EXCESS));
        furnace.stepSlow(slowDt, c);
    }

    private Combustor combustor() {
        if (gas == null) return null;
        if (combustorGas != gas) {
            combustorGas = gas;
            combustor = gas.combustor(0.05);
        }
        return combustor;
    }

    /** Заявка регулятора на газ {@code g}, нм³/с физ. */
    public double gasDemandNm3S(ProcessGas g) {
        double perNm3 = BakingFurnace.EFFICIENCY * g.fuel.lhv() * g.densityKgNm3;
        return perNm3 > 0 ? demandW / perNm3 : 0;
    }

    public void setGasSupply(ProcessGas g, double nm3S) {
        gas = nm3S > 0 ? g : null;
        gasNm3S = Math.max(0, nm3S);
    }

    /** Горелки горят (газ подан). */
    public boolean gasSupplyLit() {
        return gas != null && gasNm3S > 0;
    }

    public void setSetpoint(double tempK, double rampKPerH) {
        setpointK = Math.max(T0, Math.min(1373.15, tempK));
        rampKH = Math.max(0.5, Math.min(200, rampKPerH));
    }

    public double setpointK() {
        return setpointK;
    }

    public double rampKH() {
        return rampKH;
    }

    public BakingFurnace furnace() {
        return furnace;
    }

    @Override
    public void save(StateWriter out) {
        out.putDouble("setpoint_k", setpointK);
        out.putDouble("ramp_k_h", rampKH);
        furnace.save(out);
    }

    @Override
    public void load(StateReader in) {
        setpointK = in.getDouble("setpoint_k");
        rampKH = in.getDouble("ramp_k_h");
        furnace.load(in);
    }
}
