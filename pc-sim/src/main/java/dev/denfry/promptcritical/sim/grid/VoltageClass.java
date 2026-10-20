package dev.denfry.promptcritical.sim.grid;

/**
 * Классы напряжения ГОСТ 721-77 — лестница прогрессии (07 §7.0.3, §7.4.8, R20). Базисное напряжение
 * класса — номинал. Испытательные напряжения — ГОСТ 1516.3 [сверить], режим нейтрали — §7.5.1.
 */
public enum VoltageClass {
    LV_0_4(400, 2.5, Double.NaN, 1),
    MV_6_3(6300, 32, 60, Double.POSITIVE_INFINITY),
    MV_10_5(10500, 42, 75, Double.POSITIVE_INFINITY),
    MV_35(35000, 95, 190, Double.POSITIVE_INFINITY),
    HV_110(110000, 230, 480, 3),
    HV_220(220000, 460, 950, 3);

    public final double volts;
    /** Одноминутное испытательное напряжение промышленной частоты, кВ. */
    public final double testKv;
    /** Грозовой импульс (BIL), кВ; NaN — не нормируется. */
    public final double bilKv;
    /**
     * z₀/z₁ в месте КЗ по режиму нейтрали: 0,4 кВ — глухозаземлённая (≈ 1), 6–35 кВ — изолированная
     * (бесконечность: однофазное замыкание — не КЗ), 110 кВ и выше — эффективно заземлённая (≈ 3).
     */
    public final double zeroSequenceRatio;

    VoltageClass(double v, double testKv, double bilKv, double z0) {
        volts = v;
        this.testKv = testKv;
        this.bilKv = bilKv;
        zeroSequenceRatio = z0;
    }

    /** Нейтраль изолирована: однофазное замыкание на землю не отключается, а сигнализируется. */
    public boolean isolatedNeutral() {
        return Double.isInfinite(zeroSequenceRatio);
    }

    /** k = U/U_ном, выше которого изоляция блока этого класса пробивается в первый же тик (§7.4.8). */
    public double breakdownRatio() {
        return testKv * 1e3 / volts;
    }

    /** Класс с ближайшим (по логарифму) номиналом. */
    public static VoltageClass nearest(double volts) {
        if (!(volts > 0)) throw new IllegalArgumentException("volts");
        VoltageClass best = LV_0_4;
        for (VoltageClass c : values()) {
            if (Math.abs(Math.log(volts / c.volts)) < Math.abs(Math.log(volts / best.volts))) best = c;
        }
        return best;
    }

    /**
     * Относительная скорость расходования ресурса изоляции при k = U/U_ном (обратностепенной закон §7.4.8,
     * n = {@code grid.overvoltage_aging_exponent}); не ниже 1 — ниже номинала изоляция стареет как обычно.
     */
    public static double agingRate(double k, double exponent) {
        return k > 1 ? Math.pow(k, exponent) : 1;
    }
}
