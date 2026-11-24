package dev.denfry.promptcritical.sim.radiation;

/**
 * Радиационные СИЗ органов дыхания и кожи (04b §4b.7.3). Кратность защиты (APF) — во сколько раз под маску попадает
 * меньше, чем в воздухе: отдельно по аэрозолям (Cs, Sr, актиниды на пыли) и по газообразному йоду, который фильтр P3
 * не держит вовсе — для него нужен угольный йодный патрон. Пневмокостюм с подачей воздуха по шлангу закрывает и кожу.
 * Кратности — порядок назначенных значений (OSHA, практика НРБ).
 *
 * @param aerosolApf кратность по аэрозолям
 * @param iodineApf  кратность по газообразному йоду (без йодного патрона у полнолицевой маски — 1)
 * @param skinRetained доля загрязнения, которую костюм не пускает на кожу
 */
public record RadRespirator(double aerosolApf, double iodineApf, double skinRetained) {
    /** Полумаска с фильтром P3: аэрозоли ×10, йод проходит. */
    public static final RadRespirator HALF_MASK_P3 = new RadRespirator(10, 1, 0);
    /** Полнолицевая маска с фильтром P3, без йодного патрона. */
    public static final RadRespirator FULL_FACE = new RadRespirator(50, 1, 0);
    /** Полнолицевая маска с йодным патроном: аэрозоли и йод ×50. */
    public static final RadRespirator FULL_FACE_IODINE = new RadRespirator(50, 50, 0);
    /** Фильтрующий с принудительной подачей (PAPR, комбинированный фильтр), пока есть заряд: ×1000. */
    public static final RadRespirator PAPR = new RadRespirator(1000, 1000, 0);
    /** Пневмокостюм с подачей воздуха по шлангу: ×2000, кожа закрыта на 99 %. */
    public static final RadRespirator PNEUMOSUIT = new RadRespirator(2000, 2000, 0.99);
    /** Без защиты. */
    public static final RadRespirator NONE = new RadRespirator(1, 1, 0);

    /** Наибольшая длина шланга пневмокостюма от поста подачи воздуха, м: дальше шланг рвётся. */
    public static final double HOSE_M = 16;
    /** Ресурс йодного патрона в загрязнённом йодом воздухе, физ. с (8 ч) [BALANCE]. */
    public static final double IODINE_CARTRIDGE_PHYS_S = 8 * 3600;
    /** Заряд батареи PAPR, физ. с (8 ч смены) [BALANCE]. */
    public static final double PAPR_BATTERY_PHYS_S = 8 * 3600;
    /** Спецодежда и бахилы: доля загрязнения, которую они задерживают (тело, обувь). */
    public static final double OVERALLS_RETAINED = 0.9;

    public RadRespirator {
        if (!(aerosolApf >= 1) || !(iodineApf >= 1) || skinRetained < 0 || skinRetained > 1)
            throw new IllegalArgumentException("protection factors must be at least 1");
    }

    /** Доля вдыхаемой активности, проходящая под маску: для йода (I-131) — по йодной кратности. */
    public double inhaledFraction(Emitter e) {
        return 1 / (e == Emitter.I131 ? iodineApf : aerosolApf);
    }

    /** Лучшая из двух защит (два надетых средства не перемножаются: маска под маской не работает). */
    public RadRespirator best(RadRespirator o) {
        return new RadRespirator(Math.max(aerosolApf, o.aerosolApf), Math.max(iodineApf, o.iodineApf),
                Math.max(skinRetained, o.skinRetained));
    }
}
