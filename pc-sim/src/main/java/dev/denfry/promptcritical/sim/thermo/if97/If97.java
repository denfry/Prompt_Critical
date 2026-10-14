package dev.denfry.promptcritical.sim.thermo.if97;

/**
 * Эталонная реализация IAPWS-IF97 (ревизия 2012) с транспортными свойствами
 * (01 §1.3.1). Используется для генерации таблиц {@code if97_tables.bin} и
 * как оракул в тестах; в горячий цикл не попадает (аллоцирует, ~мкс на вызов).
 *
 * <p>Единицы — СИ: Па, К, кг/м³, Дж/кг. Регионы: 1 — жидкость, 2 — пар,
 * 3 — окрестность критической точки, 4 — линия насыщения и двухфазная смесь,
 * 5 — высокотемпературный пар.
 */
public final class If97 {
    /** Удельная газовая постоянная воды в IF97, Дж/(кг·К). */
    public static final double R = 461.526;
    public static final double T_C = 647.096;
    public static final double P_C = 22.064e6;
    public static final double RHO_C = 322.0;
    /** Давление насыщения при 273,15 К, Па — нижняя граница жидкой фазы. */
    public static final double P_MIN = 611.212677;
    public static final double T_MIN = 273.15;
    /** Нижняя граница региона 3 по температуре, К. */
    public static final double T_13 = 623.15;
    /** Граница регионов 2 и 5, К. */
    public static final double T_25 = 1073.15;
    public static final double T_MAX = 2273.15;

    private static final double[] N4 = {
        0.11670521452767e4, -0.72421316703206e6, -0.17073846940092e2, 0.12020824702470e5,
        -0.32325550322333e7, 0.14915108613530e2, -0.48232657361591e4, 0.40511340542057e6,
        -0.23855557567849, 0.65017534844798e3};
    private static final double[] N23 = {
        0.34805185628969e3, -0.11671859879975e1, 0.10192970039326e-2, 0.57254459862746e3,
        0.13918839778870e2};

    /** p_sat(623,15 К) ≈ 16,529 МПа: выше линия насыщения проходит по региону 3. */
    public static final double P_S13 = psat(T_13);
    /**
     * Допуск «на линии насыщения» для пара в регионе 3: при T = T_s плотность
     * {@link #rho3} берётся с жидкой ветви (p = p_s), поэтому корень по T у самой
     * линии дал бы жидкость. Состояния ближе 10⁻⁹ к h'' или s'' — насыщенный пар.
     */
    private static final double SAT_EPS = 1e-9;

    private If97() {}

    // ---------------------------------------------------------------- регион 4 и B23

    /** Давление насыщения p_s(T), Па; T ∈ [273,15; 647,096] К. */
    public static double psat(double t) {
        double th = t + N4[8] / (t - N4[9]);
        double a = th * th + N4[0] * th + N4[1];
        double b = N4[2] * th * th + N4[3] * th + N4[4];
        double c = N4[5] * th * th + N4[6] * th + N4[7];
        double x = 2 * c / (-b + Math.sqrt(b * b - 4 * a * c));
        return 1e6 * x * x * x * x;
    }

    /** Температура насыщения T_s(p), К; p ∈ [611,213 Па; 22,064 МПа]. */
    public static double tsat(double p) {
        double beta = Math.pow(p / 1e6, 0.25);
        double e = beta * beta + N4[2] * beta + N4[5];
        double f = N4[0] * beta * beta + N4[3] * beta + N4[6];
        double g = N4[1] * beta * beta + N4[4] * beta + N4[7];
        double d = 2 * g / (-f - Math.sqrt(f * f - 4 * e * g));
        return (N4[9] + d - Math.sqrt((N4[9] + d) * (N4[9] + d) - 4 * (N4[8] + N4[9] * d))) / 2;
    }

    /** Граница регионов 2 и 3: p_B23(T), Па. */
    public static double pB23(double t) {
        return 1e6 * (N23[0] + N23[1] * t + N23[2] * t * t);
    }

    /** Граница регионов 2 и 3: T_B23(p), К. */
    public static double tB23(double p) {
        return N23[3] + Math.sqrt((p / 1e6 - N23[4]) / N23[2]);
    }

    // ---------------------------------------------------------------- (p, T)

    /** Регион однофазного состояния (p, T); на линии насыщения ниже 623,15 К — 1. */
    public static int region(double p, double t) {
        if (t > T_25) return 5;
        if (t <= T_13) return p >= psat(Math.max(t, T_MIN)) ? 1 : 2;
        return t <= 863.15 && p > pB23(t) ? 3 : 2;
    }

    /** Однофазное состояние по (p, T). */
    public static If97Point pt(double p, double t) {
        int region = region(p, t);
        return region == 3 ? fromRegion3(rho3(p, t), t) : inRegion(region, p, t);
    }

    /**
     * Состояние по уравнению заданного региона 1, 2 или 5 без проверки границ.
     * Регион 1 выше T_s и регион 2 ниже T_s дают метастабильное продолжение
     * (перегретая жидкость и переохлаждённый пар), 01 §1.3.2(б).
     */
    public static If97Point inRegion(int region, double p, double t) {
        Gibbs g = switch (region) {
            case 1 -> Region1.gibbs(p, t);
            case 2 -> Region2.gibbs(p, t);
            case 5 -> Region5.gibbs(p, t);
            default -> throw new IllegalArgumentException("region " + region + " has no Gibbs equation");
        };
        double rho = 1.0 / g.v(p, t);
        double cp = g.cp(), cv = g.cv(), drhodp = g.drhodpT(p, t);
        double mu = Transport.viscosity(rho, t);
        double k = Transport.conductivity(rho, t, cp, cv, drhodp, mu);
        return new If97Point(region, p, t, rho, g.h(t), g.u(t), g.s(), cp, cv, g.w(t), Double.NaN, mu, k, drhodp,
                g.dvdT(p));
    }

    /** Состояние региона 3 по (ρ, T). */
    public static If97Point fromRegion3(double rho, double t) {
        Region3.Helmholtz f = Region3.helmholtz(rho, t);
        double cp = f.cp(), cv = f.cv(), dpdrho = f.dpdrhoT(t), drhodp = 1.0 / dpdrho;
        double mu = Transport.viscosity(rho, t);
        double k = Transport.conductivity(rho, t, cp, cv, drhodp, mu);
        return new If97Point(3, f.p(rho, t), t, rho, f.h(t), f.u(t), f.s(), cp, cv, f.w(t), Double.NaN, mu, k, drhodp,
                f.dpdTrho(rho) / (rho * rho * dpdrho));
    }

    /**
     * Плотность региона 3 по (p, T): ниже T_c ветка выбирается по p &gt;= p_s(T)
     * (жидкость) или p &lt; p_s(T) (пар).
     */
    static double rho3(double p, double t) {
        double lo = 1.0, hi;
        boolean vapor = t < T_C && p < psat(t);
        if (vapor) {
            hi = satDensity3(t, false);
            if (p3(hi, t) <= p) return hi; // на линии насыщения с точностью округления
        } else {
            if (t < T_C) {
                lo = satDensity3(t, true);
                if (p3(lo, t) >= p) return lo;
            }
            // полином региона 3 теряет физичность далеко за ρ ≈ 800, поэтому
            // верхнюю границу наращиваем от нижней, а не берём с запасом
            hi = Math.max(lo * 1.05, 50.0);
            while (p3(hi, t) < p) {
                lo = hi;
                hi *= 1.1;
                if (hi > 1000.0) throw new IllegalArgumentException("p=" + p + " above region 3 at T=" + t);
            }
        }
        return solveRho3(p, t, lo, hi);
    }

    private static double solveRho3(double p, double t, double lo, double hi) {
        return Roots.solve(rho -> {
            Region3.Helmholtz f = Region3.helmholtz(rho, t);
            return new double[] {f.p(rho, t) - p, f.dpdrhoT(t)};
        }, lo, hi, 0.5 * (lo + hi), 1e-11 * hi);
    }

    private static double p3(double rho, double t) {
        return Region3.helmholtz(rho, t).p(rho, t);
    }

    // ---------------------------------------------------------------- насыщение

    /** Насыщенная жидкость при давлении p (p ≤ p_c). */
    public static If97Point satLiquid(double p) {
        double t = tsat(p);
        return p <= P_S13 ? inRegion(1, p, t) : fromRegion3(satDensity3(t, true), t);
    }

    /** Насыщенный пар при давлении p (p ≤ p_c). */
    public static If97Point satVapor(double p) {
        double t = tsat(p);
        return p <= P_S13 ? inRegion(2, p, t) : fromRegion3(satDensity3(t, false), t);
    }

    /**
     * Плотность насыщения ρ' ({@code liquid}) или ρ'' в регионе 3: корень
     * p₃(ρ, T) = p_s(T) на своей ветви; старт — вспомогательные уравнения IAPWS (1992).
     */
    static double satDensity3(double t, boolean liquid) {
        if (t >= T_C) return RHO_C;
        double th = 1.0 - t / T_C;
        double guess;
        if (liquid) {
            double c3 = Math.cbrt(th);
            guess = RHO_C * (1 + 1.99274064 * c3 + 1.09965342 * c3 * c3 - 0.510839303 * Math.pow(th, 5.0 / 3)
                    - 1.75493479 * Math.pow(th, 16.0 / 3) - 45.5170352 * Math.pow(th, 43.0 / 3)
                    - 6.74694450e5 * Math.pow(th, 110.0 / 3));
        } else {
            guess = RHO_C * Math.exp(-2.03150240 * Math.pow(th, 2.0 / 6) - 2.68302940 * Math.pow(th, 4.0 / 6)
                    - 5.38626492 * Math.pow(th, 8.0 / 6) - 17.2991605 * Math.pow(th, 18.0 / 6)
                    - 44.7586581 * Math.pow(th, 37.0 / 6) - 63.9201063 * Math.pow(th, 71.0 / 6));
        }
        return branchRoot(psat(t), t, guess, liquid);
    }

    private static double branchRoot(double ps, double t, double guess, boolean liquid) {
        double rho = guess;
        for (int it = 0; it < 50; it++) {
            Region3.Helmholtz f = Region3.helmholtz(rho, t);
            double dp = f.dpdrhoT(t);
            double next;
            if (dp <= 0) {
                next = liquid ? rho * 1.01 : rho * 0.99; // внутри спинодали — уходим на свою ветвь
            } else {
                next = rho - (f.p(rho, t) - ps) / dp;
                next = liquid ? Math.max(next, RHO_C) : Math.min(Math.max(next, 0.5 * rho), RHO_C);
            }
            if (Math.abs(next - rho) < 1e-12 * rho) return next;
            rho = next;
        }
        return branchRootBracketed(ps, t, guess, liquid);
    }

    /**
     * Запасной путь у самой критической точки, где p(ρ) почти плоская: корень
     * между спинодалью и устойчивой точкой своей ветви, где p по ρ монотонна.
     */
    private static double branchRootBracketed(double ps, double t, double guess, boolean liquid) {
        double stable = guess;
        for (int it = 0; it < 200; it++) {
            Region3.Helmholtz f = Region3.helmholtz(stable, t);
            boolean beyond = liquid ? f.p(stable, t) > ps : f.p(stable, t) < ps;
            if (f.dpdrhoT(t) > 0 && beyond) break;
            stable = liquid ? stable * 1.01 : stable * 0.99;
        }
        double sp = spinodal3(t, stable);
        double psp = p3(sp, t);
        if (liquid ? psp >= ps : psp <= ps) return sp; // петля вырождена: ρ_sat ≈ спинодаль
        return liquid ? solveRho3(ps, t, sp, stable) : solveRho3(ps, t, stable, sp);
    }

    // ---------------------------------------------------------------- (p, h)

    /** Состояние по (p, h), включая двухфазную область (регион 4). */
    public static If97Point ph(double p, double h) {
        if (p <= P_S13) {
            double ts = p >= P_MIN ? tsat(p) : T_MIN;
            if (p >= P_MIN) {
                If97Point l = inRegion(1, p, ts);
                if (h < l.h()) return solveGibbsT(1, p, h, 200.0, ts, false);
                If97Point v = inRegion(2, p, ts);
                if (h <= v.h()) return mixture(p, h, l, v);
            }
            return vaporSide(p, h, ts);
        }
        if (h <= inRegion(1, p, T_13).h()) return solveGibbsT(1, p, h, 200.0, T_13, false);
        double tb = tB23(p);
        if (h >= inRegion(2, p, tb).h()) return vaporSide(p, h, tb);
        if (p < P_C) {
            double ts = tsat(p);
            If97Point l = fromRegion3(satDensity3(ts, true), ts);
            If97Point v = fromRegion3(satDensity3(ts, false), ts);
            if (h < l.h()) return solveRegion3T(p, h, T_13, ts);
            if (h <= v.h() + SAT_EPS * Math.abs(v.h())) return mixture(p, h, l, v);
            return solveRegion3T(p, h, ts, tb);
        }
        return solveRegion3T(p, h, T_13, tb);
    }

    private static If97Point vaporSide(double p, double h, double tLow) {
        if (h <= inRegion(2, p, T_25).h()) return solveGibbsT(2, p, h, tLow, T_25, false);
        return solveGibbsT(5, p, h, T_25, T_MAX, false);
    }

    private static If97Point mixture(double p, double h, If97Point l, If97Point v) {
        return mixtureX(p, (h - l.h()) / (v.h() - l.h()), l, v);
    }

    private static If97Point mixtureX(double p, double x, If97Point l, If97Point v) {
        double h = l.h() + x * (v.h() - l.h());
        double vm = l.v() + x * (v.v() - l.v());
        return new If97Point(4, p, l.t(), 1.0 / vm, h, l.u() + x * (v.u() - l.u()), l.s() + x * (v.s() - l.s()),
                Double.NaN, Double.NaN, Double.NaN, x, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
    }

    // ---------------------------------------------------------------- (p, s)

    /** Состояние по (p, s), включая двухфазную область: изоэнтропа для критического расхода (01 §1.3.9). */
    public static If97Point ps(double p, double s) {
        if (p <= P_S13) {
            double ts = p >= P_MIN ? tsat(p) : T_MIN;
            if (p >= P_MIN) {
                If97Point l = inRegion(1, p, ts);
                if (s < l.s()) return solveGibbsTs(1, p, s, 200.0, ts);
                If97Point v = inRegion(2, p, ts);
                if (s <= v.s()) return mixtureX(p, (s - l.s()) / (v.s() - l.s()), l, v);
            }
            return vaporSideS(p, s, ts);
        }
        if (s <= inRegion(1, p, T_13).s()) return solveGibbsTs(1, p, s, 200.0, T_13);
        double tb = tB23(p);
        if (s >= inRegion(2, p, tb).s()) return vaporSideS(p, s, tb);
        if (p < P_C) {
            double ts = tsat(p);
            If97Point l = fromRegion3(satDensity3(ts, true), ts);
            If97Point v = fromRegion3(satDensity3(ts, false), ts);
            if (s < l.s()) return solveRegion3Ts(p, s, T_13, ts);
            if (s <= v.s() + SAT_EPS * v.s()) return mixtureX(p, (s - l.s()) / (v.s() - l.s()), l, v);
            return solveRegion3Ts(p, s, ts, tb);
        }
        return solveRegion3Ts(p, s, T_13, tb);
    }

    private static If97Point vaporSideS(double p, double s, double tLow) {
        if (s <= inRegion(2, p, T_25).s()) return solveGibbsTs(2, p, s, tLow, T_25);
        return solveGibbsTs(5, p, s, T_25, T_MAX);
    }

    /**
     * (∂s/∂T)_p = c_p/T. С прижатием к краю: на линии насыщения s границы ветви и
     * s₀ совпадают с точностью до округления, и смены знака может не оказаться.
     */
    private static If97Point solveGibbsTs(int region, double p, double s, double tLo, double tHi) {
        double t = Roots.solve(tt -> {
            If97Point st = inRegion(region, p, tt);
            return new double[] {st.s() - s, st.cp() / tt};
        }, tLo, tHi, 0.5 * (tLo + tHi), 1e-10, true);
        return inRegion(region, p, t);
    }

    private static If97Point solveRegion3Ts(double p, double s, double tLo, double tHi) {
        double t = Roots.solve(tt -> {
            If97Point st = fromRegion3(rho3(p, tt), tt);
            return new double[] {st.s() - s, st.cp() / tt};
        }, tLo, tHi, 0.5 * (tLo + tHi), 1e-10, true);
        return fromRegion3(rho3(p, t), t);
    }

    // ---------------------------------------------------------------- метастабильные ветви

    /**
     * Состояние по (p, h) на заданной ветви без учёта купола (p &lt; p_c): внутри
     * купола — метастабильная перегретая жидкость или переохлаждённый пар,
     * которыми заполнены узлы таблиц 01 §1.3.2(б). На устойчивой стороне
     * совпадает с {@link #ph}.
     *
     * @return {@code null}, если метастабильного состояния нет (за спинодалью региона 3)
     */
    public static If97Point phBranch(double p, double h, boolean liquid) {
        if (p >= P_C || p < P_MIN) return ph(p, h);
        If97Point sat = liquid ? satLiquid(p) : satVapor(p);
        if (liquid ? h <= sat.h() : h >= sat.h()) return ph(p, h);
        double ts = sat.t();
        if (p <= P_S13) {
            return liquid ? solveGibbsT(1, p, h, ts, ts + 60.0, true)
                    : solveGibbsT(2, p, h, Math.max(200.0, ts - 60.0), ts, true);
        }
        return metastable3(p, h, sat.rho(), ts);
    }

    /**
     * Метастабильное состояние региона 3: 2D-Ньютон по (ρ, T) из точки насыщения
     * своей ветви по системе p₃(ρ, T) = p, h₃(ρ, T) = h; выход на (∂p/∂ρ)_T ≤ 0
     * означает спинодаль.
     */
    private static If97Point metastable3(double p, double h, double rho, double t) {
        for (int it = 0; it < 100; it++) {
            Region3.Helmholtz f = Region3.helmholtz(rho, t);
            double dpr = f.dpdrhoT(t);
            if (dpr <= 0) return null;
            double f1 = f.p(rho, t) - p, f2 = f.h(t) - h;
            if (Math.abs(f1) < 1e-10 * p && Math.abs(f2) < 1e-10 * Math.abs(h)) return fromRegion3(rho, t);
            double dpt = f.dpdTrho(rho), dhr = f.dhdrhoT(t), dht = f.dhdTrho();
            double det = dpr * dht - dpt * dhr;
            double dRho = (-f1 * dht + dpt * f2) / det;
            double dT = (-dpr * f2 + dhr * f1) / det;
            double lam = 1;
            while (Math.abs(lam * dRho) > 0.05 * rho || Math.abs(lam * dT) > 5.0) lam *= 0.5;
            rho += lam * dRho;
            t += lam * dT;
        }
        return null;
    }

    /** Спинодаль (∂p/∂ρ)_T = 0 между ρ_sat и ρ_c, бисекцией. */
    private static double spinodal3(double t, double sat) {
        double stable = sat, unstable = RHO_C;
        if (Region3.helmholtz(unstable, t).dpdrhoT(t) > 0) return unstable;
        for (int it = 0; it < 80; it++) {
            double mid = 0.5 * (stable + unstable);
            if (Region3.helmholtz(mid, t).dpdrhoT(t) > 0) stable = mid;
            else unstable = mid;
        }
        return stable;
    }

    private static If97Point solveGibbsT(int region, double p, double h, double tLo, double tHi, boolean clamp) {
        double t = Roots.solve(tt -> {
            If97Point s = inRegion(region, p, tt);
            return new double[] {s.h() - h, s.cp()};
        }, tLo, tHi, 0.5 * (tLo + tHi), 1e-10, clamp);
        return inRegion(region, p, t);
    }

    private static If97Point solveRegion3T(double p, double h, double tLo, double tHi) {
        double t = Roots.solve(tt -> {
            If97Point s = fromRegion3(rho3(p, tt), tt);
            return new double[] {s.h() - h, s.cp()};
        }, tLo, tHi, 0.5 * (tLo + tHi), 1e-10);
        return fromRegion3(rho3(p, t), t);
    }
}
