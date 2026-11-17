package dev.denfry.promptcritical.sim.environment;

/**
 * Источник времени года. {@code pc-sim} не знает модов сезонов: адаптер (Serene Seasons и т. п.) живёт в моде, а без
 * него действует {@link Calendar}.
 */
public interface SeasonProvider {
    /** Игровых тиков в сутках. */
    long TICKS_PER_DAY = 24_000;

    /** Доля года от начала весны, [0, 1): 0 — начало весны, 0,25 — лета, 0,5 — осени, 0,75 — зимы. */
    double yearFraction(long gameTime);

    /** Время года по доле года. */
    static Season season(double yearFraction) {
        double f = yearFraction - Math.floor(yearFraction);
        return Season.values()[Math.min(3, (int) (f * 4))];
    }

    enum Season { SPRING, SUMMER, AUTUMN, WINTER }

    /**
     * Календарь без модов: {@code daysPerSeason} игровых суток на сезон; по умолчанию 24, как у Serene Seasons
     * (три подсезона по 8 суток).
     */
    record Calendar(int daysPerSeason) implements SeasonProvider {
        public static final Calendar DEFAULT = new Calendar(24);

        public Calendar {
            if (daysPerSeason < 1) throw new IllegalArgumentException("daysPerSeason");
        }

        @Override
        public double yearFraction(long gameTime) {
            long year = 4L * daysPerSeason * TICKS_PER_DAY;
            return Math.floorMod(gameTime, year) / (double) year;
        }
    }
}
