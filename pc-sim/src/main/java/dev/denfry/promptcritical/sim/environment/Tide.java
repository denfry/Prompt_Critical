package dev.denfry.promptcritical.sim.environment;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Прилив как сумма гармоник: η(t) = Σ Aᵢ·cos(Vᵢ(t) − gᵢ), где Aᵢ и gᵢ — амплитуда и фаза запаздывания составляющей в
 * пункте, а Vᵢ — астрономический аргумент. Аргументы — по Дудсону через средние долготы (Pugh, Woodworth, «Sea-Level
 * Science», 2014, §4.2–4.3, табл. 4.2): T — солнечное время, D — элонгация Луны (D = s − h), h — долгота Солнца,
 * s − p — аномалия Луны:
 * <ul>
 *   <li>M2 = 2T − 2D — главная лунная полусуточная;</li>
 *   <li>S2 = 2T — главная солнечная полусуточная;</li>
 *   <li>N2 = 2T − 2D − (s − p) — большая лунная эллиптическая;</li>
 *   <li>K1 = T + h + 90° — лунно-солнечная суточная;</li>
 *   <li>O1 = T − h − 2D − 90° — главная лунная суточная.</li>
 * </ul>
 * Скорости отсюда: ω(M2) − ω(S2) = −2·360°/синодический месяц — биение M2 и S2 даёт сизигийные приливы в новолуние
 * и полнолуние (D = 0 и 180°) и квадратурные в четверти. С настоящим календарём ({@link Calendar#REAL}) скорости
 * совпадают с таблицами NOAA (M2 28,984104°/ч, S2 30, N2 28,43973, K1 15,041069, O1 13,943035).
 *
 * <p>В игре месяц Minecraft длится 8 суток (фаза 0 — полнолуние), поэтому {@link Calendar#game} ставит синодический
 * месяц 8 суток и аномалистический в том же отношении к нему, что в природе: биение M2–S2 — 4 суток, и сизигийная
 * вода приходит в полнолуние и новолуние на небе. «Возраст прилива» (запаздывание сизигийной воды на 1–2 суток в
 * природе) не учитывается, как и узловые поправки f, u за 18,6-летний цикл.
 */
public final class Tide {
    /** Составляющая прилива. */
    public enum Constituent { M2, S2, N2, K1, O1 }

    /**
     * Календарь: длительности средних суток, синодического и аномалистического месяцев и тропического года.
     *
     * @param synodicDays     синодический месяц, сут
     * @param anomalisticDays аномалистический месяц, сут
     * @param yearDays        тропический год, сут
     */
    public record Calendar(double synodicDays, double anomalisticDays, double yearDays) {
        /** Средние длительности (Pugh, Woodworth, 2014, табл. 4.1). */
        public static final Calendar REAL = new Calendar(29.530589, 27.554550, 365.242199);

        public Calendar {
            if (!(synodicDays > 0 && anomalisticDays > 0 && yearDays > synodicDays)) throw new IllegalArgumentException("calendar");
        }

        /** Игровой: синодический месяц 8 суток (фазы Луны Minecraft), год — календаря сезонов. */
        public static Calendar game(double yearDays) {
            double synodic = 8;
            return new Calendar(synodic, synodic * REAL.anomalisticDays / REAL.synodicDays, yearDays);
        }

        /** Скорость составляющей, °/ч. */
        public double speedDegPerHour(Constituent c) {
            double t = 360.0 / 24, d = 360 / (synodicDays * 24), h = 360 / (yearDays * 24), a = 360 / (anomalisticDays * 24);
            return switch (c) {
                case M2 -> 2 * t - 2 * d;
                case S2 -> 2 * t;
                case N2 -> 2 * t - 2 * d - a;
                case K1 -> t + h;
                case O1 -> t - h - 2 * d;
            };
        }
    }

    /**
     * Приливные постоянные пункта.
     *
     * @param amplitudeM амплитуды, м
     * @param phaseDeg   фазы запаздывания g, ° (относительно астрономического аргумента)
     */
    public record Site(Map<Constituent, Double> amplitudeM, Map<Constituent, Double> phaseDeg) {
        public Site {
            amplitudeM = Map.copyOf(amplitudeM);
            phaseDeg = Map.copyOf(phaseDeg);
        }

        public double amplitude(Constituent c) {
            return amplitudeM.getOrDefault(c, 0.0);
        }

        public double phase(Constituent c) {
            return phaseDeg.getOrDefault(c, 0.0);
        }

        /**
         * Бостон, NOAA 8443970 (гармонические постоянные NOAA CO-OPS, метры): M2 1,371, S2 0,208, N2 0,305,
         * K1 0,143, O1 0,116 — типичный полусуточный прилив побережья Атлантики.
         */
        public static Site boston() {
            Map<Constituent, Double> a = new EnumMap<>(Constituent.class), g = new EnumMap<>(Constituent.class);
            a.put(Constituent.M2, 1.371);
            a.put(Constituent.S2, 0.208);
            a.put(Constituent.N2, 0.305);
            a.put(Constituent.K1, 0.143);
            a.put(Constituent.O1, 0.116);
            g.put(Constituent.M2, 109.2);
            g.put(Constituent.S2, 146.2);
            g.put(Constituent.N2, 76.7);
            g.put(Constituent.K1, 205.6);
            g.put(Constituent.O1, 187.1);
            return new Site(a, g);
        }

        /**
         * Пункт с амплитудой M2 {@code m2AmplitudeM} и составом прилива как у Бостона (отношения S2/M2, N2/M2, K1/M2,
         * O1/M2), без фазовых запаздываний: полная вода при кульминации Луны.
         */
        public static Site semidiurnal(double m2AmplitudeM) {
            Site b = boston();
            double k = m2AmplitudeM / b.amplitude(Constituent.M2);
            Map<Constituent, Double> a = new EnumMap<>(Constituent.class);
            for (Constituent c : Constituent.values()) a.put(c, b.amplitude(c) * k);
            return new Site(a, Map.of());
        }

        /**
         * Форм-фактор F = (K1 + O1)/(M2 + S2): меньше 0,25 — полусуточный прилив, 0,25–1,5 — смешанный
         * преимущественно полусуточный, 1,5–3 — смешанный преимущественно суточный, больше 3 — суточный (Courtier, 1938).
         */
        public double formFactor() {
            return (amplitude(Constituent.K1) + amplitude(Constituent.O1)) / (amplitude(Constituent.M2) + amplitude(Constituent.S2));
        }

        /** Средняя сизигийная величина прилива 2(M2 + S2), м. */
        public double springRangeM() {
            return 2 * (amplitude(Constituent.M2) + amplitude(Constituent.S2));
        }

        /** Средняя квадратурная величина прилива 2(M2 − S2), м. */
        public double neapRangeM() {
            return 2 * (amplitude(Constituent.M2) - amplitude(Constituent.S2));
        }

        /** Средняя величина прилива 2·M2, м. */
        public double meanRangeM() {
            return 2 * amplitude(Constituent.M2);
        }
    }

    /** Усиление прилива от океана к берегу больше этого не бывает даже в резонансных заливах (Фанди ≈ 10 м M2). */
    public static final double MAX_AMPLIFICATION = 12;

    /**
     * Закон Грина для длинной волны: при медленном сужении и обмелении потока энергия сохраняется, и амплитуда
     * растёт как A₂/A₁ = (b₁/b₂)^{1/2}·(h₁/h₂)^{1/4} (Green, 1837; Pugh, Woodworth, 2014, §7.2). Глубины и ширины —
     * со стороны океана (1) и у берега (2).
     */
    public static double greensLaw(double depthOceanM, double depthCoastM, double widthOcean, double widthCoast) {
        if (!(depthOceanM > 0 && depthCoastM > 0 && widthOcean > 0 && widthCoast > 0)) throw new IllegalArgumentException("geometry");
        double g = Math.sqrt(widthOcean / widthCoast) * Math.pow(depthOceanM / depthCoastM, 0.25);
        return Math.min(MAX_AMPLIFICATION, g);
    }

    private final Site site;
    private final Calendar calendar;

    public Tide(Site site, Calendar calendar) {
        this.site = Objects.requireNonNull(site, "site");
        this.calendar = Objects.requireNonNull(calendar, "calendar");
    }

    public Site site() {
        return site;
    }

    public Calendar calendar() {
        return calendar;
    }

    /**
     * Уровень моря над средним, м, через {@code hours} часов от начала отсчёта. Начало — полночь полнолуния в начале
     * года: D = 180°, T = 0, h = 0, s − p = 0.
     */
    public double heightM(double hours) {
        double sum = 0;
        for (Constituent c : Constituent.values()) {
            double a = site.amplitude(c);
            if (a == 0) continue;
            sum += a * Math.cos(Math.toRadians(argumentDeg(c, hours) - site.phase(c)));
        }
        return sum;
    }

    /** Астрономический аргумент V составляющей, °. */
    public double argumentDeg(Constituent c, double hours) {
        double t = 360.0 / 24 * hours;
        double d = 180 + 360 * hours / (calendar.synodicDays() * 24);
        double h = 360 * hours / (calendar.yearDays() * 24);
        double anomaly = 360 * hours / (calendar.anomalisticDays() * 24);
        return switch (c) {
            case M2 -> 2 * t - 2 * d;
            case S2 -> 2 * t;
            case N2 -> 2 * t - 2 * d - anomaly;
            case K1 -> t + h + 90;
            case O1 -> t - h - 2 * d - 90;
        };
    }

    /**
     * Часы от полуночи полнолуния по времени суток Minecraft ({@code Level.getDayTime()}): сутки — 24 000 тиков, 0 тиков
     * — 6:00 утра первых суток, фаза Луны 0 — полнолуние (как {@code DimensionType.moonPhase}).
     */
    public static double hoursFromDayTime(long dayTime) {
        return dayTime / 1000.0 + 6;
    }
}
