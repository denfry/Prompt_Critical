package dev.denfry.promptcritical.sim.thermo;

/** Полный набор свойств на линии насыщения при давлении {@code pressure} (01 §1.3.2(а)). Единицы — СИ. */
public record SaturationState(double pressure, double tSat, double vF, double vG,
        double hF, double hG, double uF, double uG, double sF, double sG,
        double cpF, double cpG, double muF, double muG, double kF, double kG,
        double sigma, double dTsatdP) {

    public double rhoF() {
        return 1.0 / vF;
    }

    public double rhoG() {
        return 1.0 / vG;
    }

    /** Теплота парообразования h'' − h', Дж/кг. */
    public double latentHeat() {
        return hG - hF;
    }
}
