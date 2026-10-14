package dev.denfry.promptcritical.sim.mining;

import java.util.Objects;

/**
 * Ключи {@code mining.hoist_*}, {@code mining.rope_*}, {@code mining.safety_mode} (08 §8.19).
 *
 * @param sfPeople          запас прочности людского подъёма (9)
 * @param sfMixed           грузо-людского (7,5)
 * @param sfCargo           грузового (6,5)
 * @param ropeN0Cycles      N₀ кривой усталости (2·10⁵) [BALANCE]
 * @param corrosionPerDay   коррозия каната в мокром стволе, D_k в сутки (0,002)
 * @param ropeItemLengthM   длина предмета {@code wire_rope}, м (10)
 * @param safetyMode        {@code ENFORCED} — запрет, {@code ADVISORY} — запись {@code UNSAFE_OPERATION}
 */
public record HoistConfig(double sfPeople, double sfMixed, double sfCargo, double ropeN0Cycles,
        double corrosionPerDay, double ropeItemLengthM, SafetyMode safetyMode) {
    /** Режим норм безопасности. */
    public enum SafetyMode { ENFORCED, ADVISORY }

    public HoistConfig {
        if (!(sfPeople >= 1 && sfMixed >= 1 && sfCargo >= 1)) throw new IllegalArgumentException("hoist sf");
        if (!(ropeN0Cycles > 0 && corrosionPerDay >= 0 && ropeItemLengthM > 0)) throw new IllegalArgumentException("rope");
        Objects.requireNonNull(safetyMode, "safetyMode");
    }

    public static HoistConfig defaults() {
        return new HoistConfig(9, 7.5, 6.5, 2e5, 0.002, 10, SafetyMode.ENFORCED);
    }

    /** Требуемый запас прочности. */
    public double requiredSafetyFactor(HoistDuty duty) {
        return switch (duty) {
            case PEOPLE -> sfPeople;
            case MIXED -> sfMixed;
            case CARGO -> sfCargo;
        };
    }
}
