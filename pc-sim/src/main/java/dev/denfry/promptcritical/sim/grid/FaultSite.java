package dev.denfry.promptcritical.sim.grid;

/**
 * КЗ в острове (07 §7.5.1) после разложения: шунт z_Δ в схеме прямой последовательности, доли обратной и
 * нулевой последовательностей и отклик сети на ток в месте КЗ — столбец Z = Y⁻¹·e. Схема обратной
 * последовательности считается равной прямой без источников (z₂ ≈ z₁), нулевой — той же, умноженной на
 * z₀/z₁ класса, и только в пределах класса напряжения места КЗ (трансформаторы её не пропускают).
 * Строится при разложении острова, токи обновляются раз в тик.
 */
final class FaultSite {
    static final double SQRT3_2 = Math.sqrt(3) / 2;

    /** КЗ внутри ветви {@code index} на доле {@code position} от {@code from}; иначе — на шине {@code index}. */
    final boolean onBranch;
    final int index;
    final FaultType type;
    final VoltageClass cls;
    double position;
    /** Отклик напряжений шин острова (локальные номера) на единичный ток, втекающий в место КЗ. */
    final double[] cRe, cIm;
    /** z₁ — входное сопротивление в месте КЗ; y_Δ — шунт в схеме прямой последовательности. */
    double z1Re, z1Im, yRe, yIm;
    /** I₂ = k₂·I₁, I₀ = k₀·I₁. */
    double k2Re, k2Im, k0Re, k0Im;
    /** Однофазное замыкание при изолированной нейтрали: тока КЗ нет, есть «земля». */
    boolean ground;
    /** Ветвь: y₁ = y/x, y₂ = y/(1 − x), отпайка. */
    double y1Re, y1Im, y2Re, y2Im, tap;
    // токи последовательностей в месте КЗ на последнем решении, о.е. на S_b
    double i1Re, i1Im, i2Re, i2Im, i0Re, i0Im;

    FaultSite(boolean onBranch, int index, FaultType type, VoltageClass cls, int buses) {
        this.onBranch = onBranch;
        this.index = index;
        this.type = type;
        this.cls = cls;
        cRe = new double[buses];
        cIm = new double[buses];
    }

    /** z_Δ по виду КЗ (таблица §7.5.1); zf — сопротивление дуги. */
    void shunt(double zf) {
        double f0 = cls.zeroSequenceRatio;
        boolean isolated = Double.isInfinite(f0);
        double zr, zi;
        k2Re = k2Im = k0Re = k0Im = 0;
        switch (type) {
            case THREE_PHASE -> {
                zr = Math.max(zf, PowerGrid.MIN_FAULT_IMPEDANCE_PU);
                zi = 0;
            }
            case LINE_LINE -> {
                zr = z1Re + zf;
                zi = z1Im;
                k2Re = -1;
            }
            case LINE_LINE_GROUND -> {
                if (isolated) { // двойное замыкание на землю в сети с изолированной нейтралью — как двухфазное
                    zr = z1Re + zf;
                    zi = z1Im;
                    k2Re = -1;
                } else {
                    // z₂ ∥ (z₀ + 3z_f); I₂ = −I₁(z₀ + 3z_f)/Σ, I₀ = −I₁z₂/Σ, Σ = z₂ + z₀ + 3z_f
                    double ar = f0 * z1Re + 3 * zf, ai = f0 * z1Im;
                    double sr = z1Re + ar, si = z1Im + ai, s2 = sr * sr + si * si;
                    double pr = z1Re * ar - z1Im * ai, pi = z1Re * ai + z1Im * ar;
                    zr = (pr * sr + pi * si) / s2;
                    zi = (pi * sr - pr * si) / s2;
                    k2Re = -(ar * sr + ai * si) / s2;
                    k2Im = -(ai * sr - ar * si) / s2;
                    k0Re = -(z1Re * sr + z1Im * si) / s2;
                    k0Im = -(z1Im * sr - z1Re * si) / s2;
                }
            }
            default -> { // однофазное: z₂ + z₀ + 3z_f
                if (isolated) {
                    ground = true;
                    yRe = yIm = 0;
                    return;
                }
                zr = (1 + f0) * z1Re + 3 * zf;
                zi = (1 + f0) * z1Im;
                k2Re = k0Re = 1;
            }
        }
        double d = zr * zr + zi * zi;
        yRe = zr / d;
        yIm = -zi / d;
    }

    /** Токи последовательностей по напряжению прямой последовательности в месте КЗ. */
    void update(double vRe, double vIm) {
        i1Re = yRe * vRe - yIm * vIm;
        i1Im = yRe * vIm + yIm * vRe;
        i2Re = k2Re * i1Re - k2Im * i1Im;
        i2Im = k2Re * i1Im + k2Im * i1Re;
        i0Re = k0Re * i1Re - k0Im * i1Im;
        i0Im = k0Re * i1Im + k0Im * i1Re;
    }

    /** Наибольший фазный ток КЗ, о.е. на S_b. */
    double phaseCurrentPu() {
        return phaseMax(i1Re, i1Im, i2Re, i2Im, i0Re, i0Im);
    }

    /** Нулевая последовательность доходит до шины класса {@code c}. */
    boolean zeroReaches(VoltageClass c) {
        return c == cls && !Double.isInfinite(cls.zeroSequenceRatio);
    }

    /**
     * max(|I_a|, |I_b|, |I_c|): I_a = I₁ + I₂ + I₀, I_b = a²I₁ + aI₂ + I₀, I_c = aI₁ + a²I₂ + I₀,
     * a = e^{j120°}.
     */
    static double phaseMax(double r1, double i1, double r2, double i2, double r0, double i0) {
        double ar = r1 + r2 + r0, ai = i1 + i2 + i0;
        // a·z = (−½x − √3/2·y) + j(√3/2·x − ½y); a²·z = (−½x + √3/2·y) + j(−√3/2·x − ½y)
        double br = (-0.5 * r1 + SQRT3_2 * i1) + (-0.5 * r2 - SQRT3_2 * i2) + r0;
        double bi = (-SQRT3_2 * r1 - 0.5 * i1) + (SQRT3_2 * r2 - 0.5 * i2) + i0;
        double cr = (-0.5 * r1 - SQRT3_2 * i1) + (-0.5 * r2 + SQRT3_2 * i2) + r0;
        double ci = (SQRT3_2 * r1 - 0.5 * i1) + (-SQRT3_2 * r2 - 0.5 * i2) + i0;
        return Math.sqrt(Math.max(ar * ar + ai * ai, Math.max(br * br + bi * bi, cr * cr + ci * ci)));
    }
}
