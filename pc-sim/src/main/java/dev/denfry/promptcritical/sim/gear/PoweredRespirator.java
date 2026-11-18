package dev.denfry.promptcritical.sim.gear;

import dev.denfry.promptcritical.sim.battery.BatterySpec;

/**
 * Фильтрующий респиратор с принудительной подачей воздуха (PAPR): полнолицевая маска, вентилятор с двигателем на
 * поясе прогоняет воздух через противогазовый фильтр кислых газов (хлор, фтористый водород) и держит под маской
 * избыточное давление — подсос мимо полосы обтюрации почти исчезает.
 *
 * <ul>
 *   <li>Подача — {@value #BLOWER_LPM} л/мин: наименьший расход для плотно прилегающей лицевой части
 *   (NIOSH, 42 CFR 84.175).</li>
 *   <li>Коэффициент защиты: с работающим вентилятором — {@value #APF_POWERED} (OSHA 29 CFR 1910.134, табл. 1: PAPR с
 *   полнолицевой маской); без питания маска остаётся обычным фильтрующим противогазом — {@value #APF_UNPOWERED}
 *   (там же, полнолицевая маска с отрицательным давлением), и через фильтр идёт только лёгочная вентиляция
 *   {@value #BREATHING_LPM} л/мин (умеренная работа).</li>
 *   <li>Ёмкость фильтра — класс 1 по EN 14387 для газов группы B: хлор 1000 ppm при 30 л/мин держится не меньше
 *   20 мин, то есть 0,6 л газа ≈ 0,0245 моль. Хлор и HF сорбент поглощает одинаково по молям [упрощение]. После
 *   проскока фильтр пропускает газ, защиты нет.</li>
 *   <li>Двигатель вентилятора — {@value #BLOWER_W} Вт [оценка: около 0,5 А при 4,8 В у поясных блоков на четырёх
 *   NiCd элементах]; батарея — четыре элемента по 4 А·ч, около 8 ч работы.</li>
 * </ul>
 *
 * <p>Фильтр не задерживает угарный газ и не добавляет кислорода: в шахтной атмосфере после пожара или взрыва спасает
 * только изолирующий самоспасатель. Поэтому респиратор защищает от токсичных облаков выбросов, а не от рудничного
 * газа.
 */
public final class PoweredRespirator {
    public static final double BLOWER_LPM = 115, BREATHING_LPM = 30;
    public static final double APF_POWERED = 1000, APF_UNPOWERED = 50;
    /** Мольный объём газа при 25 °C, л/моль. */
    static final double MOLAR_VOLUME_L = 24.45;
    public static final double FILTER_CAPACITY_MOL = 0.6 / MOLAR_VOLUME_L;
    public static final double BLOWER_W = 2.4;

    private final PowerPack pack;
    private double filterMol;
    private boolean running;

    public PoweredRespirator(BatterySpec spec) {
        this.pack = new PowerPack(spec);
    }

    /** Поясной блок: четыре ламельных NiCd элемента по 4 А·ч (4,8 В). */
    public static PoweredRespirator standard() {
        return new PoweredRespirator(BatterySpec.nickelCadmiumPocketPlate(4, 4));
    }

    public PowerPack pack() {
        return pack;
    }

    /**
     * Надетый респиратор дышит воздухом с концентрацией {@code ppm} в течение {@code dtS} секунд.
     *
     * @return концентрация под маской, ppm
     */
    public double breathe(double dtS, double ppm, double ambientC) {
        running = pack.supply(dtS, BLOWER_W, ambientC) > 0;
        return inhale(dtS, ppm, running);
    }

    /**
     * Только фильтр: {@code dtS} секунд вдоха воздуха с концентрацией {@code ppm} при включённом или стоящем
     * вентиляторе (батарею мир считает отдельно, по своим часам).
     *
     * @return концентрация под маской, ppm
     */
    public double inhale(double dtS, double ppm, boolean blowerOn) {
        if (filterSpent()) return ppm;
        double flowLpm = blowerOn ? BLOWER_LPM : BREATHING_LPM;
        if (ppm > 0) filterMol += ppm * 1e-6 * flowLpm * dtS / 60 / MOLAR_VOLUME_L;
        return ppm / (blowerOn ? APF_POWERED : APF_UNPOWERED);
    }

    /** Хранение: только саморазряд. */
    public void idle(double dtS, double ambientC) {
        running = false;
        pack.supply(dtS, 0, ambientC);
    }

    /** Вентилятор работал на последнем шаге. */
    public boolean running() {
        return running;
    }

    /** Поглощено фильтром, моль. */
    public double filterMol() {
        return filterMol;
    }

    /** Доля ёмкости фильтра, 0…1. */
    public double filterLoad() {
        return Math.min(1, filterMol / FILTER_CAPACITY_MOL);
    }

    public boolean filterSpent() {
        return filterMol >= FILTER_CAPACITY_MOL;
    }

    /** Новый фильтр. */
    public void replaceFilter() {
        filterMol = 0;
    }

    /** Восстановить из предмета. */
    public void restore(double removedAh, double cycles, double tempC, double filterMol) {
        pack.battery().restore(removedAh, cycles, tempC);
        this.filterMol = Math.max(0, filterMol);
    }
}
