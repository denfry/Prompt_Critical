package dev.denfry.promptcritical.sim.drilling;

/**
 * Коллектор под конкретной скважиной: ловушка и глубина кровли пласта в точке бурения.
 *
 * @param trap ловушка клетки
 * @param topM глубина кровли коллектора под устьем, м
 */
public record Prospect(Trap trap, double topM) {
    public Prospect {
        if (trap == null || !(topM > 0)) throw new IllegalArgumentException("prospect needs a trap and a positive top depth");
    }

    /** Подошва коллектора, м. */
    public double baseM() {
        return topM + trap.thicknessM();
    }

    /** Глубина внутри коллектора. */
    public boolean contains(double depthM) {
        return depthM >= topM && depthM <= baseM();
    }
}
