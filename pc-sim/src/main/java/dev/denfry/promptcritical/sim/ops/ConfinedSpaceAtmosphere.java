package dev.denfry.promptcritical.sim.ops;

import java.util.Arrays;

/**
 * Атмосфера замкнутого пространства (13 §13.2.6): хорошо перемешанный объём {@code V} с мольными долями газов,
 *
 * <pre>V·dxᵢ/dt = (ṅ_in,i − xᵢ·ṅ_in)/c + Q_v·(xᵢ^air − xᵢ)</pre>
 *
 * c = p/(RT) = {@value #MOLAR_DENSITY} моль/м³ при 101 кПа и 300 K; ṅ_in,i — притоки (продувка азотом, газ через
 * штуцер, протечки); Q_v — вентиляция, м³/с: естественная через открытый люк {@value #NATURAL_VENT_M3S} м³/с на
 * проём [BALANCE] или переносной вентилятор {@value #VENTILATOR_M3S} м³/с. Азот здесь вместе с аргоном (оба —
 * простые удушающие газы). Шаг устойчив при любом dt: интегрирование подшагами с долей обмена не больше 0,2.
 */
public final class ConfinedSpaceAtmosphere {
    public enum Gas { O2, N2, CO2, CH4, H2S, CO }

    private static final int N = Gas.values().length;
    /** Сухой воздух: O₂, N₂+Ar, CO₂. */
    static final double[] AIR = {0.2095, 0.7901, 0.0004, 0, 0, 0};
    /** Мольная плотность газа при 101 кПа и 300 K, моль/м³ (§13.2.6). */
    public static final double MOLAR_DENSITY = 40.6;
    /** Естественная вентиляция через проём, м³/с (§13.2.6, {@code ops.natural_vent_m3s}) [BALANCE]. */
    public static final double NATURAL_VENT_M3S = 0.002;
    /** Принудительная вентиляция переносным вентилятором, м³/с (§13.2.6). */
    public static final double VENTILATOR_M3S = 0.5;
    /** Коэффициент неидеальности перемешивания при вентиляции ({@code ops.vent_mixing_factor}). */
    public static final double MIXING_FACTOR = 3;
    /** Наибольший объём замкнутого пространства, блоков ({@code ops.confined_max_volume}). */
    public static final int MAX_VOLUME = 2048;
    /** НКПР метана, мольная доля (5 %, как у термокаталитического датчика мода, {@code ProcessSources}). */
    public static final double CH4_LEL = 0.05;
    /** Безопасная доля кислорода для входа (§13.2.3, {@code ops.gas_limits.o2_min}). */
    public static final double O2_SAFE = 0.195;

    private final double volumeM3;
    private final double[] x;

    public ConfinedSpaceAtmosphere(double volumeM3) {
        this(volumeM3, AIR);
    }

    public ConfinedSpaceAtmosphere(double volumeM3, double[] fractions) {
        if (volumeM3 <= 0) throw new IllegalArgumentException("volume " + volumeM3);
        this.volumeM3 = volumeM3;
        this.x = Arrays.copyOf(fractions, N);
    }

    public double volumeM3() {
        return volumeM3;
    }

    public double fraction(Gas g) {
        return x[g.ordinal()];
    }

    public double ppm(Gas g) {
        return x[g.ordinal()] * 1e6;
    }

    /** Доля НКПР по метану. */
    public double lelFraction() {
        return fraction(Gas.CH4) / CH4_LEL;
    }

    /** Мольные доли (копия), в порядке {@link Gas}. */
    public double[] fractions() {
        return x.clone();
    }

    /**
     * {@code dtS} секунд: притоки {@code inflowMolS} (моль/с по {@link Gas}, может быть {@code null}) и вентиляция
     * {@code ventM3s}.
     */
    public void step(double dtS, double[] inflowMolS, double ventM3s) {
        double nIn = 0;
        if (inflowMolS != null) for (double v : inflowMolS) nIn += Math.max(0, v);
        double exchange = (nIn / MOLAR_DENSITY + Math.max(0, ventM3s)) / volumeM3;
        int sub = Math.max(1, (int) Math.ceil(exchange * dtS / 0.2));
        double h = dtS / sub;
        double[] dx = new double[N];
        for (int s = 0; s < sub; s++) {
            for (int i = 0; i < N; i++) {
                double in = inflowMolS == null ? 0 : Math.max(0, inflowMolS[i]);
                dx[i] = ((in - x[i] * nIn) / MOLAR_DENSITY + ventM3s * (AIR[i] - x[i])) / volumeM3;
            }
            double sum = 0;
            for (int i = 0; i < N; i++) sum += x[i] = Math.max(0, x[i] + dx[i] * h);
            if (sum > 0) for (int i = 0; i < N; i++) x[i] /= sum;
        }
    }

    /**
     * Сколько объёмов воздуха нужно продуть, чтобы поднять O₂ с {@code o2From} до {@code o2To}, с коэффициентом
     * перемешивания {@value #MIXING_FACTOR} (§13.2.6: с 5 % до 19,5 % — 2,4 объёма идеально, 7,3 с запасом).
     */
    public static double airChanges(double o2From, double o2To) {
        double air = AIR[Gas.O2.ordinal()];
        return MIXING_FACTOR * Math.log((air - o2From) / (air - o2To));
    }

    /** Время вентиляции до безопасного кислорода, с; {@code Double.POSITIVE_INFINITY} — вентиляции нет. */
    public double ventilationTimeS(double ventM3s) {
        double o2 = fraction(Gas.O2);
        if (o2 >= O2_SAFE) return 0;
        return ventM3s <= 0 ? Double.POSITIVE_INFINITY : airChanges(o2, O2_SAFE) * volumeM3 / ventM3s;
    }
}
