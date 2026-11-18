package dev.denfry.promptcritical.sim.ops;

import java.util.Map;

/**
 * Маркировка продуктов (13 §13.8.2): номер ООН, код опасности Кемлера (ДОПОГ/ADR) и ромб NFPA 704 —
 * здоровье, пожароопасность, реакционная способность (0–4) и особая опасность ({@code W}, {@code OX}, {@code SA}).
 * Значения NFPA — по таблице §13.8.2 [сверить].
 */
public final class HazardCodes {
    public record Code(int un, String kemler, int health, int fire, int reactivity, String special) {
        /** Ромб строкой «З-П-Р[-особ.]». */
        public String diamond() {
            return health + "-" + fire + "-" + reactivity + (special.isEmpty() ? "" : "-" + special);
        }
    }

    private static final Map<String, Code> BY_PRODUCT = Map.ofEntries(
            Map.entry("gasoline", new Code(1203, "33", 1, 3, 0, "")),
            Map.entry("motor_gasoline", new Code(1203, "33", 1, 3, 0, "")),
            Map.entry("diesel_fuel", new Code(1202, "30", 1, 2, 0, "")),
            Map.entry("crude_oil", new Code(1267, "33", 1, 3, 0, "")),
            Map.entry("sales_gas", new Code(1971, "23", 2, 4, 0, "")),
            Map.entry("natural_gas", new Code(1971, "23", 2, 4, 0, "")),
            Map.entry("associated_gas", new Code(1971, "23", 2, 4, 0, "")),
            Map.entry("propane", new Code(1978, "23", 2, 4, 0, "")),
            Map.entry("butane", new Code(1011, "23", 2, 4, 0, "")),
            Map.entry("hydrogen", new Code(1049, "23", 0, 4, 0, "")),
            Map.entry("hydrogen_sulfide", new Code(1053, "263", 4, 4, 0, "")),
            Map.entry("chlorine", new Code(1017, "268", 4, 0, 0, "OX")),
            Map.entry("methanol", new Code(1230, "336", 1, 3, 0, "")),
            Map.entry("benzene", new Code(1114, "33", 2, 3, 0, "")),
            Map.entry("liquid_nitrogen", new Code(1977, "22", 3, 0, 0, "SA")),
            Map.entry("liquid_oxygen", new Code(1073, "225", 3, 0, 0, "OX")),
            Map.entry("argon", new Code(1951, "22", 3, 0, 0, "SA")),
            Map.entry("helium", new Code(1046, "20", 0, 0, 0, "SA")),
            Map.entry("sodium_hydroxide_solution", new Code(1824, "80", 3, 0, 1, "")),
            Map.entry("sulfuric_acid", new Code(1830, "80", 3, 0, 2, "W")),
            Map.entry("uranium_hexafluoride", new Code(2978, "78", 4, 0, 2, "W")));

    private HazardCodes() {
    }

    /** Маркировка продукта по его идентификатору (путь без пространства имён); {@code null} — нет в таблице. */
    public static Code of(String product) {
        return BY_PRODUCT.get(product);
    }
}
