package dev.denfry.promptcritical.sim.automation.historian;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import org.junit.jupiter.api.Test;

/** Историан 03d §3d.9.5: ошибка восстановления не больше 2E_c (E_c — дверь, плюс фильтр исключений), технологический тренд сжимается в разы, агрегаты. */
class TagArchiveTest {
    /** Медленный тренд с небольшим шумом и ступенькой, 1 Гц, час. */
    private static double signal(int s) {
        return 50 + 10 * Math.sin(s / 600.0) + (s > 1800 ? 15 : 0) + 0.05 * Math.sin(s * 1.7);
    }

    @Test
    void reconstructionStaysWithinTheCompressionDeviation() {
        TagArchive a = new TagArchive(0.25, 20_000);
        for (int s = 0; s <= 3600; s++) a.add(s, signal(s));
        double worst = 0;
        for (int s = 0; s < 3600; s++) worst = Math.max(worst, Math.abs(a.valueAt(s) - signal(s)));
        // дверь даёт ≤ E_c на прошедших фильтр точках; отброшенные фильтром исключений на изломах — до 2E_c
        assertThat(worst).as("max reconstruction error").isLessThanOrEqualTo(2 * 0.25);
        double ratio = (double) a.received() / a.size();
        assertThat(ratio).as("compression ratio").isGreaterThan(10);
    }

    @Test
    void aggregatesOverAnInterval() {
        TagArchive a = new TagArchive(0.01, 1000);
        for (int s = 0; s <= 100; s++) a.add(s, s < 50 ? 0 : 10);
        assertThat(a.aggregate(0, 100, "avg")).isCloseTo(5, within(0.3));
        assertThat(a.aggregate(0, 100, "max")).isCloseTo(10, within(1e-6));
        assertThat(a.aggregate(0, 100, "integral")).isCloseTo(500, within(30.0));
    }

    @Test
    void ringKeepsTheNewestPointsAndSurvivesSaveAndLoad() {
        TagArchive a = new TagArchive(0, 8);
        for (int s = 0; s < 100; s++) a.add(s, s % 2 == 0 ? 0 : 1);
        assertThat(a.size()).isEqualTo(8);
        assertThat(a.firstT()).isGreaterThan(80);
        MutableStateTree tree = new MutableStateTree();
        a.save(new TreeStateWriter(tree));
        TagArchive b = new TagArchive(0, 8);
        b.load(new TreeStateReader(tree));
        assertThat(b.size()).isEqualTo(8);
        assertThat(b.valueAt(b.firstT())).isEqualTo(a.valueAt(a.firstT()));
    }
}
