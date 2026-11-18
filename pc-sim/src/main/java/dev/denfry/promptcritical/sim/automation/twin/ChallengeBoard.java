package dev.denfry.promptcritical.sim.automation.twin;

import dev.denfry.promptcritical.sim.automation.twin.Challenge.Metric;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Таблица лидеров задач наладчика (03d §3d.11.4): по каждой задаче и метрике — лучший результат каждого игрока, место,
 * десятка лучших и гистограмма. Меньше — лучше; ничьи — по времени отправки (раньше — выше).
 *
 * <p><b>Эталонное распределение.</b> «Верхние 10 % сервера» (достижение {@code tuner}, 11) на пустом сервере или в
 * одиночной игре не имеют смысла, поэтому к результатам игроков всегда добавляется {@value #REFERENCE_COUNT}
 * эталонных: логнормальное распределение с медианой {@link Metric#par()} (результат решения из справочника со
 * стандартными настройками) и σ = {@value #REFERENCE_SIGMA}, квантили (k − 0,5)/{@value #REFERENCE_COUNT} (нормальный
 * квантиль — логистическое приближение z = ln(p/(1 − p))·√3/π). Эталонные записи «отправлены» раньше всех и выигрывают
 * ничьи. Одиночному игроку для верхних 10 % (2-е место из 20) нужно быть лучше справочного решения примерно на 30 %;
 * на сервере с игроками порог сдвигается их результатами. Решение — наше: GDD распределение не задаёт.
 */
public final class ChallengeBoard {
    public static final int REFERENCE_COUNT = 19, TOP = 10;
    public static final double REFERENCE_SIGMA = 0.25, TOP_FRACTION = 0.10;
    /** Имя эталонных записей. */
    public static final String REFERENCE = "reference";

    /** Результат: игрок, значение, время отправки (тик сервера; эталон — −1). */
    public record Entry(String player, double value, long time) {
    }

    /** Место отправленного результата: 1 — лучший; {@code total} — участников вместе с эталоном. */
    public record Placement(int rank, int total, boolean personalBest) {
        public boolean topTenth() {
            return rank <= Math.max(1, (int) Math.ceil(TOP_FRACTION * total));
        }
    }

    private static final Comparator<Entry> ORDER = Comparator.comparingDouble(Entry::value).thenComparingLong(Entry::time);
    private final Map<String, List<Entry>> best = new LinkedHashMap<>();

    private static String key(String challenge, String metric) {
        return challenge + "/" + metric;
    }

    /** Эталонные значения метрики по возрастанию. */
    public static double[] reference(Metric m) {
        double[] out = new double[REFERENCE_COUNT];
        for (int k = 0; k < REFERENCE_COUNT; k++) {
            double p = (k + 0.5) / REFERENCE_COUNT;
            double z = Math.log(p / (1 - p)) * Math.sqrt(3) / Math.PI;
            out[k] = m.par() * Math.exp(REFERENCE_SIGMA * z);
        }
        return out;
    }

    /** Отправить результат: хранится лучший результат игрока; место — среди эталона и лучших результатов игроков. */
    public Placement submit(Challenge c, Metric m, String player, double value, long time) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("metric value " + value);
        List<Entry> list = best.computeIfAbsent(key(c.id(), m.id()), k -> new ArrayList<>());
        Entry mine = new Entry(player, value, time);
        Entry old = null;
        for (Entry e : list) if (e.player().equals(player)) old = e;
        boolean pb = old == null || ORDER.compare(mine, old) < 0;
        if (pb) {
            if (old != null) list.remove(old);
            list.add(mine);
            list.sort(ORDER);
        }
        int better = 0, total = REFERENCE_COUNT + 1;
        for (double r : reference(m)) if (r <= value) better++;
        for (Entry e : list) {
            if (e.player().equals(player)) continue;
            total++;
            if (ORDER.compare(e, mine) < 0) better++;
        }
        return new Placement(better + 1, total, pb);
    }

    /** Лучшие {@link #TOP} игроков (без эталона). */
    public List<Entry> top(String challenge, String metric) {
        List<Entry> list = best.getOrDefault(key(challenge, metric), List.of());
        return List.copyOf(list.subList(0, Math.min(TOP, list.size())));
    }

    /** Все лучшие результаты игроков (для сохранения). */
    public Map<String, List<Entry>> entries() {
        return best;
    }

    /** Восстановить запись из сохранения. */
    public void restore(String challenge, String metric, Entry e) {
        List<Entry> list = best.computeIfAbsent(key(challenge, metric), k -> new ArrayList<>());
        list.removeIf(x -> x.player().equals(e.player()));
        list.add(e);
        list.sort(ORDER);
    }

    /** Сбросить таблицу задачи (команда администратора). */
    public void reset(String challenge) {
        best.keySet().removeIf(k -> k.startsWith(challenge + "/"));
    }

    /**
     * Гистограмма результатов игроков и эталона: {@code bins} корзин от минимума до максимума, как у головоломок
     * Zachtronics.
     */
    public int[] histogram(Challenge c, Metric m, int bins) {
        List<Double> all = new ArrayList<>();
        for (double r : reference(m)) all.add(r);
        for (Entry e : best.getOrDefault(key(c.id(), m.id()), List.of())) all.add(e.value());
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (double v : all) {
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
        }
        int[] h = new int[bins];
        for (double v : all) h[hi > lo ? Math.min(bins - 1, (int) ((v - lo) / (hi - lo) * bins)) : 0]++;
        return h;
    }
}
