package dev.denfry.promptcritical.sim.methanol;

import dev.denfry.promptcritical.sim.nitrogen.Species;
import dev.denfry.promptcritical.sim.thermo.if97.If97;

/**
 * Синтез метанола на медно-цинковом катализаторе (Cu/ZnO/Al₂O₃, процесс низкого давления ICI/Lurgi, 5–10 МПа,
 * 220–280 °C).
 *
 * <p>Реакции: (1) CO + 2H₂ ⇌ CH₃OH (−90,6 кДж/моль), (2) CO₂ + 3H₂ ⇌ CH₃OH + H₂O (−49,5), (3) обратная конверсия CO
 * CO₂ + H₂ ⇌ CO + H₂O (+41,1); независимы две из трёх. Константы равновесия (идеальный газ, бар) — Graaf, Sijtsema,
 * Stamhuis, Joosten, «Chemical equilibria in methanol synthesis», Chem. Eng. Sci. 41 (1986) 2883:
 * lg K₁ = 5139/T − 12,621; lg K₂ = 3066/T − 10,592; lg K₃ = −2073/T + 2,029 (K₁ = K₂/K₃).
 *
 * <p>Кинетика — Vanden Bussche, Froment, «A steady-state kinetic model for methanol synthesis and the water gas shift
 * reaction on a commercial Cu/ZnO/Al₂O₃ catalyst», J. Catal. 161 (1996) 1, табл. 2: метанол образуется из CO₂, CO
 * переходит в CO₂ через конверсию;
 * r_MeOH = k₁·p_CO₂·p_H₂·(1 − p_H₂O·p_MeOH/(K₂·p_H₂³·p_CO₂))/β³, r_RWGS = k₅·p_CO₂·(1 − p_H₂O·p_CO/(K₃·p_CO₂·p_H₂))/β,
 * β = 1 + K_a·p_H₂O/p_H₂ + √K_H₂·√p_H₂ + K_H₂O·p_H₂O; моль/(кг кат.·с), давления в барах. Модель построена по данным
 * 180–280 °C и до 51 бар; при 50–100 бар её применяют к промышленным реакторам Lurgi (Manenti et al., Chem. Eng. Sci.
 * 66 (2011) 152) — здесь так же.
 *
 * <p>Реактор — трубчатый, изотермический (Lurgi): катализатор в трубах, в межтрубье кипит вода, слой держится на
 * {@link #BED_RISE_K} выше температуры кипения. Без охлаждения слой идёт к адиабатической равновесной температуре.
 * Потоки — массивы мольных расходов {@link Species}.
 */
public final class MethanolSynthesis {
    static final int CO = Species.CO.ordinal(), CO2 = Species.CO2.ordinal(), H2 = Species.H2.ordinal(),
            H2O = Species.H2O.ordinal(), MEOH = Species.CH3OH.ordinal(), CH4 = Species.CH4.ordinal(), N2 = Species.N2.ordinal(),
            AR = Species.AR.ordinal();
    private static final double RG = Species.R, TINY = 1e-14;
    /** Перегрев слоя над кипящей водой межтрубья в трубчатом реакторе, К [BALANCE: Lurgi 10–15 К на пике]. */
    public static final double BED_RISE_K = 8;
    /** Спекание меди: выше этой температуры слоя активность падает заметно (Twigg, Spencer, Appl. Catal. A 212 (2001) 161). */
    public static final double SINTER_K = 573.15;

    private MethanolSynthesis() {
    }

    /** K₁: CO + 2H₂ ⇌ CH₃OH, бар⁻². */
    public static double kCo(double t) {
        return Math.pow(10, 5139 / t - 12.621);
    }

    /** K₂: CO₂ + 3H₂ ⇌ CH₃OH + H₂O, бар⁻². */
    public static double kCo2(double t) {
        return Math.pow(10, 3066 / t - 10.592);
    }

    /** K₃: CO₂ + H₂ ⇌ CO + H₂O. */
    public static double kRwgs(double t) {
        return Math.pow(10, -2073 / t + 2.029);
    }

    /** Модуль (стехиометрическое число) S = (H₂ − CO₂)/(CO + CO₂); оптимум свежего газа ≈ 2,05. */
    public static double stoichiometricNumber(double[] n) {
        double c = n[CO] + n[CO2];
        return c > 0 ? (n[H2] - n[CO2]) / c : Double.POSITIVE_INFINITY;
    }

    // --- равновесие ---

    /** a — степень реакции (1), b — реакции (3). */
    private static void applyEq(double[] n, double a, double b) {
        n[CO] += -a + b;
        n[H2] -= 2 * a + b;
        n[MEOH] += a;
        n[CO2] -= b;
        n[H2O] += b;
    }

    private static double ln(double v) {
        return Math.log(Math.max(v, TINY));
    }

    private static double innerShift(double[] n, double a, double lnK3) {
        double lo = Math.max(a - n[CO], -n[H2O]), hi = Math.min(n[CO2], n[H2] - 2 * a);
        if (hi <= lo) return 0.5 * (lo + hi);
        double[] m = new double[n.length];
        for (int i = 0; i < 60; i++) {
            double b = 0.5 * (lo + hi);
            System.arraycopy(n, 0, m, 0, n.length);
            applyEq(m, a, b);
            if (ln(m[CO]) + ln(m[H2O]) - ln(m[CO2]) - ln(m[H2]) > lnK3) hi = b;
            else lo = b;
        }
        return 0.5 * (lo + hi);
    }

    /**
     * Химическое равновесие реакций (1) и (3) (а с ними и (2)) из состава {@code n} при {@code t}, {@code pBar}:
     * вложенная бисекция по степеням реакций в логарифмах.
     */
    public static double[] equilibrium(double[] n, double t, double pBar) {
        if (n[CO] + n[CO2] + n[MEOH] <= 0 || n[H2] + n[MEOH] <= 0) return n.clone();
        double lnK1 = Math.log(kCo(t)), lnK3 = Math.log(kRwgs(t)), lnP2 = 2 * Math.log(pBar);
        double lo = -n[MEOH], hi = Math.min(n[CO] + n[CO2], Math.min((n[H2] + n[CO]) / 3, (n[H2] + n[H2O]) / 2));
        double[] m = new double[n.length];
        for (int i = 0; i < 60; i++) {
            double a = 0.5 * (lo + hi);
            double b = innerShift(n, a, lnK3);
            System.arraycopy(n, 0, m, 0, n.length);
            applyEq(m, a, b);
            double q = ln(m[MEOH]) + 2 * Math.log(Species.total(m)) - ln(m[CO]) - 2 * ln(m[H2]) - lnP2;
            if (q > lnK1) hi = a;
            else lo = a;
        }
        double a = 0.5 * (lo + hi);
        double[] out = n.clone();
        applyEq(out, a, innerShift(n, a, lnK3));
        for (int i = 0; i < out.length; i++) out[i] = Math.max(0, out[i]);
        return out;
    }

    /** Равновесная конверсия CO из газа H₂:CO = {@code h2PerCo} без CO₂ (тест T10.7 GDD). */
    public static double equilibriumCoConversion(double t, double pBar, double h2PerCo) {
        double[] n = Species.flow();
        n[CO] = 1;
        n[H2] = h2PerCo;
        double[] e = equilibrium(n, t, pBar);
        return 1 - e[CO] - e[CO2];
    }

    /**
     * Адиабатическая равновесная температура: газ с {@code tInK} без отвода тепла приходит к равновесию при
     * температуре, где энтальпия равновесной смеси равна энтальпии входа. Так греется слой, когда в межтрубье нет воды.
     */
    public static double adiabaticEquilibriumK(double[] in, double tInK, double pBar) {
        double h = Species.enthalpy(in, tInK);
        double lo = tInK, hi = tInK + 500;
        for (int i = 0; i < 24; i++) {
            double t = 0.5 * (lo + hi);
            if (Species.enthalpy(equilibrium(in, t, pBar), t) > h) hi = t;
            else lo = t;
        }
        return 0.5 * (lo + hi);
    }

    // --- кинетика Vanden Bussche–Froment ---

    /** Скорости r_MeOH (из CO₂) и r_RWGS, моль/(кг кат.·с), с активностью катализатора. */
    static double[] rates(double[] n, double t, double pBar, double activity) {
        double tot = Species.total(n);
        double pCo2 = n[CO2] / tot * pBar, pH2 = Math.max(n[H2] / tot * pBar, 1e-6), pH2o = n[H2O] / tot * pBar,
                pM = n[MEOH] / tot * pBar, pCo = n[CO] / tot * pBar;
        double rt = RG * t;
        double k1 = 1.07 * Math.exp(36696 / rt), k5 = 1.22e10 * Math.exp(-94765 / rt);
        double sqrtKh2 = 0.499 * Math.exp(17197 / rt), kH2o = 6.62e-11 * Math.exp(124119 / rt);
        double beta = 1 + 3453.38 * pH2o / pH2 + sqrtKh2 * Math.sqrt(pH2) + kH2o * pH2o;
        double rM = k1 * (pCo2 * pH2 - pH2o * pM / (kCo2(t) * pH2 * pH2)) / (beta * beta * beta);
        double rR = k5 * (pCo2 - pH2o * pCo / (kRwgs(t) * pH2)) / beta;
        return new double[] {activity * rM, activity * rR};
    }

    /** xm — CO₂ + 3H₂ → CH₃OH + H₂O, xr — CO₂ + H₂ → CO + H₂O. */
    private static void applyRates(double[] n, double xm, double xr) {
        n[CO2] -= xm + xr;
        n[H2] -= 3 * xm + xr;
        n[MEOH] += xm;
        n[H2O] += xm + xr;
        n[CO] += xr;
    }

    /**
     * Изотермический реактор идеального вытеснения: газ {@code in} (моль/с) на {@code catalystKg} кг катализатора с
     * активностью {@code activity} при температуре слоя {@code t}. Интегрирование — метод средней точки с шагом по
     * массе катализатора, который дробится, пока состав за шаг меняется больше чем на 5 %.
     */
    public static double[] reactor(double[] in, double t, double pBar, double catalystKg, double activity) {
        double[] n = in.clone();
        if (!(activity > 0) || !(catalystKg > 0) || Species.total(n) <= 0) return n;
        double w = 0, hMax = catalystKg / 50, h = hMax / 64;
        double[] mid = new double[n.length], trial = new double[n.length];
        int guard = 0;
        while (w < catalystKg * (1 - 1e-12) && guard++ < 20_000) {
            h = Math.min(h, catalystKg - w);
            double[] r1 = rates(n, t, pBar, activity);
            System.arraycopy(n, 0, mid, 0, n.length);
            applyRates(mid, r1[0] * h / 2, r1[1] * h / 2);
            boolean ok = min(mid) >= 0;
            if (ok) {
                double[] r2 = rates(mid, t, pBar, activity);
                System.arraycopy(n, 0, trial, 0, n.length);
                applyRates(trial, r2[0] * h, r2[1] * h);
                ok = min(trial) >= 0 && maxRelativeChange(n, trial) <= 0.05;
            }
            if (!ok) {
                h /= 2;
                if (h < hMax * 1e-9) return equilibrium(n, t, pBar); // жёсткий участок: газ у равновесия
                continue;
            }
            System.arraycopy(trial, 0, n, 0, n.length);
            w += h;
            h = Math.min(hMax, h * 1.5);
        }
        return n;
    }

    private static double min(double[] n) {
        double m = Double.MAX_VALUE;
        for (double v : n) m = Math.min(m, v);
        return m;
    }

    private static double maxRelativeChange(double[] a, double[] b) {
        double floor = 1e-4 * Species.total(a), m = 0;
        for (int i = 0; i < a.length; i++) m = Math.max(m, Math.abs(b[i] - a[i]) / Math.max(a[i], floor));
        return m;
    }

    // --- сепаратор ---

    /** Давление пара метанола, бар: Антуан по NIST (Ambrose, Sprake, 1970), 288–357 К. */
    public static double methanolVaporPressureBar(double t) {
        return Math.pow(10, 5.20409 - 1581.341 / (t - 33.50));
    }

    /**
     * Сепаратор после водяного холодильника: метанол и вода конденсируются, в газе остаются их пары по закону Рауля
     * (y_i = x_i·p_s,i/P). Возвращает {газ, жидкость}.
     */
    public static double[][] separate(double[] out, double t, double pBar) {
        double inert = Species.total(out) - out[MEOH] - out[H2O];
        double pm = methanolVaporPressureBar(t) / pBar, pw = If97.psat(t) / 1e5 / pBar;
        double lm = out[MEOH], lw = out[H2O];
        for (int i = 0; i < 40; i++) {
            double l = lm + lw;
            if (l <= 0) break;
            double ym = lm / l * pm, yw = lw / l * pw;
            double v = inert / Math.max(1e-6, 1 - ym - yw);
            lm = Math.max(0, out[MEOH] - ym * v);
            lw = Math.max(0, out[H2O] - yw * v);
        }
        double[] gas = out.clone(), liq = Species.flow();
        gas[MEOH] = out[MEOH] - lm;
        gas[H2O] = out[H2O] - lw;
        liq[MEOH] = lm;
        liq[H2O] = lw;
        return new double[][] {gas, liq};
    }
}
