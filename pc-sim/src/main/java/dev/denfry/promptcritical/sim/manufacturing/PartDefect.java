package dev.denfry.promptcritical.sim.manufacturing;

/**
 * Дефекты поковки и детали (GDD 18 §18.15). Сериализуются по {@code name()}, поэтому константы
 * не переименовываются.
 */
public enum PartDefect {
    POROSITY(false),
    /** Трещина: брак на входном контроле (GDD 18 §18.15 п. 2). */
    CRACK(true),
    /** Пережог: брак на входном контроле (GDD 18 §18.15 п. 2). */
    BURNT(true),
    DECARB(false),
    SLAG_INCLUSION(false),
    LACK_OF_FUSION(false),
    INSULATION_FLAW(false),
    BUCKLED(false);

    /** Ворота: деталь с таким дефектом не принимается независимо от остальных свойств. */
    public final boolean gate;

    PartDefect(boolean gate) {
        this.gate = gate;
    }
}
