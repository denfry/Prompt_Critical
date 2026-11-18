package dev.denfry.promptcritical.sim.drilling;

import dev.denfry.promptcritical.sim.mining.WorldHash;

/**
 * Нефтегазоносность мира: на клетке карты {@link #CELL_BLOCKS}×{@link #CELL_BLOCKS} — не более одной антиклинальной
 * ловушки ({@link Trap}), целиком внутри клетки, так что ловушки соседних клеток не перекрываются и точку мира
 * описывает ловушка её клетки. Генерация детерминирована по сиду ({@link WorldHash}): генератор чанков, сейсмика и
 * буровая видят одни и те же пласты, сгенерирован чанк или нет.
 *
 * <p>Распределения (по порядку величин из Selley &amp; Sonnenberg, гл. 6–7; Dake, гл. 1): глубина свода 0,6–2,5 км
 * (мелкие чаще), амплитуда 20–120 м, радиус 48–120 м, мощность коллектора 8–40 м, пористость 10–30 %,
 * проницаемость 10 мД–3 Д (логнормально), заполнение купола 50–100 % амплитуды. Пустых ловушек (нарушенная покрышка)
 * 20 %, газовых 30 %, нефтяных 50 %. Давление: 60 % — гидростатическое 10,2 кПа/м (вода 1,04 г/см³), 30 % —
 * умеренное АВПД 10,8–12,5 кПа/м, 10 % — высокое 12,5–14 кПа/м.
 */
public final class PetroleumField {
    public static final int CELL_BLOCKS = 384;
    /** Наибольший радиус ловушки и отступ свода от края клетки, блоки. */
    public static final int MAX_RADIUS = 120;
    public static final double DEFAULT_PROBABILITY = 0.5;
    public static final double HYDROSTATIC_PA_M = 10_200;
    private static final int SALT = 0x0D1A;

    private final long seed;
    private final double probability;

    public PetroleumField(long seed, double probability) {
        if (!(probability >= 0 && probability <= 1)) throw new IllegalArgumentException("probability out of [0, 1]");
        this.seed = seed;
        this.probability = probability;
    }

    public PetroleumField(long seed) {
        this(seed, DEFAULT_PROBABILITY);
    }

    public static int cellOf(int block) {
        return Math.floorDiv(block, CELL_BLOCKS);
    }

    private double u(int cx, int cz, int k) {
        return WorldHash.unit(seed, cx, k, cz, SALT);
    }

    /** Ловушка клетки или {@code null}. */
    public Trap trap(int cx, int cz) {
        if (u(cx, cz, 0) >= probability) return null;
        double radius = 48 + 72 * u(cx, cz, 1);
        int span = CELL_BLOCKS - 2 * MAX_RADIUS;
        int crestX = cx * CELL_BLOCKS + MAX_RADIUS + (int) Math.floor(span * u(cx, cz, 2));
        int crestZ = cz * CELL_BLOCKS + MAX_RADIUS + (int) Math.floor(span * u(cx, cz, 3));
        double crest = 600 + 1900 * Math.pow(u(cx, cz, 4), 1.5);
        double closure = 20 + 100 * u(cx, cz, 5);
        double fill = 0.5 + 0.5 * u(cx, cz, 6);
        double thickness = 8 + 32 * u(cx, cz, 7);
        double porosity = 0.10 + 0.20 * u(cx, cz, 8);
        // проницаемость растёт с пористостью (Kozeny–Carman): общий ранг плюс разброс
        double logK = 1 + 2.5 * (0.6 * (porosity - 0.10) / 0.20 + 0.4 * u(cx, cz, 9));
        double f = u(cx, cz, 10);
        FormationFluid fluid = f < 0.2 ? FormationFluid.WATER : f < 0.5 ? FormationFluid.GAS : FormationFluid.OIL;
        double p = u(cx, cz, 11), q = u(cx, cz, 12);
        double gradient = p < 0.6 ? HYDROSTATIC_PA_M : p < 0.9 ? 10_800 + 1_700 * q : 12_500 + 1_500 * q;
        return new Trap(crestX, crestZ, radius, crest, closure,
                fluid.hydrocarbon() ? crest + fill * closure : Double.NaN, thickness, porosity, Math.pow(10, logK), fluid, gradient);
    }

    /** Ловушка клетки, в которой лежит точка; {@code null} — нет. */
    public Trap trapAt(int x, int z) {
        return trap(cellOf(x), cellOf(z));
    }

    /** Что вскроет скважина в точке (x, z): пласт клетки и глубина его кровли; {@code null} — коллектора нет. */
    public Prospect prospect(int x, int z) {
        Trap t = trapAt(x, z);
        return t == null ? null : new Prospect(t, t.topDepthM(x + 0.5, z + 0.5));
    }
}
