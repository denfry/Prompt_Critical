package dev.denfry.promptcritical.sim.ops;

import java.util.SplittableRandom;

/** Множитель ресурса экземпляра u_b (04a §4a.7.2): Вейбулл β = 1,5 с масштабом, при котором P(u_b &lt; 1) = 0,1. */
public final class Weibull {
    public static final double BEARING_BETA = 1.5;

    private Weibull() {
    }

    /** η, при котором P(X &lt; 1) = 0,1: η = (1/ln(1/0,9))^(1/β). */
    public static double scaleForL10(double beta) {
        return Math.pow(1 / Math.log(1 / 0.9), 1 / beta);
    }

    public static double sample(SplittableRandom rnd, double beta, double scale) {
        return scale * Math.pow(-Math.log(1 - rnd.nextDouble()), 1 / beta);
    }

    /** u_b нового подшипника. */
    public static double bearingLifeFactor(SplittableRandom rnd) {
        return sample(rnd, BEARING_BETA, scaleForL10(BEARING_BETA));
    }
}
