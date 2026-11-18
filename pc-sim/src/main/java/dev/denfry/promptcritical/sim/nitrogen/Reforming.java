package dev.denfry.promptcritical.sim.nitrogen;

/**
 * Паровая конверсия метана на никелевом катализаторе и конверсия CO.
 *
 * <p>Реакции: (1) CH₄ + H₂O ⇌ CO + 3H₂, (2) CO + H₂O ⇌ CO₂ + H₂, (3) CH₄ + 2H₂O ⇌ CO₂ + 4H₂. Константы равновесия —
 * Xu и Froment (AIChE J. 35 (1989) 88, табл. 2): K₁ = 10266,76·exp(30,11 − 26830/T) кПа², K₂ = exp(4400/T − 4,063),
 * K₃ = K₁K₂ (совпадают с Twigg, «Catalyst Handbook», 2-е изд., 1989, гл. 5, и с ΔG° по таблицам JANAF в пределах 2 %).
 * Кинетика — Xu и Froment (там же, табл. 3 и 4): скорости r₁ и r₃ Ленгмюра–Хиншелвуда с адсорбцией CO, H₂, CH₄ и H₂O
 * (r₂ — конверсия CO — на никеле при этих температурах на порядки быстрее, её газ держит в равновесии),
 * с фактором эффективности промышленного зерна η = 0,03 (диффузионное торможение; Elnashaie, Elshishini, «Modelling,
 * Simulation and Optimization of Industrial Fixed Bed Catalytic Reactors», 1993, гл. 6).
 *
 * <p>Трубчатая печь — реактор идеального вытеснения с линейным профилем температуры газа по слою; вторичный
 * риформинг — адиабатический: кислород воздуха сгорает с водородом, выход — равновесие при температуре выхода (с
 * приближением к равновесию по метану), её находят из баланса энтальпии. Конверсия CO — адиабатическое равновесие
 * реакции (2) с приближением к равновесию по температуре: высокотемпературная на Fe–Cr (вход 370 °C), низкотемпературная
 * на Cu–Zn (вход 205 °C) (Appl, «Ammonia», Wiley-VCH, 1999, §4.2–4.3; «Справочник азотчика», т. 1, 1986, гл. 3–4).
 * Давления в барах, потоки — моль/с.
 */
public final class Reforming {
    public static final double ETA = 0.03;
    /** Водород, подмешиваемый к сырью для гидроочистки (рецикл синтез-газа), доля от метана. */
    public static final double RECYCLE_H2 = 0.03;
    private static final int CH4 = Species.CH4.ordinal(), H2O = Species.H2O.ordinal(), CO = Species.CO.ordinal(),
            CO2 = Species.CO2.ordinal(), H2 = Species.H2.ordinal(), O2 = Species.O2.ordinal();
    private static final double RG = Species.R, TINY = 1e-12;

    private Reforming() {
    }

    /** K₁ реакции CH₄ + H₂O ⇌ CO + 3H₂, бар². */
    public static double k1(double t) {
        return 1.026676 * Math.exp(30.11 - 26830 / t);
    }

    /** K₂ реакции CO + H₂O ⇌ CO₂ + H₂. */
    public static double k2(double t) {
        return Math.exp(4400 / t - 4.063);
    }

    /** Результат аппарата: состав на выходе, температура, подведённое тепло (для адиабатических — 0). */
    public record Result(double[] n, double tK, double dutyW) {
        public double dry(Species s) {
            return Species.dryFraction(n, s);
        }
    }

    // --- кинетика Xu–Froment ---

    /** Скорости реакций (1)–(3), моль/(кг кат.·с), с фактором эффективности и активностью катализатора. */
    static double[] rates(double[] n, double t, double pBar, double activity) {
        double tot = Species.total(n);
        double pCh4 = n[CH4] / tot * pBar, pH2o = n[H2O] / tot * pBar, pCo = n[CO] / tot * pBar, pCo2 = n[CO2] / tot * pBar,
                pH2 = Math.max(n[H2] / tot * pBar, 1e-6);
        double rt = RG * t;
        double kr1 = 4.225e15 * Math.exp(-240.1e3 / rt), kr2 = 1.955e6 * Math.exp(-67.13e3 / rt),
                kr3 = 1.020e15 * Math.exp(-243.9e3 / rt);
        double kCo = 8.23e-5 * Math.exp(70.65e3 / rt), kH2 = 6.12e-9 * Math.exp(82.90e3 / rt),
                kCh4 = 6.65e-4 * Math.exp(38.28e3 / rt), kH2o = 1.77e5 * Math.exp(-88.68e3 / rt);
        double den = 1 + kCo * pCo + kH2 * pH2 + kCh4 * pCh4 + kH2o * pH2o / pH2;
        double d2 = den * den, eq1 = k1(t), eq2 = k2(t), eq3 = eq1 * eq2;
        double r1 = kr1 / Math.pow(pH2, 2.5) * (pCh4 * pH2o - pH2 * pH2 * pH2 * pCo / eq1) / d2;
        double r2 = kr2 / pH2 * (pCo * pH2o - pH2 * pCo2 / eq2) / d2;
        double r3 = kr3 / Math.pow(pH2, 3.5) * (pCh4 * pH2o * pH2o - pH2 * pH2 * pH2 * pH2 * pCo2 / eq3) / d2;
        double f = ETA * activity * 1000 / 3600; // кмоль/(кг·ч) → моль/(кг·с)
        return new double[] {r1 * f, r2 * f, r3 * f};
    }

    private static void applyExtents(double[] n, double x1, double x2, double x3) {
        n[CH4] -= x1 + x3;
        n[H2O] -= x1 + x2 + 2 * x3;
        n[CO] += x1 - x2;
        n[CO2] += x2 + x3;
        n[H2] += 3 * x1 + x2 + 4 * x3;
    }

    /** Знак движущей силы реакции (1): равновесие перескочено, если знак сменился. */
    private static boolean overshoot(double[] a, double[] b, double t, double pBar) {
        return Math.signum(drive1(a, t, pBar)) * Math.signum(drive1(b, t, pBar)) < 0;
    }

    private static double drive1(double[] n, double t, double pBar) {
        double tot = Species.total(n);
        return n[CH4] * n[H2O] * tot * tot - n[H2] * n[H2] * n[H2] * n[CO] * pBar * pBar / k1(t);
    }


    /**
     * Трубчатая печь: газ входит при {@code tInK}, выходит при {@code tOutK}, на {@code catalystKg} катализатора с
     * активностью {@code activity}; тепло, переданное через стенки труб, — {@code dutyW}.
     */
    public static Result primary(double[] feed, double pBar, double tInK, double tOutK, double catalystKg, double activity) {
        double[] n = feed.clone();
        n[H2] = Math.max(n[H2], RECYCLE_H2 * n[CH4]);
        double hIn = Species.enthalpy(n, tInK);
        if (activity > 0 && catalystKg > 0 && n[CH4] > 0) {
            double w = 0, hMax = catalystKg / 400, h = hMax;
            int guard = 0;
            while (w < catalystKg && guard++ < 40_000) {
                h = Math.min(h, catalystKg - w);
                double t = tInK + (tOutK - tInK) * (w + h / 2) / catalystKg;
                double[] r = rates(n, t, pBar, activity);
                double[] trial = n.clone();
                applyExtents(trial, r[0] * h, 0, r[2] * h);
                // конверсия CO на никеле при 500–850 °C много быстрее риформинга: её держим в равновесии
                if (min(trial) >= 0) trial = shiftEquilibrium(trial, t);
                if (min(trial) < 0 || overshoot(n, trial, t, pBar)) {
                    if (h > hMax / 256) {
                        h /= 2;
                        continue;
                    }
                    // жёсткий участок: риформинг быстрее потока, газ идёт по локальному равновесию
                    double t2 = tInK + (tOutK - tInK) * Math.min(1, (w + hMax) / catalystKg);
                    n = equilibrium(n, t2, pBar, 0);
                    w += hMax;
                    h = hMax;
                    continue;
                }
                n = trial;
                w += h;
                h = Math.min(hMax, h * 1.5);
            }
        }
        return new Result(n, tOutK, Species.enthalpy(n, tOutK) - hIn);
    }

    private static double min(double[] n) {
        double m = Double.MAX_VALUE;
        for (double v : n) m = Math.min(m, v);
        return m;
    }

    // --- равновесие ---

    /**
     * Равновесие реакций (1) и (2) из состава {@code n} при температуре {@code t}, давлении {@code pBar}. При
     * {@code approachK} > 0 равновесие по метану берётся при температуре на столько ниже (недоход до равновесия).
     */
    public static double[] equilibrium(double[] n, double t, double pBar, double approachK) {
        double tm = t - approachK;
        double lo = -Math.min(n[CO], n[H2] / 3) + TINY, hi = Math.min(n[CH4], n[H2O]) - TINY;
        if (hi <= lo) return shiftEquilibrium(n, t);
        double lnK1 = Math.log(k1(tm));
        double[] best = null;
        for (int i = 0; i < 80; i++) {
            double x1 = 0.5 * (lo + hi);
            double[] m = n.clone();
            applyExtents(m, x1, 0, 0);
            m = shiftEquilibrium(m, tm);
            double tot = Species.total(m);
            double q = Math.log(Math.max(m[CO], TINY)) + 3 * Math.log(Math.max(m[H2], TINY)) + 2 * Math.log(pBar)
                    - Math.log(Math.max(m[CH4], TINY)) - Math.log(Math.max(m[H2O], TINY)) - 2 * Math.log(tot);
            if (q > lnK1) hi = x1;
            else lo = x1;
            best = m;
        }
        return best;
    }

    /**
     * Равновесие одной реакции (2) при температуре {@code t}: (CO₂ + x)(H₂ + x) = K₂(CO − x)(H₂O − x) — квадратное
     * уравнение относительно степени превращения x.
     */
    public static double[] shiftEquilibrium(double[] n, double t) {
        double k = k2(t);
        double lo = -Math.min(n[CO2], n[H2]), hi = Math.min(n[CO], n[H2O]);
        if (hi <= lo) return n.clone();
        double a = 1 - k, b = n[CO2] + n[H2] + k * (n[CO] + n[H2O]), c = n[CO2] * n[H2] - k * n[CO] * n[H2O];
        double x;
        if (Math.abs(a) < 1e-12) {
            x = -c / b;
        } else {
            double q = -0.5 * (b + Math.copySign(Math.sqrt(Math.max(0, b * b - 4 * a * c)), b));
            double x1 = q / a, x2 = q != 0 ? c / q : x1;
            x = x1 >= lo && x1 <= hi ? x1 : x2;
        }
        x = Math.max(lo, Math.min(hi, x));
        double[] m = n.clone();
        applyExtents(m, 0, x, 0);
        return m;
    }

    // --- вторичный риформинг ---

    /** Кислород воздуха сгорает с водородом, недостающее — с метаном. */
    static void burn(double[] n) {
        double o2 = n[O2];
        double withH2 = Math.min(o2, n[H2] / 2);
        n[H2] -= 2 * withH2;
        n[H2O] += 2 * withH2;
        o2 -= withH2;
        double withCh4 = Math.min(o2 / 2, n[CH4]);
        n[CH4] -= withCh4;
        n[CO2] += withCh4;
        n[H2O] += 2 * withCh4;
        n[O2] = o2 - 2 * withCh4;
    }

    /**
     * Шахтный реактор вторичного риформинга: газ трубчатой печи при {@code tInK} и воздух {@code airMolS} при
     * {@code airK}; выход — адиабатическое равновесие.
     */
    public static Result secondary(double[] in, double tInK, double airMolS, double airK, double pBar, double approachK) {
        double[] air = Species.air(airMolS);
        double h = Species.enthalpy(in, tInK) + Species.enthalpy(air, airK);
        double[] n = in.clone();
        Species.add(n, air);
        burn(n);
        double hIn = h;
        double lo = Math.min(tInK, 900), hi = 1800;
        double[] out = n;
        for (int i = 0; i < 50; i++) {
            double t = 0.5 * (lo + hi);
            out = equilibrium(n, t, pBar, approachK);
            if (Species.enthalpy(out, t) > hIn) hi = t;
            else lo = t;
        }
        double t = 0.5 * (lo + hi);
        return new Result(equilibrium(n, t, pBar, approachK), t, 0);
    }

    /** Отношение (H₂ + CO)/N₂: после конверсии CO весь CO станет водородом. */
    public static double hydrogenToNitrogen(double[] n) {
        double n2 = n[Species.N2.ordinal()];
        return n2 > 0 ? (n[H2] + n[CO]) / n2 : Double.POSITIVE_INFINITY;
    }

    /** Расход воздуха во вторичный риформинг под заданное (H₂ + CO)/N₂ на выходе, моль/с. */
    public static double airForRatio(double[] in, double tInK, double airK, double pBar, double approachK, double ratio) {
        double lo = 0, hi = 6 * (in[CH4] + in[H2] / 4 + in[CO] / 4) + 1e-9;
        for (int i = 0; i < 40; i++) {
            double a = 0.5 * (lo + hi);
            if (hydrogenToNitrogen(secondary(in, tInK, a, airK, pBar, approachK).n()) > ratio) lo = a;
            else hi = a;
        }
        return 0.5 * (lo + hi);
    }

    // --- конверсия CO ---

    /**
     * Адиабатический конвертор CO: вход при {@code tInK}; выход — равновесие реакции (2) при температуре на
     * {@code approachK} выше температуры выхода (недоход). Без катализатора ({@code active == false}) газ проходит
     * как есть.
     */
    public static Result shift(double[] in, double tInK, double approachK, boolean active) {
        if (!active) return new Result(in.clone(), tInK, 0);
        double hIn = Species.enthalpy(in, tInK);
        double lo = tInK, hi = tInK + 300;
        double[] out = in;
        for (int i = 0; i < 50; i++) {
            double t = 0.5 * (lo + hi);
            out = shiftEquilibrium(in, t + approachK);
            if (Species.enthalpy(out, t) > hIn) hi = t;
            else lo = t;
        }
        double t = 0.5 * (lo + hi);
        return new Result(shiftEquilibrium(in, t + approachK), t, 0);
    }
}
