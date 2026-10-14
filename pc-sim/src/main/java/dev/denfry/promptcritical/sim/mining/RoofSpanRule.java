package dev.denfry.promptcritical.sim.mining;

/**
 * Правило пролёта кровли (08 §8.2.2). Блок кровли поддержан, если пролёт выработки через него не больше
 * допустимого L_adm; пролёт — число подряд открытых блоков кровли через данный по оси X и по оси Z, берётся
 * меньший (выработка шириной 3 и длиной 100 имеет пролёт 3). Крепь в самом блоке или в соседнем (по 8
 * направлениям) прибавляет к L_adm свою прибавку; сгнившая наполовину деревянная крепь прибавки не даёт
 * [допущение, в GDD нет]. При L &gt; L_adm запускается таймер {@link RoofRock#collapseDelayS}, затем обрушение
 * купола высотой 0,5(L − L_adm) + 1.
 */
public final class RoofSpanRule {
    /** Дальше этого пролёт не сканируется: такой пролёт заведомо больше любого допустимого. */
    public static final int MAX_SCAN = 32;
    /** Ниже этого состояния крепь не даёт прибавки. */
    public static final double MIN_SUPPORT_CONDITION = 0.5;

    /** Кровля в плане: открытые клетки и крепь. Координаты — блоки горизонта кровли. */
    public interface RoofGrid {
        /** Над клеткой открытая кровля (под ней воздух выработки). */
        boolean open(int x, int z);

        /** Крепь в клетке или {@code null}. */
        MineSupport support(int x, int z);

        /** Состояние крепи клетки, 0–1. */
        double supportCondition(int x, int z);
    }

    private RoofSpanRule() {}

    /** Пролёт через клетку, блоков (0 — клетка не открыта). */
    public static int span(RoofGrid g, int x, int z) {
        if (!g.open(x, z)) return 0;
        return Math.min(run(g, x, z, 1, 0), run(g, x, z, 0, 1));
    }

    private static int run(RoofGrid g, int x, int z, int dx, int dz) {
        int n = 1;
        for (int k = 1; k < MAX_SCAN && g.open(x + k * dx, z + k * dz); k++) n++;
        for (int k = 1; k < MAX_SCAN && n < MAX_SCAN && g.open(x - k * dx, z - k * dz); k++) n++;
        return n;
    }

    /** Допустимый пролёт клетки с учётом крепи рядом, блоков. */
    public static int admissibleSpan(RoofGrid g, int x, int z, RoofRock rock, MiningConfig cfg) {
        int bonus = 0;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                MineSupport s = g.support(x + dx, z + dz);
                if (s != null && g.supportCondition(x + dx, z + dz) >= MIN_SUPPORT_CONDITION)
                    bonus = Math.max(bonus, s.spanBonus);
            }
        return cfg.roofSpan(rock) + bonus;
    }

    /** Кровля над клеткой держится. */
    public static boolean supported(RoofGrid g, int x, int z, RoofRock rock, MiningConfig cfg) {
        return span(g, x, z) <= admissibleSpan(g, x, z, rock, cfg);
    }

    /** Высота купола обрушения 0,5(L − L_adm) + 1, блоков (с округлением вверх); 0, если пролёт допустим. */
    public static int collapseHeight(int span, int admissible) {
        if (span <= admissible) return 0;
        return (int) Math.ceil(0.5 * (span - admissible) + 1);
    }
}
