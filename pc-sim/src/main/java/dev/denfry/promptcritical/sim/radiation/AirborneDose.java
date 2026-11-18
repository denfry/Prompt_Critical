package dev.denfry.promptcritical.sim.radiation;

/**
 * Доза от радиоактивного облака: погружение во внешнее облучение и ингаляция (ICRP 72 — дозовые коэффициенты
 * ингаляции для взрослых; Eckerman, Ryman, FGR-12 — коэффициенты погружения в облако, эффективная доза). Объёмная
 * активность — из гауссовой модели рассеяния ({@link dev.denfry.promptcritical.sim.consequence.GasDispersion}) с
 * активностью вместо массы.
 * <ul>
 *   <li>Kr-85: погружение 1,19·10⁻¹⁶ Зв·м³/(Бк·с), ингаляции нет (инертный газ);</li>
 *   <li>I-131: ингаляция 7,4·10⁻⁹ Зв/Бк (тип F), погружение 1,7·10⁻¹⁴;</li>
 *   <li>Cs-137: ингаляция 4,6·10⁻⁹ Зв/Бк (тип F), погружение 2,6·10⁻¹⁴ (с Ba-137m).</li>
 * </ul>
 * Дыхание взрослого при лёгкой работе — 1,2 м³/ч (ICRP 66).
 */
public final class AirborneDose {
    public static final double BREATHING_M3_S = 1.2 / 3600;

    private AirborneDose() {
    }

    /** Коэффициент погружения, Зв·м³/(Бк·с). */
    public static double submersion(Emitter e) {
        return switch (e) {
            case KR85 -> 1.19e-16;
            case I131 -> 1.7e-14;
            case CS137 -> 2.6e-14;
            default -> 0;
        };
    }

    /** Коэффициент ингаляции, Зв/Бк. */
    public static double inhalation(Emitter e) {
        return switch (e) {
            case I131 -> 7.4e-9;
            case CS137 -> 4.6e-9;
            default -> 0;
        };
    }

    /** Мощность эффективной дозы в облаке с объёмной активностью {@code bqM3}, Зв/ч. */
    public static double svH(Emitter e, double bqM3) {
        return (submersion(e) + inhalation(e) * BREATHING_M3_S) * bqM3 * 3600;
    }
}
