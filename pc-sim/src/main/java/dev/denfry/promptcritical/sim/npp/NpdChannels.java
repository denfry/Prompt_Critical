package dev.denfry.promptcritical.sim.npp;

import java.util.ArrayList;
import java.util.List;

/**
 * Решётка каналов NPD на торце: квадратная сетка шагом 260,35 мм со смещением на полшага от оси каландра, 132 узла,
 * ближайшие к оси первыми, — так же, как в модели (art/blockbench/core/candu_npd/candu_npd.author.js) и в
 * {@link LightWaterCore} (радиусы по возрастанию). Номер канала — индекс в этом порядке.
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
        // тот же порядок, что в скрипте модели: i снаружи, j внутри, устойчивая сортировка по расстоянию от оси
        double p = PITCH_M * 16;
        List<double[]> pts = new ArrayList<>();
        for (int i = -8; i < 8; i++)
            for (int j = -8; j < 8; j++) pts.add(new double[] {i, j, Math.hypot((i + 0.5) * p, (j + 0.5) * p)});
        pts.sort((a, b) -> Double.compare(a[2], b[2]));
        int[][] out = new int[NpdReactor.CHANNELS][];
        for (int k = 0; k < out.length; k++) out[k] = new int[] {(int) pts.get(k)[0], (int) pts.get(k)[1]};
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
