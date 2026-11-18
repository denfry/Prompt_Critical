package dev.denfry.promptcritical.sim.nitrogen;

/**
 * Синтез аммиака ½N₂ + 3/2H₂ ⇌ NH₃ на промотированном железном катализаторе (Габер–Бош).
 *
 * <p>Равновесие — Gillespie, Beattie (Phys. Rev. 36 (1930) 743), по измерениям Larson, Dodge (JACS 45 (1923) 2918) и
 * Larson (JACS 46 (1924) 367): lg Kₐ = −2,691122·lg T − 5,519265·10⁻⁵T + 1,848863·10⁻⁷T² + 2001,6/T + 2,6899 (атм⁻¹) и
 * поправка на неидеальность lg(K_p/Kₐ) = (0,1191849/T + 91,87212/T² + 25122730/T⁴)·P (P — атм), так что при 300 атм и
 * 450 °C K_p на треть больше Kₐ. Равновесное содержание NH₃ в смеси 3:1 — таблица Ларсона–Доджа (проверка — в
 * тестах).
 *
 * <p>Кинетика — уравнение Тёмкина–Пыжева в форме Dyson, Simon (Ind. Eng. Chem. Fundam. 7 (1968) 605):
 * r = 2k₂[K_p²·p_N₂·(p_H₂³/p_NH₃²)^α − (p_NH₃²/p_H₂³)^(1−α)], α = 0,5, k₂ = 1,7698·10¹⁵·exp(−40765/(1,987T))
 * кмоль NH₃/(м³ кат.·ч); фактор эффективности зерна 6–10 мм η = 0,6 (Appl, «Ammonia», 1999, §3.5).
 * Колонна — три адиабатических слоя с охлаждением газа между слоями до заданной температуры входа (холодные байпасы);
 * температура в слое — из баланса энтальпии.
 */
public final class AmmoniaSynthesis {
    public static final double ETA = 0.6, ALPHA = 0.5, ATM_PER_BAR = 1 / 1.01325;
    /** Предел перегрева катализатора: выше — рекристаллизация железа, К. */
    public static final double CATALYST_LIMIT_K = 530 + 273.15;
    private static final int N2 = Species.N2.ordinal(), H2 = Species.H2.ordinal(), NH3 = Species.NH3.ordinal();

    private AmmoniaSynthesis() {
    }

    /** Kₐ по Gillespie–Beattie, атм⁻¹. */
    public static double ka(double t) {
        double lg = -2.691122 * Math.log10(t) - 5.519265e-5 * t + 1.848863e-7 * t * t + 2001.6 / t + 2.6899;
        return Math.pow(10, lg);
    }

    /** K_p с поправкой на неидеальность при давлении {@code pBar}, атм⁻¹. */
    public static double kp(double t, double pBar) {
        double pAtm = pBar * ATM_PER_BAR;
        return ka(t) * Math.pow(10, (0.1191849 / t + 91.87212 / (t * t) + 25122730 / (t * t * t * t)) * pAtm);
    }

    /** Равновесный состав из {@code n} при T и P: степень превращения по бисекции. */
    public static double[] equilibrium(double[] n, double t, double pBar) {
        double kp = kp(t, pBar), pAtm = pBar * ATM_PER_BAR;
        double lo = -n[NH3], hi = Math.min(n[N2] * 2, n[H2] * 2 / 3);
        for (int i = 0; i < 70; i++) {
            double x = 0.5 * (lo + hi);
            if (quotient(n, x, pAtm) > kp) hi = x;
            else lo = x;
        }
        return advance(n, 0.5 * (lo + hi));
    }

    /** Равновесная мольная доля NH₃ для стехиометрической смеси 3:1 без инертов. */
    public static double equilibriumFraction(double t, double pBar) {
        double[] n = Species.flow();
        n[N2] = 1;
        n[H2] = 3;
        return Species.fraction(equilibrium(n, t, pBar), Species.NH3);
    }

    private static double quotient(double[] n, double x, double pAtm) {
        double nh3 = n[NH3] + x, n2 = n[N2] - x / 2, h2 = n[H2] - 1.5 * x;
        if (n2 <= 0 || h2 <= 0) return Double.POSITIVE_INFINITY;
        if (nh3 <= 0) return 0;
        double tot = Species.total(n) - x;
        return (nh3 / tot) / (Math.sqrt(n2 / tot) * Math.pow(h2 / tot, 1.5) * pAtm);
    }

    static double[] advance(double[] n, double x) {
        double[] m = n.clone();
        m[NH3] += x;
        m[N2] -= x / 2;
        m[H2] -= 1.5 * x;
        return m;
    }

    /** Скорость образования NH₃, моль/(м³ кат.·с), с фактором эффективности и активностью. */
    public static double rate(double[] n, double t, double pBar, double activity) {
        double tot = Species.total(n), pAtm = pBar * ATM_PER_BAR;
        double pN2 = n[N2] / tot * pAtm, pH2 = n[H2] / tot * pAtm, pNh3 = Math.max(n[NH3] / tot * pAtm, 1e-3);
        if (pN2 <= 0 || pH2 <= 0) return 0;
        double kp = kp(t, pBar);
        double k2 = 1.7698e15 * Math.exp(-40765 / (1.987 * t));
        double h3 = pH2 * pH2 * pH2, a2 = pNh3 * pNh3;
        double r = 2 * k2 * (kp * kp * pN2 * Math.pow(h3 / a2, ALPHA) - Math.pow(a2 / h3, 1 - ALPHA));
        return r * 1000 / 3600 * ETA * activity;
    }

    /** Выход слоя: состав, температура, максимум температуры по слою. */
    public record Bed(double[] n, double outK) {
    }

    /** Адиабатический слой объёмом {@code volumeM3} с входом при {@code tInK}. */
    public static Bed bed(double[] in, double tInK, double pBar, double volumeM3, double activity) {
        double[] n = in.clone();
        double h = Species.enthalpy(n, tInK), t = tInK;
        int steps = 120;
        double dv = volumeM3 / steps;
        for (int i = 0; i < steps; i++) {
            double r = rate(n, t, pBar, activity);
            if (r <= 0) break;
            // шаг не должен перескочить равновесие при температуре после шага (реакция экзотермична)
            double dx = r * dv;
            double[] next = n;
            double tNext = t;
            for (int k = 0; k < 8; k++) {
                next = advance(n, dx);
                tNext = Species.temperatureFor(next, h, t);
                if (next[NH3] <= equilibrium(n, tNext, pBar)[NH3]) break;
                dx /= 2;
                next = n;
                tNext = t;
            }
            if (next == n) break;
            n = next;
            t = tNext;
        }
        return new Bed(n, t);
    }

    /** Колонна синтеза: три слоя, вход каждого — {@code bedInK}. */
    public record Converter(double[] out, double[] bedOutK) {
        public double maxK() {
            double m = 0;
            for (double v : bedOutK) m = Math.max(m, v);
            return m;
        }
    }

    public static Converter converter(double[] in, double bedInK, double pBar, double[] bedVolumesM3, double activity) {
        double[] n = in.clone(), outs = new double[bedVolumesM3.length];
        for (int i = 0; i < bedVolumesM3.length; i++) {
            Bed b = bed(n, bedInK, pBar, bedVolumesM3[i], activity);
            n = b.n();
            outs[i] = b.outK();
        }
        return new Converter(n, outs);
    }

    /** Давление насыщенного пара жидкого аммиака (Антуан, NIST, 239–371 К), бар. */
    public static double vaporPressureBar(double t) {
        return Math.pow(10, 4.86886 - 1113.928 / (t - 10.409));
    }

    /** Теплота испарения аммиака около −10…+30 °C, Дж/моль. */
    public static final double LATENT_J_MOL = 21.4e3;
}
