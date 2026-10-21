package dev.denfry.promptcritical.sim.ops;

/**
 * Коэффициенты вида неисправности (13 §13.6.5) [BALANCE].
 *
 * @param eff  e_k — η_eff = η·∏(1 − e_k·s_k)
 * @param trip c_k — λ_trip = λ₀·(1 + Σ c_k·s_k²)
 */
public record FaultCoefficients(double eff, double trip) {
    public FaultCoefficients {
        if (!(eff >= 0 && eff <= 1 && trip >= 0)) throw new IllegalArgumentException("coefficients");
    }
}
