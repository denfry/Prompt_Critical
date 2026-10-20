package dev.denfry.promptcritical.sim.grid;

/**
 * Поколение реле (07 §7.5.2, дерево КИП 11): A1 — электромеханические (51, 50, 87 без гармонической
 * блокировки, 27/59, 81 с погрешностью ±0,1 Гц), A2 — статические (+21, +40, +78, блокировка по h₂,
 * ±0,02 Гц), A3 — микропроцессорные терминалы.
 */
public enum RelayGeneration {
    A1_ELECTROMECHANICAL(0.1), A2_STATIC(0.02), A3_NUMERIC(0.005);

    /** Погрешность реле частоты, Гц: смещение уставки, разыгранное при установке. */
    public final double frequencyErrorHz;

    RelayGeneration(double frequencyErrorHz) {
        this.frequencyErrorHz = frequencyErrorHz;
    }
}
