package dev.denfry.promptcritical.sim.radiation;

import java.util.List;

/**
 * Горизонтальный модуль сухого хранения класса NUHOMS (02 §2.7.4): пенал с ТВС лежит в бетонном модуле, тепло
 * уносит воздух естественной тягой — вход внизу на фасаде, выход наверху. Тепловая модель — стационарная по 02
 * §2.7.4: T_оболочки = T_окр + P·R₀/φ, R₀ = {@value #R0_K_PER_W} К/Вт (35 кВт → +280 К), φ — доля открытых
 * вентканалов; совсем закрытые каналы — не меньше {@value #MIN_VENT} (тепло уходит теплопроводностью бетона).
 * К стационару температура идёт с постоянной времени {@value #TAU_S} физ. с (масса пенала и бетона) [BALANCE].
 * Предел оболочек твэлов при хранении — 400 °C (NRC ISG-11).
 */
public final class DryStorage {
    public static final double R0_K_PER_W = 8e-3, MIN_VENT = 0.1, TAU_S = 7200, CLAD_LIMIT_K = 673.15;
    /** Защита: стены и крыша модуля, обычный бетон (HSM ≈ 0,9 м). */
    public static final List<DoseRate.Layer> WALL = List.of(new DoseRate.Layer(ShieldMaterial.CONCRETE, 90));
    /** Мест под ТВС в пенале (NUHOMS-24PT). */
    public static final int ASSEMBLIES = 24;

    private DryStorage() {
    }

    /** Источник пенала, видимый снаружи модуля. */
    public static PointSource outside(PointSource canister) {
        return DoseRate.behind(canister, WALL);
    }

    /** Стационарная температура оболочек, К. */
    public static double steadyCladK(double ambientK, double heatW, double ventOpen) {
        return ambientK + Math.max(0, heatW) * R0_K_PER_W / Math.max(MIN_VENT, Math.min(1, ventOpen));
    }

    /** Температура оболочек через {@code dtS} физ. с: экспонента к стационару. */
    public static double step(double cladK, double ambientK, double heatW, double ventOpen, double dtS) {
        double target = steadyCladK(ambientK, heatW, ventOpen);
        return target + (cladK - target) * Math.exp(-Math.max(0, dtS) / TAU_S);
    }

    public static boolean overLimit(double cladK) {
        return cladK > CLAD_LIMIT_K;
    }
}
