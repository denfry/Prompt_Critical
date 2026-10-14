package dev.denfry.promptcritical.sim.mining;

/**
 * Бензиновая лампа {@code flame_safety_lamp} (лампа Дэви/Вольфа, 08 §8.2.5): ореол над приспущенным пламенем
 * от 1 % CH₄, растёт к 5 %; выше 5 % смесь горит внутри сетки; гаснет при O₂ &lt; 17 %. Пламя выходит наружу
 * (воспламенение) при CH₄ ≥ 5 % и рваной сетке или струе быстрее 4 м/с.
 */
public final class SafetyLamp {
    public static final double HALO_FROM = 0.01;
    public static final double O2_EXTINCTION = 0.17;
    public static final double MAX_SAFE_VELOCITY = 4;

    private SafetyLamp() {}

    /**
     * @param xCh4      метан у лампы (у кровли — {@link MethaneField#roofMethane})
     * @param xO2       кислород
     * @param velocity  скорость струи, м/с
     * @param gauzeOk   сетка цела
     */
    public static LampState state(double xCh4, double xO2, double velocity, boolean gauzeOk) {
        if (xO2 < O2_EXTINCTION) return LampState.EXTINGUISHED;
        if (xCh4 >= StoneDustRule.CH4_LEL) {
            return !gauzeOk || velocity > MAX_SAFE_VELOCITY ? LampState.IGNITES : LampState.FLAME_IN_GAUZE;
        }
        return xCh4 >= HALO_FROM ? LampState.HALO : LampState.NORMAL;
    }

    /** Высота ореола, доля от полной (у 5 % — 1), для отрисовки. */
    public static double haloFraction(double xCh4) {
        return Math.max(0, Math.min(1, (xCh4 - HALO_FROM) / (StoneDustRule.CH4_LEL - HALO_FROM)));
    }
}
