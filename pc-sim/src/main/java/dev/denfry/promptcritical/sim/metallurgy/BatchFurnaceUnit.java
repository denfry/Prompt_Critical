package dev.denfry.promptcritical.sim.metallurgy;

import dev.denfry.promptcritical.sim.combustion.Combustor;
import dev.denfry.promptcritical.sim.core.FastContext;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Двойник камерной печи прокатного и термического цеха ({@link BatchFurnace}) с регулятором: уставка кладки и
 * наибольшая скорость её нагрева, К/ч физ.; заявка на газ покрывает разгон кладки, потери и тепло садки.
 */
public final class BatchFurnaceUnit implements IronmakingUnit {
    /**
     * Исполнение печи.
     *
     * @param defaultSetpointK уставка по умолчанию, К
     * @param maxSetpointK     предельная уставка, К
     */
    public record Spec(int capacity, double heatCapacityJK, double lossWK, double efficiency, double defaultSetpointK,
            double maxSetpointK) {
        /** Методическая печь: 12 заготовок, 1230 °C, до 1350 °C; КПД 0,5 [BALANCE]. */
        public static final Spec REHEATING = new Spec(12, 3e7, 400, 0.5, 1503.15, 1623.15);
        /** Термическая печь: 8 мест, 900 °C, до 1100 °C [BALANCE]. */
        public static final Spec HEAT_TREATMENT = new Spec(8, 1.2e7, 200, 0.55, 1173.15, 1373.15);
    }

    public static final double DEFAULT_RAMP_K_H = 200, AIR_EXCESS = 1.1;
    private static final double T0 = 293.15, REG_TAU_S = 1_800;

    private final String deviceType;
    private final Spec spec;
    private final BatchFurnace furnace;
    private double setpointK, rampKH = DEFAULT_RAMP_K_H, demandW;
    private ProcessGas gas;
    private double gasNm3S;
    private Combustor combustor;
    private ProcessGas combustorGas;

    public BatchFurnaceUnit(String deviceType, Spec spec) {
        this.deviceType = deviceType;
        this.spec = spec;
        this.furnace = new BatchFurnace(spec.capacity(), spec.heatCapacityJK(), spec.lossWK(), spec.efficiency());
        this.setpointK = spec.defaultSetpointK();
    }

    @Override
    public String deviceType() {
        return deviceType;
    }

    @Override
    public void step(IronmakingSubsystem works, FastContext ctx, double slowDt) {
        if (slowDt <= 0) return;
        double t = furnace.tempK();
        double rate = Math.max(-rampKH / 3600, Math.min(rampKH / 3600, (setpointK - t) / REG_TAU_S));
        demandW = Math.max(0, spec.heatCapacityJK() * rate + spec.lossWK() * (t - T0)
                + Math.max(0, furnace.pieceHeatW()));
        Combustor c = combustor();
        double fuel = gas == null ? 0 : gasNm3S * gas.densityKgNm3;
        furnace.setHeating(fuel, c == null ? 0 : c.airFor(fuel, AIR_EXCESS));
        furnace.stepSlow(slowDt, c);
    }

    private Combustor combustor() {
        if (gas == null) return null;
        if (combustorGas != gas) {
            combustorGas = gas;
            combustor = gas.combustor(0.1);
        }
        return combustor;
    }

    /** Заявка регулятора на газ {@code g}, нм³/с физ. */
    public double gasDemandNm3S(ProcessGas g) {
        double perNm3 = spec.efficiency() * g.fuel.lhv() * g.densityKgNm3;
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
        setpointK = Math.max(T0, Math.min(spec.maxSetpointK(), tempK));
        rampKH = Math.max(5, Math.min(1000, rampKPerH));
    }

    public double setpointK() {
        return setpointK;
    }

    public double rampKH() {
        return rampKH;
    }

    public Spec spec() {
        return spec;
    }

    public BatchFurnace furnace() {
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
