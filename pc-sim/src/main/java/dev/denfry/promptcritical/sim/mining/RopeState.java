package dev.denfry.promptcritical.sim.mining;

import java.util.Objects;

/**
 * Состояние подъёмного каната — компонент {@code rope_state} (08 §8.17, §8.2.8).
 *
 * @param diameterMm     d, мм
 * @param gradeMpa       маркировочная группа R_r, МПа (1770)
 * @param construction   конструкция
 * @param damage         D_k — накопленное повреждение (0,7 — {@code ROPE_WEAR}, 1 — обрыв)
 * @param bendCycles     число перегибов на шкиве
 * @param lastInspectedT время последнего осмотра, с симуляции
 * @param tonneKm        наработка, т·км
 */
public record RopeState(double diameterMm, double gradeMpa, RopeConstruction construction, double damage,
        long bendCycles, double lastInspectedT, double tonneKm) {
    /** D_k, с которого на панели обслуживания {@code ROPE_WEAR} (13). */
    public static final double WEAR_ALARM = 0.7;
    /**
     * Остаточная прочность F = F_разр·(1 − k·D_k): обрывы проволок снижают прочность ещё до D_k = 1 [допущение,
     * в GDD нет; порядок — браковка по обрывам на шаге свивки].
     */
    public static final double STRENGTH_LOSS_AT_FULL_DAMAGE = 0.3;

    public RopeState {
        if (!(diameterMm > 0 && gradeMpa > 0)) throw new IllegalArgumentException("rope d/grade");
        Objects.requireNonNull(construction, "construction");
        if (!(damage >= 0) || bendCycles < 0 || !(tonneKm >= 0)) throw new IllegalArgumentException("rope wear");
    }

    /** Новый канат. */
    public static RopeState fresh(double diameterMm, double gradeMpa, RopeConstruction construction) {
        return new RopeState(diameterMm, gradeMpa, construction, 0, 0, 0, 0);
    }

    /** Разрывное усилие нового каната F_разр, Н. */
    public double breakingForceN() {
        return construction.breakingCoeff * diameterMm * diameterMm * gradeMpa;
    }

    /** Остаточная прочность с учётом повреждения, Н; 0 — канат оборван. */
    public double residualStrengthN() {
        return damage >= 1 ? 0 : breakingForceN() * (1 - STRENGTH_LOSS_AT_FULL_DAMAGE * damage);
    }

    /** Погонная масса q_k, кг/м. */
    public double massPerMetreKg() {
        return construction.massCoeff * diameterMm * diameterMm;
    }

    /** Металлическое сечение, м². */
    public double metalAreaM2() {
        return construction.areaCoeff * diameterMm * diameterMm * 1e-6;
    }

    /** Нужна замена ({@code ROPE_WEAR}). */
    public boolean worn() {
        return damage >= WEAR_ALARM;
    }

    /** Шаг свивки, на котором осмотр видит обрывы проволок: 40·D_k³ (§8.2.8) — обрывов на шаг. */
    public double visibleBrokenWires() {
        return 40 * damage * damage * damage;
    }

    /** Отметка осмотра. */
    public RopeState inspected(double t) {
        return new RopeState(diameterMm, gradeMpa, construction, damage, bendCycles, t, tonneKm);
    }
}
