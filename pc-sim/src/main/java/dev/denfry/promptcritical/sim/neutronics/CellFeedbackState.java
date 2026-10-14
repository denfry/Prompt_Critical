package dev.denfry.promptcritical.sim.neutronics;

/**
 * Состояние, от которого зависят константы ячейки (обратные связи, 01 §1.6.4).
 *
 * @param fuelEffTemp    T_eff топлива по Роуландсу, К
 * @param coolantDensity плотность теплоносителя, кг/м³ (из {@code FluidNode})
 * @param moderatorTemp  температура замедлителя, К (для LWR — теплоносителя); T_n = T_m + 50 К
 * @param boronPpm       бор в жидкой воде (теплоноситель и бак замедлителя), ppm по массе
 * @param xenonAbs       Σ_a Xe-135, усреднённое по узлу, см⁻¹
 * @param samariumAbs    Σ_a Sm-149, усреднённое по узлу, см⁻¹
 */
public record CellFeedbackState(double fuelEffTemp, double coolantDensity, double moderatorTemp, double boronPpm,
        double xenonAbs, double samariumAbs) {
    public CellFeedbackState {
        if (!(fuelEffTemp > 0)) throw new IllegalArgumentException("fuelEffTemp");
        if (!(coolantDensity >= 0)) throw new IllegalArgumentException("coolantDensity");
        if (!(moderatorTemp > 0)) throw new IllegalArgumentException("moderatorTemp");
        if (!(boronPpm >= 0)) throw new IllegalArgumentException("boronPpm");
        if (!(xenonAbs >= 0) || !(samariumAbs >= 0)) throw new IllegalArgumentException("poisons");
    }

    /** Эффективная температура топлива по Роуландсу: T_eff = 4/9 T_center + 5/9 T_surface. */
    public static double rowlands(double centerTemp, double surfaceTemp) {
        return 4.0 / 9 * centerTemp + 5.0 / 9 * surfaceTemp;
    }

    public CellFeedbackState withFuelTemp(double t) {
        return new CellFeedbackState(t, coolantDensity, moderatorTemp, boronPpm, xenonAbs, samariumAbs);
    }

    public CellFeedbackState withCoolantDensity(double rho) {
        return new CellFeedbackState(fuelEffTemp, rho, moderatorTemp, boronPpm, xenonAbs, samariumAbs);
    }

    public CellFeedbackState withModeratorTemp(double t) {
        return new CellFeedbackState(fuelEffTemp, coolantDensity, t, boronPpm, xenonAbs, samariumAbs);
    }

    public CellFeedbackState withBoron(double ppm) {
        return new CellFeedbackState(fuelEffTemp, coolantDensity, moderatorTemp, ppm, xenonAbs, samariumAbs);
    }
}
