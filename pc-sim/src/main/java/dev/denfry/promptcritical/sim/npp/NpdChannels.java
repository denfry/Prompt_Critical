package dev.denfry.promptcritical.sim.npp;

import java.util.ArrayList;
import java.util.List;

/**
 * Решётка каналов NPD на торце: квадратная сетка шагом 260,35 мм со смещением на полшага от оси каландра, 132 узла,
 * ближайшие к оси первыми, — те же 132 узла, что в модели (art/blockbench/core/candu_npd/candu_npd.author.js), и
 * радиусы по возрастанию, как в {@link LightWaterCore}. Номер канала — индекс в этом порядке (порядок внутри кольца
 * у модели может отличаться: там он задаёт только цвет фидеров; канал 0 в обоих — узел (−1, −1)).
 *
 * <p>Течение в соседних каналах встречное (CANTEACH, «CANDU Origins and Evolution»: отсюда двусторонняя перегрузка):
 * по шахматному порядку свежие пучки вводятся то с северного, то с южного торца — всегда по течению.
 */
public final class NpdChannels {
    public static final double PITCH_M = 0.26035;
    private static final int[][] NODES = nodes();

    private NpdChannels() {
    }

    private static int[][] nodes() {
        // Расстояние — точным целым (2i+1)² + (2j+1)²: на краю 132 узлов два семейства лежат на одном радиусе
        // (5½, 3½) и (6½, ½) шага, и сравнение плавающих hypot выбирало между ними по шуму округления — решётка
        // выходила несимметричной и расходилась с моделью. При равном радиусе ближе узел с меньшей наибольшей
        // координатой: так берётся восьмёрка (5½, 3½), как в модели, и зона симметрична. Дальше — i снаружи, j внутри.
        List<int[]> pts = new ArrayList<>();
        for (int i = -8; i < 8; i++)
            for (int j = -8; j < 8; j++) {
                int a = 2 * i + 1, b = 2 * j + 1;
                pts.add(new int[] {i, j, a * a + b * b, Math.max(Math.abs(a), Math.abs(b))});
            }
        pts.sort((u, v) -> u[2] != v[2] ? Integer.compare(u[2], v[2]) : Integer.compare(u[3], v[3]));
        int[][] out = new int[NpdReactor.CHANNELS][];
        for (int k = 0; k < out.length; k++) out[k] = new int[] {pts.get(k)[0], pts.get(k)[1]};
        return out;
    }

    /** Узел канала: (i, j) — центр в (i + ½, j + ½) шагов от оси, j вверх. */
    public static int[] node(int ch) {
        return NODES[Math.floorMod(ch, NODES.length)];
    }

    /** Смещение центра канала от оси каландра, м: x (на восток, если смотреть на северный торец), y вверх. */
    public static double x(int ch) {
        return (node(ch)[0] + 0.5) * PITCH_M;
    }

    public static double y(int ch) {
        return (node(ch)[1] + 0.5) * PITCH_M;
    }

    /** Свежее топливо идёт с северного торца (иначе — с южного): шахматный порядок встречного течения. */
    public static boolean fuelledFromNorth(int ch) {
        int[] n = node(ch);
        return ((n[0] + n[1]) & 1) == 0;
    }

    /** Обозначение канала как на CANDU: ряд буквой сверху вниз (A…), столбец числом с запада (1…). */
    public static String label(int ch) {
        int[] n = node(ch);
        int row = 7 - n[1], col = n[0] + 8 + 1;
        return (char) ('A' + row) + (col < 10 ? "0" : "") + col;
    }
}
