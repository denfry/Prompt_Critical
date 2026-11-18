package dev.denfry.promptcritical.sim.petrochem;

/**
 * Фенолоформальдегидная смола для гетинакса печатных плат (Ullmann's, «Formaldehyde» и «Phenolic Resins»;
 * Knop, Pilato, «Phenolic Resins»).
 *
 * <p><b>Формальдегид из метанола.</b> Серебряный процесс (600–650 °C, недостаток воздуха): CH₃OH → HCHO + H₂ и
 * CH₃OH + ½O₂ → HCHO + H₂O, выход {@link #SILVER_YIELD} моль/моль. Процесс Formox на железомолибденовом катализаторе
 * (250–400 °C, избыток воздуха): только окисление, выход {@link #FORMOX_YIELD}. Продукт — формалин 37 %.
 *
 * <p><b>Поликонденсация.</b> Фенол + n HCHO → смола + n H₂O: каждая метиленовая связь даёт молекулу воды, масса смолы
 * на моль фенола = 94,11 + r·(30,03 − 18,02), r — мольное отношение F : P. Новолак (кислый катализатор, r 0,75–0,85)
 * — термопласт, отверждается уротропином; по Карозерсу при полном расходе формальдегида среднечисловая степень
 * поликонденсации DPn = 1/(1 − r) (r = 0,8 → 5 ядер, M ≈ 520, типично 500–900). Резол (щелочной катализатор,
 * r 1,0–3,0) — сам отверждается при нагреве, им пропитывают бумагу гетинакса.
 */
public final class PhenolFormaldehyde {
    public static final double M_PHENOL = 94.11, M_HCHO = 30.03, M_H2O = 18.02, M_METHANOL = 32.04;
    public static final double SILVER_YIELD = 0.89, FORMOX_YIELD = 0.92;

    public enum Catalyst { SILVER, FE_MO }

    public enum ResinType { NOVOLAC, RESOL }

    /** @param resinKg смола на кг фенола; waterKg вода конденсации на кг фенола; dpn среднее число фенольных ядер. */
    public record Resin(ResinType type, double resinKg, double waterKg, double formaldehydeKg, double dpn) {
    }

    private PhenolFormaldehyde() {
    }

    /** Формальдегид, кг на кг метанола. */
    public static double formaldehydePerMethanol(Catalyst c) {
        return (c == Catalyst.SILVER ? SILVER_YIELD : FORMOX_YIELD) * M_HCHO / M_METHANOL;
    }

    /**
     * @param ratio мольное отношение формальдегид : фенол
     * @param basic щелочной катализатор (резол) или кислый (новолак)
     */
    public static Resin condense(double ratio, boolean basic) {
        double r = Math.max(0.1, Math.min(3, ratio));
        ResinType type = basic && r >= 1 ? ResinType.RESOL : ResinType.NOVOLAC;
        // в новолаке реагирует весь формальдегид; в резоле избыток остаётся метилольными группами (−CH₂OH),
        // которые тоже входят в массу смолы, а воду дают только мостики (не больше одного на фенол сверх цепи)
        double bridges = type == ResinType.NOVOLAC ? Math.min(r, 0.99) : 1;
        double hcho = r * M_HCHO / M_PHENOL, water = bridges * M_H2O / M_PHENOL;
        double resin = 1 + hcho - water;
        double dpn = type == ResinType.NOVOLAC ? 1 / (1 - Math.min(r, 0.99)) : Double.POSITIVE_INFINITY;
        return new Resin(type, resin, water, hcho, dpn);
    }
}
