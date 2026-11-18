package dev.denfry.promptcritical.sim.radiation;


/**
 * Радионуклиды, значимые для дозы от отработанного топлива и активированной стали: период полураспада, линии γ,
 * средняя энергия распада в тепло (β + γ) и дочерний нуклид (ENSDF / Nuclear Data Sheets; Козлов В. Ф., «Справочник
 * по радиационной безопасности», 1999; ICRP 107). Короткоживущие дочерние в вековом равновесии свёрнуты в родителя:
 * Ba-137m — в Cs-137, Rh-106 — в Ru-106. Долгоживущие дочерние — отдельные нуклиды с накоплением по Бейтману: Y-90
 * из Sr-90, Nb-95 из Zr-95. Тормозное излучение β (Sr-90/Y-90) не учитывается. Керм-постоянная Γ_K, мГр·м²/(ГБк·ч),
 * следует из линий: Co-60 — 0,306, Cs-137 — 0,0779.
 */
public enum Emitter {
    CO60(5.2714 * Times.Y, new double[] {1.1732, 1.3325}, new double[] {0.9985, 0.9998}, 2.600, null),
    CS137(30.08 * Times.Y, new double[] {0.6617}, new double[] {0.851}, 0.810, null),
    CS134(2.0652 * Times.Y, new double[] {0.5693, 0.6047, 0.7958, 0.8019, 1.3652}, new double[] {0.154, 0.976, 0.855, 0.087, 0.030}, 1.71, null),
    I131(8.0252 * Times.D, new double[] {0.3645, 0.6370}, new double[] {0.815, 0.0716}, 0.571, null),
    Y90(2.6684 * Times.D, new double[0], new double[0], 0.934, null),
    SR90(28.79 * Times.Y, new double[0], new double[0], 0.196, Y90),
    NB95(34.991 * Times.D, new double[] {0.7658}, new double[] {0.998}, 0.808, null),
    ZR95(64.032 * Times.D, new double[] {0.7242, 0.7567}, new double[] {0.4417, 0.5446}, 0.852, NB95),
    RU106(371.8 * Times.D, new double[] {0.5119, 0.6221, 1.0503}, new double[] {0.204, 0.0993, 0.0156}, 1.62, null);

    public final double halfLifeS;
    /** Средняя энергия распада, уходящая в тепло, МэВ. */
    public final double heatMeV;
    /** Дочерний нуклид или null. */
    public final Emitter daughter;
    private final double[] energies, yields;

    Emitter(double halfLifeS, double[] energies, double[] yields, double heatMeV, Emitter daughter) {
        this.halfLifeS = halfLifeS;
        this.energies = energies;
        this.yields = yields;
        this.heatMeV = heatMeV;
        this.daughter = daughter;
    }

    /** Постоянная распада, 1/с. */
    public double lambda() {
        return Math.log(2) / halfLifeS;
    }

    /** Активность через {@code t} секунд без учёта накопления, Бк. */
    public double activity(double a0Bq, double tS) {
        return a0Bq * Math.pow(2, -tS / halfLifeS);
    }

    /** Источник при активности {@code bq}. */
    public PointSource source(double bq) {
        double[] p = new double[yields.length];
        for (int i = 0; i < p.length; i++) p[i] = bq * yields[i];
        return new PointSource(energies.clone(), p, 0);
    }

    /** Керм-постоянная, мГр·м²/(ГБк·ч), из линий. */
    public double kermaConstant() {
        return DoseRate.gammaGyH(source(1e9), 1, java.util.List.of()) * 1000;
    }

    /** Линии γ, МэВ (копия). */
    public double[] energies() {
        return energies.clone();
    }
}

/** Единицы времени для таблицы нуклидов (юлианский год). */
final class Times {
    static final double D = 86400, Y = 365.25 * D;

    private Times() {
    }
}
