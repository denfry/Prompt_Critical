package dev.denfry.promptcritical.sim.ops;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Сальник штока машины или вала насоса (13 §13.6.7): износ набивки w от наработки, ×10 на сухом ходу (13 §13.6.6,
 * {@code DRY_PUMP}). Подтяжка {@code torque_wrench} на ходу снимает половину износа, не больше трёх раз; замена
 * {@code gland_packing} — на остановленной машине. Ресурс и протечка [BALANCE, моё].
 */
public final class GlandPacking implements FaultSource {
    /** Ресурс набивки до w = 1, ч физики. */
    public static final double LIFE_H = 3000;
    public static final double DRY_FACTOR = 10;
    public static final int MAX_TIGHTENINGS = 3;
    /** Протечка при w = 1, кг/с; растёт как w². */
    public static final double FULL_LEAK_KG_S = 0.05;

    private double wear;
    private int tightenings;

    public void step(double dtPhysS, boolean dryRunning) {
        if (!(dtPhysS > 0)) return;
        wear = Math.min(1, wear + dtPhysS / 3600 / LIFE_H * (dryRunning ? DRY_FACTOR : 1));
    }

    public double wear() {
        return wear;
    }

    public int tightenings() {
        return tightenings;
    }

    public double leakKgS() {
        return FULL_LEAK_KG_S * wear * wear;
    }

    /** Подтяжка: w ×0,5; false — набивка выбрана, нужна замена. */
    public boolean tighten() {
        if (tightenings >= MAX_TIGHTENINGS) return false;
        wear *= 0.5;
        tightenings++;
        return true;
    }

    public void replace() {
        wear = 0;
        tightenings = 0;
    }

    public void save(StateWriter out) {
        out.putDouble("wear", wear);
        out.putInt("tightenings", tightenings);
    }

    public void load(StateReader in) {
        wear = in.getDouble("wear");
        tightenings = in.getInt("tightenings");
    }

    @Override
    public MaintenanceFaultKind kind() {
        return MaintenanceFaultKind.PACKING_LEAK;
    }

    @Override
    public double value() {
        return wear;
    }

    @Override
    public void repair() {
        replace();
    }
}
