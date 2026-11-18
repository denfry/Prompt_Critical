package dev.denfry.promptcritical.sim.environment;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Учёт выбросов по источникам (14 §14.1.4): масса по веществам за текущие сутки и за период квот, категория источника
 * и метод определения. Множитель метода (измерено 1,0 · рассчитано 1,2 · замещено 1,5) идёт в отчётность для квот и
 * плат, но не в физическое загрязнение.
 */
public final class EmissionLedger {
    /** VOC — летучие органические вещества: испарение разливов и дыхания резервуаров (09 §9.8.4, 14 §14.5.3). */
    public enum Pollutant { SO2, NOX, PM, CO2, CH4, CO, VOC }

    public enum Category { BOILER, GAS_TURBINE, ENGINE, FLARE, COKE_OVEN, BLAST_FURNACE, REFINERY, FUGITIVE, SPILL, PROCESS }

    public enum Method {
        MEASURED(1.0), CALCULATED(1.2), SUBSTITUTED(1.5);

        public final double factor;

        Method(double factor) {
            this.factor = factor;
        }
    }

    /** Потенциал глобального потепления CH₄ на 100 лет для квот (§14.1.1). */
    public static final double GWP_CH4 = 28;

    /** Записи одного источника. */
    public static final class Source {
        public final Category category;
        public Method method = Method.CALCULATED;
        final EnumMap<Pollutant, Double> day = new EnumMap<>(Pollutant.class), period = new EnumMap<>(Pollutant.class);

        Source(Category category) {
            this.category = category;
        }

        public double dayKg(Pollutant p) {
            return day.getOrDefault(p, 0.0);
        }

        public double periodKg(Pollutant p) {
            return period.getOrDefault(p, 0.0);
        }
    }

    private final Map<String, Source> sources = new LinkedHashMap<>();
    private final java.util.List<SpillRecord> spills = new java.util.ArrayList<>();

    /**
     * Разлив, не поместившийся в мир блоками (09 §9.11.9, 14 §14.5.1): {@code blockKey} — место (BlockPos.asLong),
     * {@code fluidId} — жидкость, {@code m3} — объём, {@code onWater} — на воде.
     */
    public record SpillRecord(long blockKey, String fluidId, double m3, boolean onWater) {}

    public Source source(String id, Category c) {
        return sources.computeIfAbsent(id, k -> new Source(c));
    }

    public Map<String, Source> sources() {
        return java.util.Collections.unmodifiableMap(sources);
    }

    /** Записать выброс {@code kgS} кг/с за {@code dtS} с. */
    public void record(String id, Category c, Pollutant p, double kgS, double dtS) {
        if (!(kgS > 0) || !(dtS > 0)) return;
        Source s = source(id, c);
        s.day.merge(p, kgS * dtS, Double::sum);
        s.period.merge(p, kgS * dtS, Double::sum);
    }

    /** Записать разлив (14 §14.5.1). */
    public void recordSpill(long blockKey, String fluidId, double m3, boolean onWater) {
        if (m3 > 0) spills.add(new SpillRecord(blockKey, fluidId, m3, onWater));
    }

    public java.util.List<SpillRecord> spills() {
        return java.util.Collections.unmodifiableList(spills);
    }

    /** Суммарный объём разливов, м³. */
    public double spilledM3() {
        double v = 0;
        for (SpillRecord r : spills) v += r.m3();
        return v;
    }

    /** Новые сутки: суточные счётчики обнуляются. */
    public void rollDay() {
        for (Source s : sources.values()) s.day.clear();
    }

    /** Новый период квот. */
    public void rollPeriod() {
        for (Source s : sources.values()) s.period.clear();
    }

    /** CO₂-эквивалент за период по всем источникам с множителем метода, т. */
    public double reportedCo2eT() {
        double kg = 0;
        for (Source s : sources.values()) kg += s.method.factor * (s.periodKg(Pollutant.CO2) + GWP_CH4 * s.periodKg(Pollutant.CH4));
        return kg / 1000;
    }

    /** Отчётная масса вещества за период по всем источникам с множителем метода, кг. */
    public double reportedKg(Pollutant p) {
        double kg = 0;
        for (Source s : sources.values()) kg += s.method.factor * s.periodKg(p);
        return kg;
    }
}
