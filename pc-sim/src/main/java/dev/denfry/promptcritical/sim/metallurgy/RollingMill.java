package dev.denfry.promptcritical.sim.metallurgy;

import java.util.Objects;

/**
 * Клеть {@code rolling_mill_stand} (08 §8.7.2), Целиков упрощённо:
 * F = k_f·w·√(RΔh)·Q_p, M = 2Fλ√(RΔh), P = Mω, захват при Δh ≤ μ²R.
 *
 * <p>k_f горячей = 120 МПа при 1100 °C ×2 на каждые −100 К, умноженное на √(σ₀,₂/245 МПа) марки [моё]; сверху
 * ограничено холодным k_f = 1,8·σ₀,₂ (400–700 МПа). Между {@link #CRACK_LOW_K} и Ar₃ — трещины. Уширение по Зибелю
 * Δb = C·(Δh/h)·√(RΔh), длина — из постоянства объёма. Температура: + работа деформации k_f·ln(h/h')/(ρc),
 * горячий пропуск — ещё −{@link #PASS_CHILL_K} на валки и излучение [BALANCE].
 */
public final class RollingMill {
    public static final double KF_REF_PA = 120e6, KF_REF_K = 1373.15, KF_DOUBLING_K = 100, KF_COLD_PER_YIELD = 1.8;
    public static final double REF_YIELD_PA = 245e6;
    public static final double AR3_K = 1123.15, CRACK_LOW_K = 573.15;
    public static final double QP = 1.1, LEVER_ARM = 0.5, SPREAD_C = 0.35, PASS_CHILL_K = 15, STEEL_C = 650;
    /** Плита — ширина от 4 толщин; круг/квадрат сортовых валков — до 30 мм. */
    public static final double PLATE_ASPECT = 4, ROD_MAX_M = 0.03;

    private final MillRollKind rolls;
    private final double rollRadiusM, mu, maxTorqueNm;

    public RollingMill(MillRollKind rolls, double rollRadiusM, double mu, double maxTorqueNm) {
        this.rolls = Objects.requireNonNull(rolls, "rolls");
        if (rolls != MillRollKind.FLAT && rolls != MillRollKind.SECTION)
            throw new IllegalArgumentException("stand rolls FLAT or SECTION, not " + rolls);
        if (!(rollRadiusM > 0 && mu > 0 && maxTorqueNm > 0)) throw new IllegalArgumentException("mill parameters");
        this.rollRadiusM = rollRadiusM;
        this.mu = mu;
        this.maxTorqueNm = maxTorqueNm;
    }

    /** F, Н. */
    public static double force(double kfPa, double widthM, double radiusM, double dhM, double qp) {
        return kfPa * widthM * Math.sqrt(radiusM * dhM) * qp;
    }

    /** M на двух валках, Н·м. */
    public static double torque(double forceN, double lever, double radiusM, double dhM) {
        return 2 * forceN * lever * Math.sqrt(radiusM * dhM);
    }

    /** Сопротивление деформации заготовки, Па. */
    public static double flowStressPa(StockState s) {
        double y = s.grade().yieldPa();
        double hot = KF_REF_PA * Math.pow(2, (KF_REF_K - s.tempK()) / KF_DOUBLING_K) * Math.sqrt(y / REF_YIELD_PA);
        return Math.min(hot, KF_COLD_PER_YIELD * y);
    }

    /** Пропуск с зазором валков {@code gapM} при угловой скорости валков {@code omega}, рад/с. */
    public PassResult pass(StockState in, double gapM, double omega) {
        if (!(gapM > 0) || !(omega >= 0)) throw new IllegalArgumentException("gap/omega");
        double h = in.thicknessM(), dh = h - gapM;
        if (dh <= 0) return new PassResult(PassResult.Outcome.ROLLED, in, 0, 0, 0);
        if (dh > mu * mu * rollRadiusM) return new PassResult(PassResult.Outcome.NOT_BITTEN, in, 0, 0, 0);
        double kf = flowStressPa(in);
        double f = force(kf, in.widthM(), rollRadiusM, dh, QP);
        double m = torque(f, LEVER_ARM, rollRadiusM, dh);
        if (m > maxTorqueNm) return new PassResult(PassResult.Outcome.SHEAR_PIN, in, f, m, m * omega);
        double t = in.tempK();
        boolean hot = t >= AR3_K;
        boolean cracked = in.cracked() || t > CRACK_LOW_K && t < AR3_K;
        double w = in.widthM() + SPREAD_C * dh / h * Math.sqrt(rollRadiusM * dh);
        double len = h * in.widthM() * in.lengthM() / (gapM * w);
        double tOut = t + kf * Math.log(h / gapM) / (StockState.DENSITY * STEEL_C) - (hot ? PASS_CHILL_K : 0);
        StockState out = new StockState(shapeAfter(in.shape(), gapM, w), gapM, w, len, tOut, in.grade(), cracked,
                in.porous());
        return new PassResult(PassResult.Outcome.ROLLED, out, f, m, m * omega);
    }

    private StockShape shapeAfter(StockShape s, double h, double w) {
        if (rolls == MillRollKind.FLAT) return w >= PLATE_ASPECT * h ? StockShape.PLATE : StockShape.BILLET;
        return h <= ROD_MAX_M ? StockShape.ROD : (s == StockShape.INGOT ? StockShape.BILLET : s);
    }

    public MillRollKind rolls() {
        return rolls;
    }
}
