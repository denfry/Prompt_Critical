package dev.denfry.promptcritical.sim.drilling;

/**
 * Пластовый флюид ловушки. Вязкость и плотность — в пластовых условиях на глубинах 0,6–2,5 км: лёгкая нефть
 * 1,5 мПа·с и 750 кг/м³, пластовая вода 0,5 мПа·с (около 60 °C) и 1030 кг/м³, природный газ 0,02 мПа·с
 * (McCain, «The Properties of Petroleum Fluids», 2-е изд., гл. 3, 6, 16); плотность газа считается по давлению
 * ({@link NaturalGas}).
 */
public enum FormationFluid {
    GAS(2.0e-5, Double.NaN),
    OIL(1.5e-3, 750),
    WATER(5.0e-4, 1030);

    /** Динамическая вязкость в пласте, Па·с. */
    public final double viscosityPaS;
    /** Плотность жидкости в пласте, кг/м³; у газа — NaN, см. {@link NaturalGas#densityKgM3}. */
    public final double liquidDensityKgM3;

    FormationFluid(double viscosityPaS, double liquidDensityKgM3) {
        this.viscosityPaS = viscosityPaS;
        this.liquidDensityKgM3 = liquidDensityKgM3;
    }

    public boolean hydrocarbon() {
        return this != WATER;
    }

    /** Плотность при абсолютном давлении и температуре, кг/м³. */
    public double densityKgM3(double pressureAbsPa, double temperatureK) {
        return this == GAS ? NaturalGas.densityKgM3(pressureAbsPa, temperatureK) : liquidDensityKgM3;
    }
}
