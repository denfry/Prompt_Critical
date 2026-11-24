package dev.denfry.promptcritical.sim.radiation;

import java.util.Arrays;

/**
 * Годовой учёт дозы (04b §4b.6.1, §4b.5.2): доза за всю жизнь и за скользящее окно {@value #YEAR_DAYS} игровых суток.
 * Окно хранится корзинами по {@value #BUCKET_DAYS} суток, поэтому доза уходит из окна ступеньками не больше 1/73 года.
 * Пределы по НРБ-99/2009 и ICRP 103: персонал группы А — 20 мЗв в год (в среднем за 5 лет, не больше 50 мЗв в один год),
 * население — 1 мЗв в год.
 */
public final class DoseLedger {
    public static final int YEAR_DAYS = 365, BUCKET_DAYS = 5, BUCKETS = YEAR_DAYS / BUCKET_DAYS;
    /** Годовой предел персонала группы А, Зв ({@code radiation.limits.worker_annual_sv}). */
    public static final double WORKER_ANNUAL_SV = 0.02;
    /** Наибольшая доза персонала за один год, Зв. */
    public static final double WORKER_SINGLE_YEAR_SV = 0.05;
    /** Предел облучения населения, Зв/год. */
    public static final double PUBLIC_ANNUAL_SV = 1e-3;

    private final double[] buckets;
    private long lastBucket;
    private double lifetimeSv;

    public DoseLedger() {
        this(new double[BUCKETS], Long.MIN_VALUE, 0);
    }

    /** Восстановление из сохранения: корзины, номер последней корзины, доза за жизнь. */
    public DoseLedger(double[] buckets, long lastBucket, double lifetimeSv) {
        if (buckets.length != BUCKETS) throw new IllegalArgumentException("ledger needs " + BUCKETS + " buckets");
        this.buckets = buckets.clone();
        this.lastBucket = lastBucket;
        this.lifetimeSv = lifetimeSv;
    }

    private static long bucketOf(long day) {
        return Math.floorDiv(day, BUCKET_DAYS);
    }

    /** Сдвинуть окно к суткам {@code day}: корзины, вышедшие из года, обнуляются. */
    private void advance(long day) {
        long b = bucketOf(day);
        if (lastBucket == Long.MIN_VALUE || b - lastBucket >= BUCKETS) {
            if (lastBucket != Long.MIN_VALUE) Arrays.fill(buckets, 0);
            lastBucket = b;
            return;
        }
        for (long k = lastBucket + 1; k <= b; k++) buckets[(int) Math.floorMod(k, BUCKETS)] = 0;
        if (b > lastBucket) lastBucket = b;
    }

    /** Записать дозу {@code sv}, полученную в сутки {@code day}. */
    public void add(long day, double sv) {
        if (!(sv > 0)) return;
        advance(day);
        buckets[(int) Math.floorMod(bucketOf(day), BUCKETS)] += sv;
        lifetimeSv += sv;
    }

    /** Доза за последние {@value #YEAR_DAYS} суток на сутки {@code day}, Зв. */
    public double annualSv(long day) {
        advance(day);
        double s = 0;
        for (double v : buckets) s += v;
        return s;
    }

    public double lifetimeSv() {
        return lifetimeSv;
    }

    /** Годовой предел персонала уже набран. */
    public boolean overWorkerLimit(long day) {
        return annualSv(day) >= WORKER_ANNUAL_SV;
    }

    public double[] buckets() {
        return buckets.clone();
    }

    public long lastBucket() {
        return lastBucket;
    }
}
