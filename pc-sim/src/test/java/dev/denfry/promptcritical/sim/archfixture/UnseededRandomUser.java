package dev.denfry.promptcritical.sim.archfixture;

/** Нарушитель правила noUnseededRandom. */
public final class UnseededRandomUser {
    public double roll() {
        return Math.random();
    }
}
