package dev.denfry.promptcritical.sim.combustion;

/**
 * Накипь на водяной стороне поверхностей нагрева (07 §7.9.3): dδ/dt = k_н·C_жёстк·q/q_ref, ΔT_ст = q·δ/λ_н.
 * k_н ({@code boiler.scale_rate}) подобран так, что сырая вода жёсткостью 5 мг-экв/л за 5 физ. суток при
 * q_ref = 300 кВт/м² даёт 0,5 мм, а λ_н = 1 Вт/(м·К) — +150 К на экранах.
 */
public final class BoilerScale {
    /** Опорный тепловой поток q_ref, Вт/м². */
    public static final double REF_FLUX = 300e3;
    /** Теплопроводность накипи λ_н, Вт/(м·К) (1–2 против 45 у стали). */
    public static final double CONDUCTIVITY = 1.0;
    /** {@code boiler.scale_rate} k_н, м/(с·моль-экв/м³) [BALANCE]. */
    public static final double RATE = 0.5e-3 / (5 * 86_400.0 * 5);

    private BoilerScale() {}

    /** Скорость роста накипи, м/с физ.: жёсткость котловой воды, моль-экв/м³ (= мг-экв/л), тепловой поток, Вт/м². */
    public static double growthRate(double hardnessMolEqM3, double fluxWm2) {
        return RATE * Math.max(0, hardnessMolEqM3) * Math.max(0, fluxWm2) / REF_FLUX;
    }

    /** Перепад температуры в слое накипи толщиной {@code thicknessM}, К. */
    public static double temperatureRise(double fluxWm2, double thicknessM) {
        return Math.max(0, fluxWm2) * thicknessM / CONDUCTIVITY;
    }

    /** Термическое сопротивление слоя, м²·К/Вт. */
    public static double resistance(double thicknessM) {
        return thicknessM / CONDUCTIVITY;
    }
}
