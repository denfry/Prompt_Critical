package dev.denfry.promptcritical.sim.journal;

/**
 * Шкала событий по мотивам INES (04d §4d.8.3). Уровни 3–7 — по выбросу в беккерелях йод-131-эквивалента, границы
 * сдвинуты вниз на 1–2 порядка против реальной шкалы [BALANCE]; уровни 0–2 — по нарушениям без выброса. Пересчёт
 * выброса в йод-эквивалент (коэффициенты 04b {@code radiation.iodine_equivalent}) делает вызывающий.
 */
public final class IncidentScale {
    private IncidentScale() {}

    /** {@code journal.announce_min_level} по умолчанию (§4d.8.3). */
    public static final int DEFAULT_ANNOUNCE_MIN_LEVEL = 3;
    /** Радиус, в котором об уровне 3 узнают соседи станции, м (§4d.8.3). */
    public static final double NEIGHBOUR_RADIUS_M = 256.0;

    /** АЗ без нарушений пределов. */
    public static final int LEVEL_TRIP = 0;
    /** Байпас защиты. */
    public static final int LEVEL_BYPASS = 1;
    /** Нарушение пределов эксплуатации (DNBR, давление), отказ оболочки ТВЭЛ (04c) без выброса. */
    public static final int LEVEL_LIMIT_VIOLATION = 2;
    /** Доза игрока выше порога 04b — уровень 3 без выброса. */
    public static final int LEVEL_OVERDOSE = 3;

    /** Нижние границы выброса для уровней 3..7, Бк йод-экв. (строго больше). */
    private static final double[] RELEASE_BQ = {1e12, 1e13, 1e14, 1e15, 1e16};

    /** Кому объявляется событие (§4d.8.3). */
    public enum Audience {
        /** Команде станции. */
        STATION,
        /** Команде станции и игрокам в {@link #NEIGHBOUR_RADIUS_M} от неё. */
        STATION_AND_NEIGHBOURS,
        /** Всему серверу. */
        SERVER,
        /** Всему серверу с тревожным звуком. */
        SERVER_SOUND,
        /** Всему серверу с титром на экране. */
        SERVER_TITLE
    }

    /** Уровень по выбросу; меньше 10¹² Бк йод-экв. — 0 (выброс сам по себе уровня не даёт). */
    public static int levelForRelease(double iodineEquivalentBq) {
        int level = 0;
        for (int i = 0; i < RELEASE_BQ.length; i++)
            if (iodineEquivalentBq > RELEASE_BQ[i]) level = 3 + i;
        return level;
    }

    public static Audience audience(int level) {
        if (level >= 6) return Audience.SERVER_TITLE;
        if (level == 5) return Audience.SERVER_SOUND;
        if (level == 4) return Audience.SERVER;
        if (level == 3) return Audience.STATION_AND_NEIGHBOURS;
        return Audience.STATION;
    }

    /** Объявлять ли в чат при настройке {@code journal.announce_min_level}. */
    public static boolean announce(int level, int announceMinLevel) {
        return level >= announceMinLevel;
    }
}
