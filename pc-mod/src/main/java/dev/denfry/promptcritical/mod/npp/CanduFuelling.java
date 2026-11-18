package dev.denfry.promptcritical.mod.npp;

import dev.denfry.promptcritical.sim.npp.CanduReactor;

/**
 * Ход перегрузочной машины CANDU для рендера и эффектов: решётка концевой арматуры на торце и путь машины к каналу.
 *
 * <p>Решётка — та же, что в арте (art/blockbench/core/candu_reactor.author.js): шаг 3 пикселя модели вокруг x 8, y 9,
 * узлы (i, j) с i² + j² ≤ 30 — ровно {@value CanduReactor#CHANNELS} каналов, по рядам сверху вниз, с запада на восток
 * (так нумеруют каналы CANDU: ряды A…W, столбцы 1…22). Настоящая решётка CANDU-6 — 380 каналов с шагом 28,6 см; в
 * игре их {@value CanduReactor#CHANNELS}, и шаг в арте выбран так, чтобы торец показывал их все.
 *
 * <p>Цикл после перегрузки: {@link #TRAVEL} тиков машина едет от стоянки к каналу, {@link #DWELL} стоит у него
 * (захват закрыт, хобот вдвинут на {@link #SNOUT_PX}), {@link #TRAVEL} возвращается. На CANDU-6 машина работает у
 * канала около часа; здесь цикл сжат до 16 с, как и вся перегрузка в игре — одним нажатием.
 */
public final class CanduFuelling {
    public static final int TRAVEL = 80, DWELL = 160, CYCLE = 2 * TRAVEL + DWELL;
    public static final double PITCH = 3, SNOUT_PX = 0.5;
    /** Стоянка машины (смещение от оси x 8, y 9): внизу у западной колонны. */
    public static final double HOME_X = -12, HOME_Y = -18;
    private static final int[][] LATTICE = lattice();

    private CanduFuelling() {
    }

    private static int[][] lattice() {
        int[][] out = new int[CanduReactor.CHANNELS][];
        int k = 0;
        for (int j = 6; j >= -6; j--)
            for (int i = -6; i <= 6; i++) if (i * i + j * j <= 30) out[k++] = new int[] {i, j};
        if (k != CanduReactor.CHANNELS) throw new IllegalStateException("CANDU lattice has " + k + " nodes");
        return out;
    }

    /** Узел решётки канала {@code ch}: (i, j) в шагах от оси. */
    public static int[] node(int ch) {
        return LATTICE[Math.floorMod(ch, LATTICE.length)];
    }

    /**
     * Смещение машины от положения в модели (x, y, z в пикселях) через {@code t} тиков после перегрузки канала
     * {@code ch}; {@code ch < 0} — перегрузок не было, машина на стоянке.
     */
    public static double[] offset(int ch, double t) {
        if (ch < 0 || t < 0 || t >= CYCLE) return new double[] {HOME_X, HOME_Y, 0};
        int[] n = node(ch);
        double tx = n[0] * PITCH, ty = n[1] * PITCH;
        double s;
        if (t < TRAVEL) s = smooth(t / TRAVEL);
        else if (t < TRAVEL + DWELL) s = 1;
        else s = 1 - smooth((t - TRAVEL - DWELL) / TRAVEL);
        double snout = t >= TRAVEL + 10 && t < TRAVEL + DWELL - 10 ? SNOUT_PX : 0;
        return new double[] {HOME_X + (tx - HOME_X) * s, HOME_Y + (ty - HOME_Y) * s, snout};
    }

    /** Машина едет (моторы моста и тележки работают). */
    public static boolean moving(int ch, double t) {
        return ch >= 0 && t >= 0 && t < CYCLE && (t < TRAVEL || t >= TRAVEL + DWELL);
    }

    /** Машина стоит у канала с вдвинутым хоботом. */
    public static boolean latched(int ch, double t) {
        return ch >= 0 && t >= TRAVEL + 10 && t < TRAVEL + DWELL - 10;
    }

    private static double smooth(double x) {
        return x * x * (3 - 2 * x);
    }
}
