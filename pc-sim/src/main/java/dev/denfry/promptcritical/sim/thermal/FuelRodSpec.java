package dev.denfry.promptcritical.sim.thermal;

/**
 * Геометрия ТВЭЛа (01 §1.13.2). Единицы — СИ.
 *
 * @param pelletRings                 кольца равной площади в таблетке (3..5, {@code thermal.fuel_radial_nodes})
 * @param fillGasPressure             давление заполнения гелием при 20 °C, Па
 * @param theoreticalDensityFraction  плотность таблетки в долях теоретической (пористость 1 − TD)
 */
public record FuelRodSpec(double pelletRadius, double cladInnerRadius, double cladOuterRadius,
        double activeLength, int pelletRings, double fillGasPressure, double theoreticalDensityFraction) {
    public FuelRodSpec {
        if (!(pelletRadius > 0 && cladInnerRadius >= pelletRadius && cladOuterRadius > cladInnerRadius)) {
            throw new IllegalArgumentException("radii");
        }
        if (!(activeLength > 0)) throw new IllegalArgumentException("activeLength");
        if (pelletRings < 3 || pelletRings > 5) throw new IllegalArgumentException("pelletRings: 3..5");
        if (!(fillGasPressure > 0)) throw new IllegalArgumentException("fillGasPressure");
        if (!(theoreticalDensityFraction > 0.8 && theoreticalDensityFraction <= 1)) {
            throw new IllegalArgumentException("theoreticalDensityFraction");
        }
    }

    /** ТВЭЛ ПР-300 (§1.14.1, §1.4.6): таблетка ⌀7,57 мм, оболочка ⌀9,10×0,69 мм, 1 м, He 2 МПа, 95 % ТП. */
    public static FuelRodSpec pr300(int pelletRings) {
        return new FuelRodSpec(3.785e-3, 3.86e-3, 4.55e-3, 1.0, pelletRings, 2.0e6, 0.95);
    }

    public double porosity() {
        return 1 - theoreticalDensityFraction;
    }
}
