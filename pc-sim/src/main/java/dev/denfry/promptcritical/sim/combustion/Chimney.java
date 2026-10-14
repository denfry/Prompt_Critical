package dev.denfry.promptcritical.sim.combustion;

/** Естественная тяга дымовой трубы (07 §7.9.4): Δp = g·H·(ρ_возд − ρ_газ), плотности — идеальный газ. */
public final class Chimney {
    public static final double GRAVITY = 9.80665;
    public static final double GAS_CONSTANT = 8.314462618;
    /** Молярная масса сухого воздуха, кг/моль. */
    public static final double AIR_MOLAR_MASS = 0.0289647;

    private Chimney() {}

    /**
     * Тяга, Па (отрицательна, если газы в трубе тяжелее наружного воздуха — «опрокидывание» тяги).
     *
     * @param heightM      высота трубы H, м
     * @param ambientK     температура наружного воздуха, К
     * @param ambientPa    атмосферное давление, Па
     * @param gasK         средняя температура газов в трубе, К
     * @param gasMolarMass молярная масса газов, кг/моль
     */
    public static double draftPa(double heightM, double ambientK, double ambientPa, double gasK, double gasMolarMass) {
        double rhoAir = ambientPa * AIR_MOLAR_MASS / (GAS_CONSTANT * ambientK);
        double rhoGas = ambientPa * gasMolarMass / (GAS_CONSTANT * gasK);
        return GRAVITY * heightM * (rhoAir - rhoGas);
    }
}
