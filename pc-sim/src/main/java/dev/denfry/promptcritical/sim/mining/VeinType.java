package dev.denfry.promptcritical.sim.mining;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Тип смешанной жилы — строка каталога 08 §8.1.2.
 *
 * @param id        {@code vein_id}
 * @param seam      угольная свита (пласты §8.1.4) вместо линзы §8.1.1
 * @param roles     содержание по ролям {@link OreRole} (индекс — ordinal); {@code null} у основной роли угольной
 *                  свиты (уголь несёт {@code coal_assay}) и у отсутствующей роли (вероятность уходит основной)
 * @param yMin      нижняя граница Y тела
 * @param yMax      верхняя граница
 * @param yPeak     наиболее вероятная высота (треугольное распределение)
 * @param f0        f₀ — доля рудных блоков в центре тела
 * @param weight    вес выбора в ячейке
 * @param biomes    множители веса по классам биомов; класс, которого нет в таблице, тело не получает. Пустая
 *                  таблица — «все биомы» с множителем 1
 */
public record VeinType(String id, boolean seam, OreGrade[] roles, int yMin, int yMax, int yPeak, double f0,
        double weight, Map<BiomeClass, Double> biomes) {
    public VeinType {
        Objects.requireNonNull(id, "id");
        if (roles == null || roles.length != OreRole.values().length) throw new IllegalArgumentException(id + ": roles");
        if (!seam && roles[OreRole.PRIMARY.ordinal()] == null) throw new IllegalArgumentException(id + ": primary");
        if (!(yMin <= yPeak && yPeak <= yMax)) throw new IllegalArgumentException(id + ": y range");
        if (!(f0 > 0 && f0 <= 1 && weight >= 0)) throw new IllegalArgumentException(id + ": f0/weight");
        biomes = biomes.isEmpty() ? Map.of() : Map.copyOf(new EnumMap<>(biomes));
    }

    /** Содержание роли или {@code null}. */
    public OreGrade grade(OreRole role) {
        return roles[role.ordinal()];
    }

    /** Множитель веса в биоме; {@code null} — биом неизвестен (headless), множитель 1. */
    public double biomeFactor(BiomeClass biome) {
        if (biome == null || biomes.isEmpty()) return 1;
        return biomes.getOrDefault(biome, 0.0);
    }
}
