package dev.denfry.promptcritical.sim.metallurgy;

/**
 * Дефект отливки (08 §8.9). У отливки одно поле дефекта (как {@code PipeDefect} 08 §8.7.4): при нескольких
 * причинах записывается самый опасный — порядок констант от опасного к безобидному.
 */
public enum CastingDefect {
    /** Усадочная раковина: нет прибыли или её модуль &lt; 1,2 модуля отливки. Сквозная течь корпуса. */
    SHRINKAGE_CAVITY(true, true, true),
    /** Горячая трещина: разнотолщинность у сплава, склонного к горячим трещинам. */
    HOT_TEAR(true, true, true),
    /** Холодный спай (неслитина): перегрев &lt; 50 К. */
    COLD_SHUT(true, true, false),
    /** Газовая пористость: влажность смеси &gt; 6 % или нераскисленная сталь. */
    GAS_POROSITY(true, false, false),
    /** Отбел чугуна: быстрое охлаждение — твёрдый белый чугун, не режется. */
    CHILL(false, false, false),
    NONE(false, false, false);

    /** Находит радиография (12). */
    public final boolean radiography;
    /** Находит УЗК. */
    public final boolean ultrasonic;
    /** Даёт течь на гидроиспытании. */
    public final boolean leaksOnHydrotest;

    CastingDefect(boolean radiography, boolean ultrasonic, boolean leaksOnHydrotest) {
        this.radiography = radiography;
        this.ultrasonic = ultrasonic;
        this.leaksOnHydrotest = leaksOnHydrotest;
    }
}
