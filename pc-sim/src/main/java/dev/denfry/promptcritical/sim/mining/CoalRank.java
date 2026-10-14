package dev.denfry.promptcritical.sim.mining;

/**
 * Марка угля по ГОСТ 25543 (08 §8.1.4): по выходу летучих V^daf и толщине пластического слоя y. Смежные марки в
 * стандарте перекрываются; здесь — упрощённое однозначное разбиение [границы сверить с ГОСТ 25543-2013].
 */
public enum CoalRank {
    /** бурый: V^daf &gt; 42 % */
    B,
    /** длиннопламенный: 33–42 %, y &lt; 6 мм */
    D,
    /** газовый: 33–42 %, y 6–16 мм */
    G,
    /** жирный: основа шихты коксования */
    ZH,
    /** коксовый */
    K,
    /** отощённый спекающийся */
    OS,
    /** тощий */
    T,
    /** антрацит: V^daf &lt; 8 % */
    A;

    /**
     * Марка по выходу летучих и пластическому слою.
     *
     * @param volatileDaf    V^daf, доля
     * @param plasticLayerMm y, мм
     */
    public static CoalRank classify(double volatileDaf, double plasticLayerMm) {
        double v = volatileDaf * 100, y = plasticLayerMm;
        if (v > 42) return B;
        if (v < 8) return A;
        if (v >= 33) return y >= 16 ? ZH : y >= 6 ? G : D;
        if (v >= 18) {
            if (y >= 12) return v >= 27 ? ZH : K;
            return y >= 6 ? OS : T;
        }
        return y >= 6 ? OS : T;
    }

    /** Коксующиеся марки — основа шихты (08 §8.4.2). */
    public boolean coking() {
        return this == ZH || this == K || this == G || this == OS;
    }
}
