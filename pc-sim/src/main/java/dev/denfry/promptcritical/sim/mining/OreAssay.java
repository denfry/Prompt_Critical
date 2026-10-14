package dev.denfry.promptcritical.sim.mining;

import java.util.Map;
import java.util.Objects;

/**
 * Анализ руды — компонент {@code ore_assay} предметов {@code raw_*} (08 §8.17). Тип блока виден сразу; класс
 * G0–G5, попутные и сера — после анализа (§8.1.3).
 *
 * @param veinId        жила-источник
 * @param mineral       ID руды
 * @param gradeClass    класс G0–G5 — квантование для стекования (02 §2.0.3)
 * @param gradeFrac     содержание основного компонента, массовая доля
 * @param byproductsPpm попутные (Co, PGM, Mo, Ag), ppm (г/т)
 * @param sulfurFrac    сера, массовая доля
 * @param alloy         ферросплав ({@code FeMn|FeSi|FeCr|FeMo}) у продуктов плавки; у сырой руды {@code null}
 * @param purity        чистота материала мишени (12), NaN — не задана
 */
public record OreAssay(String veinId, String mineral, byte gradeClass, double gradeFrac,
        Map<String, Double> byproductsPpm, double sulfurFrac, String alloy, double purity) {
    public OreAssay {
        Objects.requireNonNull(mineral, "mineral");
        if (gradeClass < 0 || gradeClass >= OreGrade.CLASSES) throw new IllegalArgumentException("gradeClass: " + gradeClass);
        if (!(gradeFrac >= 0 && gradeFrac <= 1)) throw new IllegalArgumentException("gradeFrac: " + gradeFrac);
        if (!(sulfurFrac >= 0 && sulfurFrac <= 1)) throw new IllegalArgumentException("sulfurFrac: " + sulfurFrac);
        byproductsPpm = Map.copyOf(byproductsPpm);
    }

    /** Попутный компонент, ppm (0 — нет). */
    public double byproductPpm(String id) {
        return byproductsPpm.getOrDefault(id, 0.0);
    }
}
