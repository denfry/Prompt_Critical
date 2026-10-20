package dev.denfry.promptcritical.sim.metallurgy;

/**
 * Скрытый дефект трубы (08 §8.7.4). Кто находит — по таблице контроля: УЗК — расслоения, непровар, стенку (и плены);
 * радиография — поры, непровар, трещины, раковины. Гидроиспытание находит любой дефект с p_f &lt; p_исп (труба
 * лопается). Плоские дефекты ({@link #planar}) подрастают по Пэрису.
 */
public enum DefectKind {
    NONE(false, false, false),
    LACK_OF_FUSION(true, true, true),
    POROSITY(false, true, false),
    HOOK_CRACK(false, true, true),
    LAMINATION(true, false, false),
    /** Разностенность. */
    ECCENTRICITY(true, false, false),
    /** Плены от осевой пористости заготовки. */
    INCLUSION_LAP(true, false, false),
    SHRINKAGE(false, true, false);

    public final boolean ultrasonic, radiography, planar;

    DefectKind(boolean ultrasonic, boolean radiography, boolean planar) {
        this.ultrasonic = ultrasonic;
        this.radiography = radiography;
        this.planar = planar;
    }
}
