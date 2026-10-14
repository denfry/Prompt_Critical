package dev.denfry.promptcritical.sim.mining;

import java.util.Map;

/**
 * Ключи {@code worldgen.veins.*} и {@code worldgen.coal.*} (08 §8.19).
 *
 * @param enabled              {@code worldgen.veins.enabled}
 * @param cellChunks           сторона ячейки, чанков (3)
 * @param fillProbability      вероятность тела в ячейке (0,45) [BALANCE]
 * @param weights              {@code worldgen.veins.<vein_id>.weight}; нет ключа — вес из каталога, 0 — жила
 *                             выключена ({@code .enabled = false})
 * @param seamThicknessMin     минимальная мощность пласта, блоков (1)
 * @param seamThicknessMax     максимальная (4)
 * @param rankGradientPerBlock падение V^daf с глубиной, %/блок (0,32)
 */
public record VeinConfig(boolean enabled, int cellChunks, double fillProbability, Map<String, Double> weights,
        int seamThicknessMin, int seamThicknessMax, double rankGradientPerBlock) {
    public VeinConfig {
        if (cellChunks < 1) throw new IllegalArgumentException("cellChunks: " + cellChunks);
        if (!(fillProbability >= 0 && fillProbability <= 1)) throw new IllegalArgumentException("fillProbability");
        if (seamThicknessMin < 1 || seamThicknessMax < seamThicknessMin) throw new IllegalArgumentException("seam thickness");
        if (!(rankGradientPerBlock >= 0)) throw new IllegalArgumentException("rankGradientPerBlock");
        weights = Map.copyOf(weights);
    }

    public static VeinConfig defaults() {
        return new VeinConfig(true, 3, 0.45, Map.of(), 1, 4, 0.32);
    }

    /** Сторона ячейки, блоков. */
    public int cellBlocks() {
        return cellChunks * 16;
    }

    /** Вес типа с учётом конфига. */
    public double weight(VeinType type) {
        Double w = weights.get(type.id());
        return w != null ? w : type.weight();
    }
}
