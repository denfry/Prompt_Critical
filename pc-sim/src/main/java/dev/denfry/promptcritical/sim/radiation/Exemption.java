package dev.denfry.promptcritical.sim.radiation;

import java.util.EnumMap;
import java.util.Map;

/**
 * Уровень освобождения (17 §17.3.3, R54): предмет перестаёт быть источником, если сумма долей
 * Σ a_i / a_i,осв ≤ 1, где a_i — удельная активность нуклида в предмете (Бк/г). Значения a_i,осв — уровни
 * освобождения для умеренных количеств из МАГАТЭ GSR Part 3, табл. I.1 (нуклид с дочерними в равновесии, «+») [сверить].
 * Правило применяют рамка на проходной, приём на складе торговли и запрет вложения в контейнеры (16 §16.8).
 */
public final class Exemption {
    private static final Map<Emitter, Double> BQ_G = new EnumMap<>(Emitter.class);

    static {
        BQ_G.put(Emitter.CO60, 10.0);
        BQ_G.put(Emitter.CS137, 10.0);
        BQ_G.put(Emitter.CS134, 10.0);
        BQ_G.put(Emitter.I131, 100.0);
        BQ_G.put(Emitter.Y90, 1000.0);
        BQ_G.put(Emitter.SR90, 100.0);
        BQ_G.put(Emitter.NB95, 10.0);
        BQ_G.put(Emitter.ZR95, 10.0);
        BQ_G.put(Emitter.RU106, 100.0);
        BQ_G.put(Emitter.KR85, 1e5);
        BQ_G.put(Emitter.IR192, 10.0);
        BQ_G.put(Emitter.PU238, 1.0);
        // N-16 в таблице нет (T½ 7 с); берём строгое значение, как у жёстких γ-излучателей
        BQ_G.put(Emitter.N16, 10.0);
        BQ_G.put(Emitter.AM241, 1.0);
        BQ_G.put(Emitter.PU241, 100.0);
        BQ_G.put(Emitter.CM244, 10.0);
    }

    private Exemption() {
    }

    /** Уровень освобождения нуклида, Бк/г; для нуклида без значения — 1 Бк/г (строже всех в таблице). */
    public static double bqPerGram(Emitter e) {
        return BQ_G.getOrDefault(e, 1.0);
    }

    /** Сумма долей Σ (a_i / m) / a_i,осв для активностей {@code bq} в массе {@code massKg}. */
    public static double fractionSum(Map<Emitter, Double> bq, double massKg) {
        double sum = 0;
        for (Map.Entry<Emitter, Double> e : bq.entrySet()) {
            double a = e.getValue();
            if (!(a > 0)) continue;
            // предмет без массы с активностью — точечный источник, освободить его нечем
            if (!(massKg > 0)) return Double.POSITIVE_INFINITY;
            sum += a / (massKg * 1000) / bqPerGram(e.getKey());
        }
        return sum;
    }

    public static boolean exempt(Map<Emitter, Double> bq, double massKg) {
        return fractionSum(bq, massKg) <= 1;
    }
}
