package dev.denfry.promptcritical.sim.uranium;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Осаждение диураната аммония («жёлтого кека») из элюата аммиаком (Скороваров, 1979, гл. 8; Merritt, гл. 8;
 * Benedict–Pigford–Levi, гл. 5): 2 UO₂²⁺ + 6 NH₃ + 3 H₂O → (NH₄)₂U₂O₇ + 4 NH₄⁺ при pH 7–8 и 40–60 °C. Аммиак идёт
 * и на нейтрализацию свободной кислоты элюата; всего {@value #NH3_KG_PER_KG_U} кг NH₃ на кг U (0,21 — на сам ДУА).
 * Недодозировка аммиака — pH ниже 6,5, уран остаётся в маточнике. При полной дозе маточник уносит 0,3–0,5 % урана.
 */
public final class AduPrecipitation {
    public static final double NH3_KG_PER_KG_U = 0.55, RECOVERY = 0.996;
    /** Масса ДУА на кг U: (NH₄)₂U₂O₇ — 624,1 г на 2 атома U. */
    public static final double ADU_KG_PER_KG_U = 624.13 / (2 * UraniumLedger.M_U);

    private final UraniumLedger ledger = new UraniumLedger();
    private double lastRecovery;

    public UraniumLedger ledger() {
        return ledger;
    }

    /** Извлечение в осадок при доле нормы аммиака: ниже нормы pH не дотягивает до 7. */
    public static double recovery(double ammoniaFraction) {
        double f = Math.clamp(ammoniaFraction, 0, 1);
        return RECOVERY * Math.pow(f, 3);
    }

    /**
     * Осаждение.
     *
     * @return уран в осадке, кг
     */
    public double step(double eluateKgU, double ammoniaKg) {
        if (eluateKgU <= 0) return 0;
        ledger.in(eluateKgU);
        lastRecovery = recovery(ammoniaKg / (eluateKgU * NH3_KG_PER_KG_U));
        double cake = eluateKgU * lastRecovery;
        ledger.out(cake);
        ledger.loss(eluateKgU - cake);
        return cake;
    }

    public double lastRecovery() {
        return lastRecovery;
    }

    public void save(StateWriter out) {
        ledger.save(out);
    }

    public void load(StateReader in) {
        ledger.load(in);
    }
}
