package dev.denfry.promptcritical.sim.waste;

import dev.denfry.promptcritical.sim.radiation.Emitter;

/**
 * Свойства γ-излучателей {@link Emitter} для отходов: элемент (миграция), дозовый коэффициент при поступлении с водой
 * и пищей для взрослых (ICRP Publication 72, Зв/Бк) и принадлежность к трансурановым.
 *
 * <p>Y-90 в равновесии с Sr-90 входит в коэффициент Sr-90 (ICRP 72 учитывает дочерний в теле), Nb-95 — отдельно.
 * Kr-85 и N-16 — газ и активационный нуклид теплоносителя: в воду не переходят, доза 0.
 */
public final class EmitterData {
    private EmitterData() {
    }

    public static Element element(Emitter e) {
        return switch (e) {
            case CO60 -> Element.CO;
            case CS137, CS134 -> Element.CS;
            case I131 -> Element.I;
            case Y90 -> Element.Y;
            case SR90 -> Element.SR;
            case NB95 -> Element.NB;
            case ZR95 -> Element.ZR;
            case RU106 -> Element.RU;
            case KR85 -> Element.KR;
            case IR192 -> Element.IR;
            case PU238, PU241 -> Element.PU;
            case AM241 -> Element.AM;
            case CM244 -> Element.AM; // трёхвалентный актинид: подвижность в барьерах как у америция
            case N16 -> Element.N;
        };
    }

    /** Дозовый коэффициент, Зв/Бк. */
    public static double doseSvPerBq(Emitter e) {
        return switch (e) {
            case CO60 -> 3.4e-9;
            case CS137 -> 1.3e-8;
            case CS134 -> 1.9e-8;
            case I131 -> 2.2e-8;
            case Y90 -> 0; // в коэффициенте Sr-90
            case SR90 -> 2.8e-8;
            case NB95 -> 5.8e-10;
            case ZR95 -> 9.5e-10;
            case RU106 -> 7.0e-9;
            case IR192 -> 1.4e-9;
            case PU238 -> 2.3e-7;
            case PU241 -> 4.8e-9;
            case AM241 -> 2.0e-7;
            case CM244 -> 1.2e-7;
            case KR85, N16 -> 0;
        };
    }

    public static boolean transuranic(Emitter e) {
        return e == Emitter.PU238 || e == Emitter.PU241 || e == Emitter.AM241 || e == Emitter.CM244;
    }

    /** Период полураспада, годы. */
    public static double halfLifeY(Emitter e) {
        return e.halfLifeS / LongLivedNuclide.YEAR_S;
    }
}
