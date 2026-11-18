package dev.denfry.promptcritical.sim.automation.historian;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Архив одного тега историана (03d §3d.9.5): фильтр исключений и сжатие «вращающаяся дверь» (Bristol E. H., «Swinging
 * door trending: adaptive trend recording?», ISA, 1990), кольцевой буфер архивных точек.
 *
 * <p>Фильтр исключений: значение проходит дальше, если |y − y_last| > E_x (E_x = E_c/2) или прошло T_max. Дверь: от
 * последней архивной точки A строятся створки из шарниров (t_A, y_A ± E_c); пока наибольший наклон к верхнему шарниру
 * не превысил наименьший к нижнему, точки лежат в коридоре ±E_c от прямой из A и не архивируются; когда дверь
 * распахнулась, архивируется предыдущая точка, она становится новой A. Восстановление — линейная интерполяция: на
 * точках, прошедших фильтр, ошибка по построению не больше E_c, на отброшенных фильтром исключений у изломов — до 2E_c.
 * Время — секунды (физические, по часам историана).
 */
public final class TagArchive {
    /** Наибольший разрыв между архивными точками, с ({@code historian.max_gap_s}). */
    public static final double MAX_GAP_S = 600;

    private final double ec, ex;
    private final double[] t;
    private final float[] v;
    private int head, size;
    // дверь
    private boolean hasA;
    private double tA, yA, sUp = Double.NEGATIVE_INFINITY, sDown = Double.POSITIVE_INFINITY;
    private double tPrev = Double.NaN, yPrev;
    // фильтр исключений: последнее прошедшее значение и последнее отброшенное
    private double tLast = Double.NaN, yLast, tHeld = Double.NaN, yHeld;
    private long received;

    /**
     * @param ec       допуск сжатия E_c в единицах тега
     * @param capacity наибольшее число архивных точек (кольцо)
     */
    public TagArchive(double ec, int capacity) {
        if (!(ec >= 0) || capacity < 2) throw new IllegalArgumentException("ec >= 0, capacity >= 2");
        this.ec = ec;
        this.ex = ec / 2;
        this.t = new double[capacity];
        this.v = new float[capacity];
    }

    public double ec() {
        return ec;
    }

    public int size() {
        return size;
    }

    public long received() {
        return received;
    }

    /**
     * Новое значение тега в момент {@code time}. Фильтр исключений отбрасывает значения в пределах E_x от последнего
     * прошедшего; когда значение проходит, сначала в дверь идёт последнее отброшенное (иначе излом тренда терялся
     * бы между ними) — как в схеме исключение + дверь.
     */
    public void add(double time, double y) {
        if (Double.isNaN(y)) return;
        received++;
        if (!Double.isNaN(tLast) && Math.abs(y - yLast) <= ex && time - tLast < MAX_GAP_S) {
            tHeld = time;
            yHeld = y;
            return;
        }
        tLast = time;
        yLast = y;
        if (!Double.isNaN(tHeld) && tHeld < time) door(tHeld, yHeld);
        tHeld = Double.NaN;
        door(time, y);
    }

    /** Шаг «вращающейся двери» для значения, прошедшего фильтр исключений. */
    private void door(double time, double y) {
        if (!hasA) {
            archive(time, y);
            open(time, y);
            return;
        }
        double dt = time - tA;
        if (dt <= 0) return;
        double up = (y - (yA + ec)) / dt, down = (y - (yA - ec)) / dt;
        double nUp = Math.max(sUp, up), nDown = Math.min(sDown, down);
        if (nUp > nDown || dt >= MAX_GAP_S) {
            // дверь распахнулась: архивируется предыдущая точка, створки — от неё через текущую
            double ta = Double.isNaN(tPrev) ? time : tPrev, ya = Double.isNaN(tPrev) ? y : yPrev;
            archive(ta, ya);
            open(ta, ya);
            if (time > ta) {
                double d = time - ta;
                sUp = (y - (yA + ec)) / d;
                sDown = (y - (yA - ec)) / d;
            }
        } else {
            sUp = nUp;
            sDown = nDown;
        }
        tPrev = time;
        yPrev = y;
    }

    private void open(double time, double y) {
        hasA = true;
        tA = time;
        yA = y;
        sUp = Double.NEGATIVE_INFINITY;
        sDown = Double.POSITIVE_INFINITY;
        tPrev = Double.NaN;
    }

    private void archive(double time, double y) {
        int i = (head + size) % t.length;
        if (size == t.length) head = (head + 1) % t.length;
        else size++;
        t[i] = time;
        v[i] = (float) y;
    }

    /** Архивная точка {@code k} (0 — старейшая) с последней непринятой: {время, значение}. */
    private double tAt(int k) {
        return t[(head + k) % t.length];
    }

    private double vAt(int k) {
        return v[(head + k) % t.length];
    }

    /** Точки для запросов: архив и хвост — последняя принятая точка (она ещё не архивирована дверью). */
    private List<double[]> points() {
        List<double[]> out = new ArrayList<>(size + 1);
        for (int k = 0; k < size; k++) out.add(new double[] {tAt(k), vAt(k)});
        if (!Double.isNaN(tPrev) && (size == 0 || tPrev > tAt(size - 1))) out.add(new double[] {tPrev, yPrev});
        return out;
    }

    /** Сырые архивные точки в [t0, t1], не больше {@code max}. */
    public List<double[]> query(double t0, double t1, int max) {
        List<double[]> out = new ArrayList<>();
        for (double[] p : points()) if (p[0] >= t0 && p[0] <= t1 && out.size() < max) out.add(p);
        return out;
    }

    /** Значение в момент {@code time} линейной интерполяцией; NaN — вне архива. */
    public double valueAt(double time) {
        List<double[]> p = points();
        if (p.isEmpty() || time < p.get(0)[0]) return Double.NaN;
        for (int k = 1; k < p.size(); k++) {
            double[] a = p.get(k - 1), b = p.get(k);
            if (time <= b[0]) return b[0] == a[0] ? b[1] : a[1] + (b[1] - a[1]) * (time - a[0]) / (b[0] - a[0]);
        }
        return p.get(p.size() - 1)[1];
    }

    /** Ряд с шагом {@code step} в [t0, t1]. */
    public List<double[]> interpolate(double t0, double t1, double step, int max) {
        List<double[]> out = new ArrayList<>();
        if (!(step > 0)) return out;
        for (double x = t0; x <= t1 + 1e-9 && out.size() < max; x += step) {
            double y = valueAt(x);
            if (!Double.isNaN(y)) out.add(new double[] {x, y});
        }
        return out;
    }

    /** Агрегат за [t0, t1]: avg (по времени), min, max, count, integral, stdev; NaN — нет данных. */
    public double aggregate(double t0, double t1, String fn) {
        List<double[]> p = interpolate(t0, t1, Math.max((t1 - t0) / 512, 1e-6), 1024);
        if (p.isEmpty()) return Double.NaN;
        return switch (fn) {
            case "min" -> p.stream().mapToDouble(a -> a[1]).min().orElse(Double.NaN);
            case "max" -> p.stream().mapToDouble(a -> a[1]).max().orElse(Double.NaN);
            case "count" -> query(t0, t1, Integer.MAX_VALUE).size();
            case "integral", "avg", "stdev" -> {
                double integral = 0, span = 0;
                for (int k = 1; k < p.size(); k++) {
                    double d = p.get(k)[0] - p.get(k - 1)[0];
                    integral += d * (p.get(k)[1] + p.get(k - 1)[1]) / 2;
                    span += d;
                }
                if (fn.equals("integral")) yield integral;
                double avg = span > 0 ? integral / span : p.get(0)[1];
                if (fn.equals("avg")) yield avg;
                double s2 = 0;
                for (double[] a : p) s2 += (a[1] - avg) * (a[1] - avg);
                yield Math.sqrt(s2 / p.size());
            }
            default -> throw new IllegalArgumentException("fn " + fn);
        };
    }

    public double firstT() {
        return size == 0 ? Double.NaN : tAt(0);
    }

    public double lastT() {
        List<double[]> p = points();
        return p.isEmpty() ? Double.NaN : p.get(p.size() - 1)[0];
    }

    // --- сохранение ---

    public void save(StateWriter out) {
        double[] tt = new double[size];
        double[] vv = new double[size];
        for (int k = 0; k < size; k++) {
            tt[k] = tAt(k);
            vv[k] = vAt(k);
        }
        out.putDoubles("t", tt, size);
        out.putDoubles("v", vv, size);
        out.putLong("received", received);
    }

    public void load(StateReader in) {
        double[] tt = in.getDoubles("t"), vv = in.getDoubles("v");
        head = 0;
        size = 0;
        hasA = false;
        for (int k = Math.max(0, tt.length - t.length); k < Math.min(tt.length, vv.length); k++) archive(tt[k], vv[k]);
        if (size > 0) open(tAt(size - 1), vAt(size - 1));
        tLast = size > 0 ? tAt(size - 1) : Double.NaN;
        yLast = size > 0 ? vAt(size - 1) : 0;
        received = in.optLong("received", size);
    }
}
