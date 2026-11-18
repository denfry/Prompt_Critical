package dev.denfry.promptcritical.sim.hazard;

import java.util.SplittableRandom;

/**
 * Воспламенение утечки (09 §9.11.3). Вероятность по расходу — Cox, Lees &amp; Ang (1990); доля «немедленного»
 * воспламенения {@value #IMMEDIATE_FRACTION} (струйный пожар или пожар пролива, облака нет), остальное — «задержанное»:
 * облако успевает накопиться, и его поджигает источник внутри (взрыв или пожар-вспышка).
 */
public final class Ignition {
    public static final double IMMEDIATE_FRACTION = 0.4;

    /** Источник зажигания в облаке и вероятность поджечь его за секунду (§9.11.3). */
    public enum Source {
        /** Открытый огонь, лава, работающие печи и факелы. */
        OPEN_FLAME(1.0),
        /** Искрящие механизмы Create и электрические машины 07 без взрывозащиты [BALANCE]. */
        SPARKING(0.1),
        /** Редстоун под током [BALANCE]. */
        REDSTONE(0.02);

        public final double perSecond;

        Source(double perSecond) {
            this.perSecond = perSecond;
        }
    }

    /** Итог броска при начале утечки. */
    public enum Outcome { IMMEDIATE, DELAYED, NONE }

    private Ignition() {
    }

    /** Полная вероятность воспламенения утечки с расходом {@code kgS} (таблица §9.11.3). */
    public static double probability(double kgS, boolean gas) {
        if (kgS < 1) return 0.01;
        if (kgS <= 50) return gas ? 0.07 : 0.03;
        return gas ? 0.30 : 0.08;
    }

    /**
     * Бросок при начале утечки: немедленное воспламенение с вероятностью P·0,4; иначе облако копится, и его судьбу решают
     * источники зажигания в нём ({@link #ignitesThisSecond}). «Задержанное» без источников — {@link Outcome#DELAYED}
     * означает, что облако воспламенится само при первом же источнике.
     */
    public static Outcome roll(double kgS, boolean gas, SplittableRandom rnd) {
        double p = probability(kgS, gas);
        double u = rnd.nextDouble();
        if (u < p * IMMEDIATE_FRACTION) return Outcome.IMMEDIATE;
        if (u < p) return Outcome.DELAYED;
        return Outcome.NONE;
    }

    /** Облако с источником {@code s} внутри загорится в эту секунду. */
    public static boolean ignitesThisSecond(Source s, SplittableRandom rnd) {
        return rnd.nextDouble() < s.perSecond;
    }
}
