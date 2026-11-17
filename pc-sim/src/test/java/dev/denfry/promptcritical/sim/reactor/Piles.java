package dev.denfry.promptcritical.sim.reactor;

import java.util.SplittableRandom;

/** Тестовые кладки: куб топлива {@code fuel}³ в графитовом отражателе {@code reflector}, стержни в колоннах. */
final class Piles {
    static final long CONTROL_KEY = 1, ZIP_KEY = 2;

    private Piles() {}

    /**
     * Кладка: топливо с загрузкой {@code load}, Ra-Be источник в центре нижнего слоя отражателя, счётчик у грани.
     * Аварийный стержень — в центральной колонне (вес стержня ~φ²), регулирующий — рядом.
     */
    static PileLayout cube(int fuel, int reflector, int load, boolean control, boolean zip, boolean source) {
        int n = fuel + 2 * reflector, c = n / 2;
        PileLayout.Builder b = PileLayout.builder();
        int[] controlCol = new int[n], zipCol = new int[n];
        for (int y = n - 1; y >= 0; y--) {
            for (int z = 0; z < n; z++) {
                for (int x = 0; x < n; x++) {
                    boolean inFuel = x >= reflector && x < reflector + fuel && y >= reflector && y < reflector + fuel
                            && z >= reflector && z < reflector + fuel;
                    PileLayout.NodeKind k;
                    if (control && x == c - 1 && z == c) k = PileLayout.NodeKind.ROD_CHANNEL;
                    else if (zip && x == c && z == c) k = PileLayout.NodeKind.ROD_CHANNEL;
                    else if (source && x == c && z == c - 1 && y == 0) k = PileLayout.NodeKind.SOURCE;
                    else k = inFuel ? PileLayout.NodeKind.FUEL : PileLayout.NodeKind.GRAPHITE;
                    int i = b.addNode(x, y, z, k, k == PileLayout.NodeKind.FUEL ? load : 0);
                    if (control && x == c - 1 && z == c) controlCol[n - 1 - y] = i;
                    if (zip && x == c && z == c) zipCol[n - 1 - y] = i;
                }
            }
        }
        if (control) b.addRod(CONTROL_KEY, PileLayout.RodKind.CONTROL, controlCol);
        if (zip) b.addRod(ZIP_KEY, PileLayout.RodKind.ZIP, zipCol);
        b.addDetector(10, b.indexOf(0, c, c));
        return b.build();
    }

    static GraphitePile pile(PileLayout layout) {
        GraphitePile p = new GraphitePile();
        p.form(layout);
        return p;
    }

    static void run(GraphitePile p, double seconds, SplittableRandom r) {
        run(p, seconds, r, PileEvents.NONE);
    }

    static void run(GraphitePile p, double seconds, SplittableRandom r, PileEvents events) {
        int ticks = (int) Math.round(seconds / GraphitePile.DT);
        for (int i = 0; i < ticks; i++) p.step(i % 20 == 0, r, events);
    }
}
