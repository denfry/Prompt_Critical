package dev.denfry.promptcritical.sim.ops;

/**
 * Смазка узла (13 §13.6.7): доливка {@code oil_can} на ходу, интервал {@code lube.tallow_interval_h} у {@code tallow},
 * 2000 ч у масла (04a §4a.7.5). Счётчик идёт по наработке; израсходованная смазка — a_lub = 0,05, как сухой ход.
 */
public final class Lubrication implements FaultSource {
    public static final double OIL_INTERVAL_H = 2000;

    private final double tallowIntervalH;
    private Lubricant lubricant;
    private double hoursSince;

    /** @param initial смазка, с которой машина поставлена (заводская набивка) */
    public Lubrication(double tallowIntervalH, Lubricant initial) {
        if (!(tallowIntervalH > 0) || initial == null) throw new IllegalArgumentException("lubrication");
        this.tallowIntervalH = tallowIntervalH;
        this.lubricant = initial;
    }

    public void step(double dtPhysS) {
        if (dtPhysS > 0) hoursSince += dtPhysS / 3600;
    }

    /** Доливка или замена смазки. */
    public void apply(Lubricant l) {
        if (l == null) throw new IllegalArgumentException("lubricant");
        lubricant = l;
        hoursSince = 0;
    }

    public Lubricant lubricant() {
        return lubricant;
    }

    public double intervalH() {
        return switch (lubricant) {
            case NONE -> 0;
            case TALLOW -> tallowIntervalH;
            case LUBE_OIL -> OIL_INTERVAL_H;
        };
    }

    /** Остаток смазки r ∈ [0, 1]. */
    public double remaining() {
        return lubricant == Lubricant.NONE ? 0 : Math.clamp(1 - hoursSince / intervalH(), 0, 1);
    }

    /** a_lub; при сухом ходе насоса (без заливки) — 0,05 независимо от смазки. */
    public double factor(boolean dryRunning) {
        return dryRunning || remaining() <= 0 ? Lubricant.NONE.factor() : lubricant.factor();
    }

    @Override
    public MaintenanceFaultKind kind() {
        return MaintenanceFaultKind.LUBRICATION;
    }

    @Override
    public double value() {
        return remaining();
    }

    @Override
    public void repair() {
        apply(lubricant == Lubricant.NONE ? Lubricant.TALLOW : lubricant);
    }
}
