package dev.denfry.promptcritical.sim.mining;

/**
 * Ключи {@code mining.*} шахтной атмосферы и кровли (08 §8.19); привязка мода читает их из
 * {@code promptcritical-server.toml}.
 *
 * @param methaneEmissionScale   множитель газовыделения [BALANCE]
 * @param desorptionTauS         τ_d десорбции обнажения, с физ.
 * @param explosionBlastFraction f_blast — доля теплоты взрыва в разрушение [BALANCE]
 * @param dustPerTonKg           пыль на тонну отбитого угля, кг [BALANCE]
 * @param canaryUptakeRatio      во сколько раз канарейка набирает COHb быстрее человека [сверить]
 * @param airVelocityMinMs       минимальная скорость воздуха в выработках, м/с
 * @param airVelocityFaceMaxMs   максимальная скорость в лаве, м/с
 * @param roofSpanStable         допустимый пролёт устойчивой кровли (песчаник), блоков [BALANCE]
 * @param roofSpanMedium         средней (аргиллит)
 * @param roofSpanWeak           неустойчивой (углистый сланец)
 */
public record MiningConfig(double methaneEmissionScale, double desorptionTauS, double explosionBlastFraction,
        double dustPerTonKg, double canaryUptakeRatio, double airVelocityMinMs, double airVelocityFaceMaxMs,
        int roofSpanStable, int roofSpanMedium, int roofSpanWeak) {
    public MiningConfig {
        if (!(methaneEmissionScale >= 0 && desorptionTauS > 0 && explosionBlastFraction >= 0 && dustPerTonKg >= 0))
            throw new IllegalArgumentException("mining config");
        if (!(canaryUptakeRatio >= 1)) throw new IllegalArgumentException("canaryUptakeRatio: " + canaryUptakeRatio);
        if (roofSpanStable < 1 || roofSpanMedium < 1 || roofSpanWeak < 1) throw new IllegalArgumentException("roof span");
    }

    public static MiningConfig defaults() {
        return new MiningConfig(1.0, 172_800, 0.03, 0.5, 20, 0.25, 4, 6, 4, 2);
    }

    /** Допустимый пролёт кровли без крепи, блоков. */
    public int roofSpan(RoofRock rock) {
        return switch (rock) {
            case SANDSTONE -> roofSpanStable;
            case MUDSTONE -> roofSpanMedium;
            case CARBONACEOUS_SHALE -> roofSpanWeak;
        };
    }

    /** Предел скорости в ветви, м/с. */
    public double maxVelocity(BranchKind kind) {
        return kind == BranchKind.FACE ? airVelocityFaceMaxMs : kind.maxVelocityMs;
    }
}
