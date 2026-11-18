package dev.denfry.promptcritical.sim.radiation;


/**
 * Радионуклиды, значимые для дозы от отработанного топлива и активированной стали: период полураспада, линии γ,
 * средняя энергия распада в тепло (β + γ) и дочерний нуклид (ENSDF / Nuclear Data Sheets; Козлов В. Ф., «Справочник
 * по радиационной безопасности», 1999; ICRP 107). Короткоживущие дочерние в вековом равновесии свёрнуты в родителя:
 * Ba-137m — в Cs-137, Rh-106 — в Ru-106. Долгоживущие дочерние — отдельные нуклиды с накоплением по Бейтману: Y-90
 * из Sr-90, Nb-95 из Zr-95. Тормозное излучение β Y-90 — эффективной линией (см. {@link #Y90}). Керм-постоянная Γ_K, мГр·м²/(ГБк·ч),
 * следует из линий: Co-60 — 0,306, Cs-137 — 0,0779, Ir-192 — ≈ 0,13.
 *
 * <p>Изотопы облучательных каналов (12 §12.2): Ir-192 — девять линий с выходом больше 3 % (ENSDF; отброшенные слабые линии
 * и K-рентген дают меньше 3 % кермы); Pu-238 — α-распад 5,593 МэВ целиком в тепло, γ 43,5 кэВ (0,04 %)
 * пренебрежимы, зато есть нейтроны спонтанного деления и (α,n) на кислороде оксида — {@link #neutronsPerDecay}.
 */
public enum Emitter {
    CO60(5.2714 * Times.Y, new double[] {1.1732, 1.3325}, new double[] {0.9985, 0.9998}, 2.600, null),
    CS137(30.08 * Times.Y, new double[] {0.6617}, new double[] {0.851}, 0.810, null),
    CS134(2.0652 * Times.Y, new double[] {0.5693, 0.6047, 0.7958, 0.8019, 1.3652}, new double[] {0.154, 0.976, 0.855, 0.087, 0.030}, 1.71, null),
    I131(8.0252 * Times.D, new double[] {0.3645, 0.6370}, new double[] {0.815, 0.0716}, 0.571, null),
    /**
     * Y-90: γ нет, но жёсткие β (E_max 2,28 МэВ) тормозятся в стали капсулы и матрице источника. Доля энергии β в
     * тормозное излучение ≈ 3,5·10⁻⁴·Z·E_max (Z ≈ 26) = 2,1 % → 0,019 МэВ на распад; спектр свёрнут в одну линию
     * 0,5 МэВ (12 Q12.7). Этим «Бета-М» без свинца светит единицами Гр/ч на метре.
     */
    Y90(2.6684 * Times.D, new double[] {0.5}, new double[] {0.0387}, 0.934, null),
    SR90(28.79 * Times.Y, new double[0], new double[0], 0.196, Y90),
    NB95(34.991 * Times.D, new double[] {0.7658}, new double[] {0.998}, 0.808, null),
    ZR95(64.032 * Times.D, new double[] {0.7242, 0.7567}, new double[] {0.4417, 0.5446}, 0.852, NB95),
    RU106(371.8 * Times.D, new double[] {0.5119, 0.6221, 1.0503}, new double[] {0.204, 0.0993, 0.0156}, 1.62, null),
    /** Криптон-85: инертный газ, при растворении топлива уходит в газоочистку и в трубу. */
    KR85(10.739 * Times.Y, new double[] {0.5140}, new double[] {0.00434}, 0.253, null),
    /** Иридий-192: источник гамма-дефектоскопов (12 §12.2.3). Тепло — средняя энергия β 0,18 МэВ плюс γ. */
    IR192(73.829 * Times.D, new double[] {0.20579, 0.29596, 0.30847, 0.31651, 0.46807, 0.48458, 0.58858, 0.60441, 0.61247},
            new double[] {0.0334, 0.287, 0.297, 0.827, 0.478, 0.0319, 0.0452, 0.082, 0.0534}, 1.00, null),
    /**
     * Плутоний-238 в PuO₂: 0,568 Вт/г чистого изотопа. Нейтроны — 1,5·10⁴ н/(с·г) Pu-238 в оксиде природного кислорода
     * (спонтанное деление ≈ 2,6·10³ плюс (α,n) на O-17/O-18) при удельной активности 6,34·10¹¹ Бк/г → 2,4·10⁻⁸ н/распад.
     */
    PU238(87.7 * Times.Y, new double[0], new double[0], 5.593, null, 1.5e4 / 6.34e11),
    /**
     * Азот-16 — активация кислорода воды O-16(n,p)N-16 в зоне: T½ 7,13 с, жёсткие γ 6,129 МэВ (67 %), 7,115 МэВ (4,9 %),
     * 2,742 МэВ (0,8 %) (ENSDF). У кипящего реактора уходит с паром в турбину — машинный зал на мощности под излучением.
     * Тепло — средняя энергия β (≈ 2,4 МэВ) плюс γ.
     */
    N16(7.13, new double[] {6.129, 7.115, 2.742}, new double[] {0.67, 0.049, 0.008}, 6.9, null);

    public final double halfLifeS;
    /** Средняя энергия распада, уходящая в тепло, МэВ. */
    public final double heatMeV;
    /** Дочерний нуклид или null. */
    public final Emitter daughter;
    /** Быстрых нейтронов на распад (спектр — как у деления, {@link PointSource#neutrons}). */
    public final double neutronsPerDecay;
    private final double[] energies, yields;

    Emitter(double halfLifeS, double[] energies, double[] yields, double heatMeV, Emitter daughter) {
        this(halfLifeS, energies, yields, heatMeV, daughter, 0);
    }

    Emitter(double halfLifeS, double[] energies, double[] yields, double heatMeV, Emitter daughter, double neutronsPerDecay) {
        this.halfLifeS = halfLifeS;
        this.energies = energies;
        this.yields = yields;
        this.heatMeV = heatMeV;
        this.daughter = daughter;
        this.neutronsPerDecay = neutronsPerDecay;
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
        return new PointSource(energies.clone(), p, bq * neutronsPerDecay);
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
