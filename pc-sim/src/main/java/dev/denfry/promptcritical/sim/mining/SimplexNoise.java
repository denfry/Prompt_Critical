package dev.denfry.promptcritical.sim.mining;

/**
 * Трёхмерный simplex-шум (Густавсон) с перестановкой от сида, нормированный к единичной дисперсии, как требует
 * 02 §2.1.3. Только сложение, умножение и {@code floor} — результат совпадает бит в бит на любом потоке (T8.2).
 * Неизменяемый, потокобезопасный.
 */
public final class SimplexNoise {
    /**
     * Делитель к единичному σ: стандартное отклонение сырого 3D simplex (с множителем 32) по выборке 10⁶ точек
     * (проверяется тестом). Для OpenSimplex2 в 02 §2.1.3 указано 0,35; у классического simplex — своё.
     */
    static final double SIGMA_RAW = 0.4254;

    private static final double F3 = 1.0 / 3, G3 = 1.0 / 6;
    private static final int[][] GRAD = {
            {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0}, {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
            {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}};

    private final short[] perm = new short[512];
    private final double scale;

    /**
     * @param seed       сид
     * @param scaleBlocks масштаб шума, блоков (длина волны порядка масштаба)
     */
    public SimplexNoise(long seed, double scaleBlocks) {
        if (!(scaleBlocks > 0)) throw new IllegalArgumentException("scaleBlocks: " + scaleBlocks);
        this.scale = 1 / scaleBlocks;
        short[] p = new short[256];
        for (int i = 0; i < 256; i++) p[i] = (short) i;
        long s = seed;
        for (int i = 255; i > 0; i--) {
            s = WorldHash.mix(s + 0x9E3779B97F4A7C15L);
            int j = (int) Long.remainderUnsigned(s, i + 1);
            short t = p[i];
            p[i] = p[j];
            p[j] = t;
        }
        for (int i = 0; i < 512; i++) perm[i] = p[i & 255];
    }

    /** N(x) с σ ≈ 1 в точке блока (центр блока). */
    public double sample(int x, int y, int z) {
        return raw((x + 0.5) * scale, (y + 0.5) * scale, (z + 0.5) * scale) / SIGMA_RAW;
    }

    /** Сырой simplex в диапазоне ≈ [−1, 1]. */
    double raw(double xin, double yin, double zin) {
        double s = (xin + yin + zin) * F3;
        int i = (int) Math.floor(xin + s), j = (int) Math.floor(yin + s), k = (int) Math.floor(zin + s);
        double t = (i + j + k) * G3;
        double x0 = xin - (i - t), y0 = yin - (j - t), z0 = zin - (k - t);
        int i1, j1, k1, i2, j2, k2;
        if (x0 >= y0) {
            if (y0 >= z0) { i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 1; k2 = 0; }
            else if (x0 >= z0) { i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 0; k2 = 1; }
            else { i1 = 0; j1 = 0; k1 = 1; i2 = 1; j2 = 0; k2 = 1; }
        } else {
            if (y0 < z0) { i1 = 0; j1 = 0; k1 = 1; i2 = 0; j2 = 1; k2 = 1; }
            else if (x0 < z0) { i1 = 0; j1 = 1; k1 = 0; i2 = 0; j2 = 1; k2 = 1; }
            else { i1 = 0; j1 = 1; k1 = 0; i2 = 1; j2 = 1; k2 = 0; }
        }
        double x1 = x0 - i1 + G3, y1 = y0 - j1 + G3, z1 = z0 - k1 + G3;
        double x2 = x0 - i2 + 2 * G3, y2 = y0 - j2 + 2 * G3, z2 = z0 - k2 + 2 * G3;
        double x3 = x0 - 1 + 3 * G3, y3 = y0 - 1 + 3 * G3, z3 = z0 - 1 + 3 * G3;
        int ii = i & 255, jj = j & 255, kk = k & 255;
        double n = corner(perm[ii + perm[jj + perm[kk]]], x0, y0, z0)
                + corner(perm[ii + i1 + perm[jj + j1 + perm[kk + k1]]], x1, y1, z1)
                + corner(perm[ii + i2 + perm[jj + j2 + perm[kk + k2]]], x2, y2, z2)
                + corner(perm[ii + 1 + perm[jj + 1 + perm[kk + 1]]], x3, y3, z3);
        return 32 * n;
    }

    private static double corner(int h, double x, double y, double z) {
        double t = 0.6 - x * x - y * y - z * z;
        if (t < 0) return 0;
        int[] g = GRAD[h % 12];
        t *= t;
        return t * t * (g[0] * x + g[1] * y + g[2] * z);
    }
}
