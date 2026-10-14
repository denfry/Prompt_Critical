package dev.denfry.promptcritical.sim.thermo;

import java.util.Objects;

/**
 * Вода и пар (01 §1.3): обёртка над таблицами IF97 и таблицей критического расхода.
 * Бор и растворённые газы — пассивные скаляры узла, на EOS не влияют, поэтому
 * {@link #componentCount()} = 0 и массив долей не читается.
 */
public final class WaterPackage implements FluidPackage {
    private final WaterProperties water;
    private final CriticalFlowTable criticalFlow;

    public WaterPackage(WaterProperties water, CriticalFlowTable criticalFlow) {
        this.water = Objects.requireNonNull(water, "water");
        this.criticalFlow = Objects.requireNonNull(criticalFlow, "criticalFlow");
    }

    /** Таблицы IF97 и модель критического расхода {@code hydraulics.critical_flow_model}. */
    public static WaterPackage of(CriticalFlowTable.Model model) {
        return new WaterPackage(WaterProperties.tables(), CriticalFlowTable.of(model));
    }

    public WaterProperties water() {
        return water;
    }

    public CriticalFlowTable criticalFlow() {
        return criticalFlow;
    }

    @Override
    public String id() {
        return "water";
    }

    @Override
    public int componentCount() {
        return 0;
    }

    @Override
    public void stateFromMassEnergy(double density, double specificEnergy, double[] massFractions, int offset,
            double pGuess, ThermoState out) {
        water.stateFromMassEnergy(density, specificEnergy, pGuess, out);
    }

    @Override
    public void stateFromPressureEnthalpy(double p, double h, double[] massFractions, int offset, ThermoState out) {
        water.stateFromPressureEnthalpy(p, h, out);
    }

    @Override
    public double criticalMassFlux(double p0, double h0, double[] massFractions, int offset) {
        return criticalFlow.criticalMassFlux(p0, h0);
    }

    @Override
    public double soundSpeed(double p, double h, double[] massFractions, int offset) {
        return water.soundSpeed(p, h);
    }

    @Override
    public long familyKey(double[] massFractions, int offset) {
        return 0;
    }
}
