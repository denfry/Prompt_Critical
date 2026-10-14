package dev.denfry.promptcritical.sim.neutronics;

import java.util.EnumMap;

/**
 * Баланс реактивности (01 §1.7.1): компоненты в Δk/k и их сумма
 * ρ_total = STATIC + RODS + DOPPLER + MODERATOR_TEMP + COOLANT_DENSITY + BORON + EXTERNAL —
 * то, что видит кинетика и тег {@code core.reactivity.total}. XENON и SAMARIUM — только
 * раскрытие STATIC для HMI.
 */
public final class ReactivityBudget {
    public static final double PCM = 1e-5;
    private static final ReactivityComponent[] ALL = ReactivityComponent.values();

    private final double[] value = new double[ALL.length];
    private double total;

    public double total() {
        return total;
    }

    public double component(ReactivityComponent c) {
        return value[c.ordinal()];
    }

    public double dollars(double betaEff) {
        return total / betaEff;
    }

    public double totalPcm() {
        return total / PCM;
    }

    public void set(ReactivityComponent c, double rho) {
        if (!Double.isFinite(rho)) throw new IllegalArgumentException(c + " = " + rho);
        value[c.ordinal()] = rho;
        recompute();
    }

    /** Новый STATIC на SLOW-шаге: квазистатические поправки отсчитываются заново. */
    public void resetFastComponents() {
        for (ReactivityComponent c : ALL) if (c.fast()) value[c.ordinal()] = 0;
        recompute();
    }

    private void recompute() {
        double s = 0;
        for (ReactivityComponent c : ALL) if (c.summed()) s += value[c.ordinal()];
        total = s;
    }

    /** Копия для HMI и журнала — вне горячего цикла. */
    public EnumMap<ReactivityComponent, Double> snapshot() {
        EnumMap<ReactivityComponent, Double> m = new EnumMap<>(ReactivityComponent.class);
        for (ReactivityComponent c : ALL) m.put(c, value[c.ordinal()]);
        return m;
    }
}
