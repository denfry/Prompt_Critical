package dev.denfry.promptcritical.sim.mining;

/**
 * Коэффициент аэродинамического сопротивления α (Аткинсон), Н·с²/м⁴, по виду крепи (08 §8.2.7; середины
 * диапазонов [сверить]).
 */
public enum AirwayLining {
    CONCRETE(0.003),
    /** без крепи, по породе */
    ROCK(0.012),
    /** деревянная рамная крепь: 0,015–0,020 */
    TIMBER(0.0175),
    /** арочная податливая: 0,010–0,015 */
    ARCH(0.0125),
    /** лава с механизированной крепью: 0,03–0,05 */
    POWERED_FACE(0.04);

    public final double alpha;

    AirwayLining(double alpha) {
        this.alpha = alpha;
    }
}
