package dev.denfry.promptcritical.sim.ops;

/**
 * Физиологическое действие атмосферы замкнутого пространства на человека (13 §13.2.6, пороги OSHA/NIOSH [сверить]).
 * Стадии возвращаются числом 0…4; что делает каждая с игроком — таблица §13.2.6.
 *
 * <ul>
 *   <li>O₂: 0 — от {@value ConfinedSpaceAtmosphere#O2_SAFE}; 1 — 16–19,5 % (замедление I); 2 — 12–16 % (замедление II,
 *   тошнота); 3 — 8–12 % (слепота, слабость, 1 урона/с); 4 — ниже 8 % (потеря сознания через
 *   {@value #O2_FAINT_S} с, 2 урона/с).</li>
 *   <li>H₂S: 1 — 10–100 ppm (тошнота); 2 — 100–500 ppm (паралич обоняния, яд); 3 — 500–1000 ppm (потеря сознания
 *   через {@value #H2S_FAINT_S} с); 4 — выше 1000 ppm (сразу, 4 урона/с).</li>
 *   <li>CO — через карбоксигемоглобин: dCOHb/dt = (COHb_eq − COHb)/τ, COHb_eq = M·x_CO/x_O₂ / (1 + M·x_CO/x_O₂),
 *   M = {@value #HALDANE} (константа Холдейна), τ = {@value #CO_TAU_S} с [BALANCE, в жизни — часы]; 1 — 10–30 %
 *   (тошнота), 2 — 30–40 % (замедление), 3 — 40–60 % (потеря сознания), 4 — выше 60 % (смерть).</li>
 * </ul>
 */
public final class AtmosphereHazard {
    public static final double HALDANE = 240, CO_TAU_S = 600;
    public static final double O2_FAINT_S = 10, H2S_FAINT_S = 30;

    private AtmosphereHazard() {
    }

    public static int o2Stage(double o2) {
        if (o2 >= ConfinedSpaceAtmosphere.O2_SAFE) return 0;
        if (o2 >= 0.16) return 1;
        if (o2 >= 0.12) return 2;
        if (o2 >= 0.08) return 3;
        return 4;
    }

    public static int h2sStage(double ppm) {
        if (ppm < 10) return 0;
        if (ppm < 100) return 1;
        if (ppm < 500) return 2;
        if (ppm <= 1000) return 3;
        return 4;
    }

    /** Равновесный карбоксигемоглобин при долях CO и O₂. */
    public static double cohbEquilibrium(double xCo, double xO2) {
        if (xCo <= 0) return 0;
        double r = HALDANE * xCo / Math.max(xO2, 1e-3);
        return r / (1 + r);
    }

    /** COHb через {@code dtS} секунд дыхания воздухом с долями CO и O₂ (точное решение релаксации). */
    public static double cohbStep(double cohb, double xCo, double xO2, double dtS) {
        double eq = cohbEquilibrium(xCo, xO2);
        return eq + (cohb - eq) * Math.exp(-dtS / CO_TAU_S);
    }

    public static int cohbStage(double cohb) {
        if (cohb < 0.10) return 0;
        if (cohb < 0.30) return 1;
        if (cohb < 0.40) return 2;
        if (cohb <= 0.60) return 3;
        return 4;
    }
}
