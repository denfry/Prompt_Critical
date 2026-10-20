package dev.denfry.promptcritical.sim.automation;

/** Режим четвертьквадратного множителя A2 (03d §3d.4.5); формулы в вольтах. */
public enum MultiplierMode {
    /** y = x₁x₂/10. */
    MUL,
    /** y = 10x₁/x₂; при |x₂| &lt; 0,1 В — насыщение и UNCERTAIN. */
    DIV,
    /** y = x²/10. */
    SQUARE,
    /** y = √(10x) с отсечкой малого сигнала. */
    SQRT
}
