package dev.denfry.promptcritical.sim.metallurgy;

import dev.denfry.promptcritical.sim.combustion.BurnerKind;
import dev.denfry.promptcritical.sim.combustion.BurnerSpec;
import dev.denfry.promptcritical.sim.combustion.Combustor;
import dev.denfry.promptcritical.sim.combustion.FuelGas;
import dev.denfry.promptcritical.sim.combustion.FuelSpec;

/**
 * Горючие газы завода (07 §7.8.1, 08 §8.4–8.5) — топливо обогрева батареи, кауперов, обжиговой печи и зажигательного
 * горна агломашины. В мире газ течёт по трубам Create: 1 мБ = 1 нм³ (моё). Колошниковый газ — типичный состав
 * (CO 22, CO₂ 22, H₂ 3, N₂ 53 %, ≈ 3,1 МДж/нм³); фактический состав печи в трубы не переносится.
 */
public enum ProcessGas {
    COKE_OVEN("coke_oven_gas", CokeBattery.COKE_OVEN_GAS, BurnerKind.GAS),
    BLAST_FURNACE("blast_furnace_gas", blastFurnaceGas(), BurnerKind.LOW_BTU_GAS),
    /** Каптированный шахтный метан (08 §8.2.6), CH₄ 95 %. */
    MINE_METHANE("mine_methane", mineMethane(), BurnerKind.GAS);

    public final String fluidId;
    public final FuelSpec fuel;
    /** Плотность при н. у., кг/нм³. */
    public final double densityKgNm3;
    private final BurnerKind burner;

    ProcessGas(String fluidId, FuelSpec fuel, BurnerKind burner) {
        this.fluidId = fluidId;
        this.fuel = fuel;
        this.burner = burner;
        this.densityKgNm3 = FuelGas.mixtureDensityKgPerNm3(composition(fluidId));
    }

    /** Горелка на этом газе с номинальным расходом, кг/с. */
    public Combustor combustor(double nominalFuelKgS) {
        return new Combustor(fuel, BurnerSpec.of(burner, nominalFuelKgS));
    }

    /** Газ по id жидкости; {@code null} — не горючий газ завода. */
    public static ProcessGas byFluid(String fluidId) {
        for (ProcessGas g : values()) if (g.fluidId.equals(fluidId)) return g;
        return null;
    }

    private static double[] composition(String id) {
        double[] x = new double[FuelGas.values().length];
        switch (id) {
            case "coke_oven_gas" -> {
                x[FuelGas.H2.ordinal()] = 0.58;
                x[FuelGas.CH4.ordinal()] = 0.26;
                x[FuelGas.CO.ordinal()] = 0.06;
                x[FuelGas.C2H4.ordinal()] = 0.03;
                x[FuelGas.CO2.ordinal()] = 0.02;
                x[FuelGas.N2.ordinal()] = 0.05;
            }
            case "blast_furnace_gas" -> {
                x[FuelGas.CO.ordinal()] = 0.22;
                x[FuelGas.CO2.ordinal()] = 0.22;
                x[FuelGas.H2.ordinal()] = 0.03;
                x[FuelGas.N2.ordinal()] = 0.53;
            }
            default -> {
                x[FuelGas.CH4.ordinal()] = 0.95;
                x[FuelGas.N2.ordinal()] = 0.04;
                x[FuelGas.CO2.ordinal()] = 0.01;
            }
        }
        return x;
    }

    private static FuelSpec blastFurnaceGas() {
        return FuelSpec.fromGasComposition("blast_furnace_gas", composition("blast_furnace_gas"));
    }

    private static FuelSpec mineMethane() {
        return FuelSpec.fromGasComposition("mine_methane", composition("mine_methane"));
    }
}
