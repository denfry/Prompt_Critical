package dev.denfry.promptcritical.sim.gasprocessing;

/**
 * Уравнение состояния Пенга–Робинсона (Peng, Robinson, Ind. Eng. Chem. Fundam. 15 (1976) 59) для газовой смеси с
 * правилами смешения Ван-дер-Ваальса без коэффициентов парного взаимодействия: Z-фактор, остаточная энтальпия,
 * изоэнтальпийное расширение (дросселирование) и коэффициент Джоуля–Томсона.
 *
 * <p>a_i = 0,45724R²T_c²/p_c·[1 + κ(1 − √(T/T_c))]², κ = 0,37464 + 1,54226ω − 0,26992ω²; b_i = 0,07780RT_c/p_c.
 * Остаточная энтальпия H_R = RT(Z − 1) + (T·da/dT − a)/(2√2b)·ln[(Z + (1 + √2)B)/(Z + (1 − √2)B)] (Poling et al., гл. 6).
 * Энтальпия идеального газа — по теплоёмкостям при 300 K ({@link GasComponent#cpIdealJMolK}); в интервале 230–320 K
 * это даёт ошибку теплоёмкости в несколько процентов.
 */
public final class PengRobinson {
    public static final double R = 8.314_462;
    private static final double SQRT2 = Math.sqrt(2);

    private PengRobinson() {
    }

    private static double ai(GasComponent c, double t) {
        double kappa = 0.37464 + 1.54226 * c.omega - 0.26992 * c.omega * c.omega;
        double s = 1 + kappa * (1 - Math.sqrt(t / c.tcK));
        return 0.45724 * R * R * c.tcK * c.tcK / c.pcPa * s * s;
    }

    private static double bi(GasComponent c) {
        return 0.07780 * R * c.tcK / c.pcPa;
    }

    static double mixA(double[] x, double t) {
        GasComponent[] cs = GasComponent.values();
        double[] a = new double[cs.length];
        for (int i = 0; i < cs.length; i++) a[i] = x[i] > 0 ? ai(cs[i], t) : 0;
        double sum = 0;
        for (int i = 0; i < cs.length; i++)
            for (int j = 0; j < cs.length; j++) sum += x[i] * x[j] * Math.sqrt(a[i] * a[j]);
        return sum;
    }

    static double mixB(double[] x) {
        double b = 0;
        for (GasComponent c : GasComponent.values()) b += x[c.ordinal()] * bi(c);
        return b;
    }

    /** Z-фактор газовой фазы (наибольший корень кубического уравнения). */
    public static double zVapor(double[] x, double t, double p) {
        double a = mixA(x, t), b = mixB(x);
        double aa = a * p / (R * R * t * t), bb = b * p / (R * t);
        return largestRoot(-(1 - bb), aa - 3 * bb * bb - 2 * bb, -(aa * bb - bb * bb - bb * bb * bb));
    }

    /** Наибольший вещественный корень z³ + c2z² + c1z + c0 = 0 (тригонометрическая формула Кардано). */
    static double largestRoot(double c2, double c1, double c0) {
        double q = (3 * c1 - c2 * c2) / 9, r = (9 * c2 * c1 - 27 * c0 - 2 * c2 * c2 * c2) / 54;
        double d = q * q * q + r * r;
        if (d > 0) {
            double s = Math.cbrt(r + Math.sqrt(d)), u = Math.cbrt(r - Math.sqrt(d));
            return s + u - c2 / 3;
        }
        double theta = Math.acos(r / Math.sqrt(-q * q * q));
        double m = 2 * Math.sqrt(-q);
        double z1 = m * Math.cos(theta / 3) - c2 / 3, z2 = m * Math.cos((theta + 2 * Math.PI) / 3) - c2 / 3,
                z3 = m * Math.cos((theta + 4 * Math.PI) / 3) - c2 / 3;
        return Math.max(z1, Math.max(z2, z3));
    }

    /** Остаточная энтальпия газа, Дж/моль. */
    public static double residualEnthalpy(double[] x, double t, double p) {
        double a = mixA(x, t), b = mixB(x), dt = 0.01;
        double dadt = (mixA(x, t + dt) - mixA(x, t - dt)) / (2 * dt);
        double z = zVapor(x, t, p), bb = b * p / (R * t);
        return R * t * (z - 1) + (t * dadt - a) / (2 * SQRT2 * b) * Math.log((z + (1 + SQRT2) * bb) / (z + (1 - SQRT2) * bb));
    }

    /** Энтальпия газа от 298,15 K в идеальном состоянии, Дж/моль. */
    public static double enthalpy(double[] x, double t, double p) {
        double cp = 0;
        for (GasComponent c : GasComponent.values()) cp += x[c.ordinal()] * c.cpIdealJMolK;
        return cp * (t - 298.15) + residualEnthalpy(x, t, p);
    }

    /** Температура после дросселирования от (t1, p1) до p2 при постоянной энтальпии, К. */
    public static double throttle(double[] x, double t1, double p1, double p2) {
        double h = enthalpy(x, t1, p1);
        double lo = t1 - 120, hi = t1 + 20;
        for (int i = 0; i < 80; i++) {
            double mid = 0.5 * (lo + hi);
            if (enthalpy(x, mid, p2) > h) hi = mid;
            else lo = mid;
        }
        return 0.5 * (lo + hi);
    }

    /**
     * Дросселирование двухфазного потока: газ и жидкость, разделённые флешем при (t1, p1), идут через штуцер вместе.
     * Газ расширяется по своей энтальпии (уравнение состояния для его собственного, тощего состава), жидкость — тепловой
     * балласт с теплоёмкостью {@link #LIQUID_CP_J_KG_K}, её собственный эффект Джоуля–Томсона мал. Баланс:
     * V·[H_г(T₂, p₂) − H_г(t1, p1)] + (1 − V)·M_ж·c_ж·(T₂ − t1) = 0 [приближение: дополнительная конденсация при
     * расширении не учитывается]. Энтальпию смеси по общему составу считать нельзя: для жирной смеси при низкой
     * температуре наибольший корень уравнения уже «жидкий», и охлаждение завышается вдвое.
     */
    public static double throttleTwoPhase(double[] x, double t1, double p1, double p2) {
        Flash f = Flash.of(x, t1, p1);
        double v = f.vapor();
        if (v <= 0) return t1;
        double[] y = f.y();
        double liquidJK = (1 - v) * GasComponent.molarMass(f.x()) / 1000 * LIQUID_CP_J_KG_K;
        double h1 = enthalpy(y, t1, p1);
        double lo = t1 - 120, hi = t1 + 20;
        for (int i = 0; i < 80; i++) {
            double mid = 0.5 * (lo + hi);
            double balance = v * (enthalpy(y, mid, p2) - h1) + liquidJK * (mid - t1);
            if (balance > 0) hi = mid;
            else lo = mid;
        }
        return 0.5 * (lo + hi);
    }

    /** Теплоёмкость жидких углеводородов C3–C6, Дж/(кг·К) (GPSA, рис. 23-2). */
    public static final double LIQUID_CP_J_KG_K = 2_300;

    /** Коэффициент Джоуля–Томсона (∂T/∂p)_H, К/Па. */
    public static double jouleThomson(double[] x, double t, double p) {
        double dp = Math.max(1e3, 1e-3 * p);
        return (t - throttle(x, t, p + dp / 2, p - dp / 2)) / dp;
    }
}
