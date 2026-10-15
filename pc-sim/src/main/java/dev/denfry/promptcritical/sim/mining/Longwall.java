package dev.denfry.promptcritical.sim.mining;

/**
 * Очистной комбайн механизированной лавы (08 §8.2.3): удельная энергия резания H_w по крепости f (Протодьяконов),
 * энергия на блок, мощность привода от вала Create и предел подачи v_f.
 *
 * <p>H_w линейна по f: 0,3 кВт·ч/т при f = 0,8 и 0,8 кВт·ч/т при f = 1,5 (концы диапазонов GDD); порода вне пласта
 * режется по той же прямой. Крепость угля — по выходу летучих: 0,8 при V^daf ≥ 40 % (длиннопламенные, бурые) и
 * 1,5 при V^daf ≤ 8 % (антрацит) [допущение: в GDD связи с маркой нет].
 */
public final class Longwall {
    /** Пределы крепости угля по Протодьяконову (08 §8.2.3). */
    public static final double F_SOFT = 0.8, F_HARD = 1.5;
    /** H_w на концах диапазона крепости, кВт·ч/т. */
    public static final double HW_SOFT = 0.3, HW_HARD = 0.8;
    /** Наименьшая H_w — для пород мягче угля, кВт·ч/т. */
    public static final double HW_MIN = 0.1;
    /** V^daf, ниже и выше которых крепость угля постоянна. */
    public static final double VDAF_HARD = 0.08, VDAF_SOFT = 0.40;
    /** Плотность породы в массиве, кг/м³. */
    public static final double ROCK_DENSITY = 2600;

    private Longwall() {}

    /** Крепость угля f по выходу летучих V^daf (доля). */
    public static double coalProtodyakonov(double volatileDaf) {
        double t = (volatileDaf - VDAF_HARD) / (VDAF_SOFT - VDAF_HARD);
        t = Math.max(0, Math.min(1, t));
        return F_HARD + (F_SOFT - F_HARD) * t;
    }

    /** Удельная энергия резания H_w, кВт·ч/т. */
    public static double specificEnergyKwhPerT(double f) {
        return Math.max(HW_MIN, HW_SOFT + (f - F_SOFT) * (HW_HARD - HW_SOFT) / (F_HARD - F_SOFT));
    }

    /** Энергия резания блока 1 м³ плотности ρ, Дж. */
    public static double blockEnergyJ(double f, double densityKgM3) {
        return specificEnergyKwhPerT(f) * 3.6e6 * densityKgM3 / 1000;
    }

    /**
     * Мощность резания, Вт: привод даёт номинал пропорционально скорости вала до номинальной, но не больше свободной
     * мощности сети Create.
     *
     * @param rpm    скорость вала gantry, об/мин
     * @param spareW свободная мощность сети, Вт
     */
    public static double cuttingPowerW(LongwallConfig cfg, double rpm, double spareW) {
        double drive = cfg.shearerRatedW() * Math.min(1, Math.abs(rpm) / cfg.nominalRpm());
        return Math.max(0, Math.min(drive, spareW));
    }

    /** Наименьшее время выемки столбца шириной 1 м при v_f,max, с. */
    public static double minColumnTimeS(LongwallConfig cfg) {
        return 60 / cfg.maxFeedMPerMin();
    }

    /** Скорость подачи, м/мин, если столбец 1 м вынут за t с. */
    public static double feedMPerMin(double columnTimeS) {
        return columnTimeS <= 0 ? Double.POSITIVE_INFINITY : 60 / columnTimeS;
    }

    /** Вода орошения на массу угля, м³. */
    public static double sprayWaterM3(LongwallConfig cfg, double tonnes) {
        return cfg.sprayWaterLPerT() * tonnes / 1000;
    }
}
