package dev.denfry.promptcritical.sim.metallurgy;

/**
 * Ключи {@code casting.*} и {@code machining.*} (08 §8.19), нужные I0.
 *
 * @param chvorinovBSandMinCm2 B стали в песчаной форме, мин/см² (2,5)
 * @param taylorN              показатель Тейлора быстрорежущего резца (0,125)
 * @param taylorCMMin          C_T быстрорежущего резца по стали, м/мин (70)
 */
public record MetallurgyConfig(double chvorinovBSandMinCm2, double taylorN, double taylorCMMin) {
    public MetallurgyConfig {
        if (!(chvorinovBSandMinCm2 > 0 && taylorN > 0 && taylorN < 1 && taylorCMMin > 0))
            throw new IllegalArgumentException("metallurgy config");
    }

    public static MetallurgyConfig defaults() {
        return new MetallurgyConfig(2.5, 0.125, 70);
    }
}
