package dev.denfry.promptcritical.sim.manufacturing;

/** Цвета каления и побежалости по температуре (таблицы справочника кузнеца), К. */
public final class HeatColors {
    private HeatColors() {}

    /** Цвет каления; нижние границы 823,15 … 1473,15 К. Источник: таблица цветов «Справочник кузнеца» [сверить]. */
    public enum Glow {
        NONE(0), DARK_RED(823.15), CHERRY(1023.15), LIGHT_CHERRY(1073.15), ORANGE(1173.15),
        YELLOW(1323.15), WHITE(1473.15);

        public final double lowerK;

        Glow(double lowerK) {
            this.lowerK = lowerK;
        }
    }

    /** Цвет побежалости; нижние границы 493,15 … 603,15 К. Источник: стандартная таблица цветов побежалости [сверить]. */
    public enum Temper {
        NONE(0), LIGHT_STRAW(493.15), STRAW(513.15), BROWN(528.15), PURPLE(543.15), BLUE(563.15),
        LIGHT_BLUE(588.15), GRAY(603.15);

        public final double lowerK;

        Temper(double lowerK) {
            this.lowerK = lowerK;
        }
    }

    public static Glow glow(double tK) {
        Glow r = Glow.NONE;
        for (Glow g : Glow.values()) {
            if (g != Glow.NONE && tK >= g.lowerK) {
                r = g;
            }
        }
        return r;
    }

    public static Temper temper(double tK) {
        Temper r = Temper.NONE;
        for (Temper t : Temper.values()) {
            if (t != Temper.NONE && tK >= t.lowerK) {
                r = t;
            }
        }
        return r;
    }
}
