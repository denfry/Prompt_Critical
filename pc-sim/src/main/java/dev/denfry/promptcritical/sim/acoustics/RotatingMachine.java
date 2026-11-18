package dev.denfry.promptcritical.sim.acoustics;

import dev.denfry.promptcritical.sim.acoustics.VibrationSpectrum.Band;
import dev.denfry.promptcritical.sim.acoustics.VibrationSpectrum.Line;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Вращающаяся машина для вибродиагностики (04a §4a.6.2): дисбаланс, расцентровка, лопатки, зубчатая передача и подшипник
 * качения. Амплитуды — СКЗ виброскорости:
 *
 * <ul>
 *   <li>1x: v = v⁰·(u/u₀)·s³·R(r_c)/R(r_c0) — скорость ωX отклика жёсткого ротора на силу дисбаланса m·e·ω²
 *       (Den Hartog J. P., «Mechanical Vibrations», 4th ed., §3.2), R(r) = 1/√((1−r²)² + (2ζr)²), r_c = ω/ω_c,
 *       ω_c = 1,5·ω_n, ζ = 0,05; s = ω/ω_n;</li>
 *   <li>2x: v = v⁰·(δ/δ₀)·s — расцентровка δ, δ₀ = 0,05 мм (типовой допуск центровки муфты при 1000–1500 об/мин);</li>
 *   <li>лопаточная: v = v⁰·(1 + 3|q − 1|)·s² — рост вне точки наилучшего КПД;</li>
 *   <li>дефектные частоты подшипника: v = v_brg⁰·g(D_b)·s, g — по стадиям {@link RollingBearing};</li>
 *   <li>фон: v_bg⁰·s·(1 + 5·h₄); огибающая ВЧ: a⁰·(1 + 20·h₁).</li>
 * </ul>
 *
 * Умолчания [BALANCE] 04a §4a.6.2 подобраны для крупного насоса класса III: v⁰_1x = 1,0, v⁰_2x = 0,4, v⁰_BPF = 0,6,
 * v_bg = 0,3 мм/с ⇒ 1,27 мм/с, зона A. Для другого класса все амплитуды масштабируются отношением границ A/B, чтобы
 * исправная машина любого класса стояла на той же доле своей зоны A.
 */
public final class RotatingMachine {
    public static final double V1X0 = 1.0e-3, V2X0 = 0.4e-3, VBPF0 = 0.6e-3, VBG0 = 0.3e-3, VGMF0 = 0.5e-3;
    /** Пик дефектной частоты наружного кольца в конце стадии 2, м/с (04a §4a.7.3: «0,5–1 мм/с»). */
    public static final double VBRG0 = 0.75e-3;
    public static final double AHF0 = 0.5;
    public static final double DELTA0_MM = 0.05;
    public static final double CRITICAL_SPEED_RATIO = 1.5, ROTOR_DAMPING = 0.05;
    /** Остаточный дисбаланс после балансировки, доля прежнего (04a §4a.7.5). */
    public static final double BALANCING_RESIDUAL = 0.2;
    public static final double AMBIENT_K = 293.15;

    private final MachineClass machineClass;
    private final BearingGeometry geometry;
    private final double nominalRpm;
    private final int blades, gearTeeth;
    private double imbalance, misalignmentMm;
    private final RollingBearing bearing;

    /**
     * @param imbalance      u/u₀ (u₀ — класс балансировки G2.5 ISO 21940-11)
     * @param misalignmentMm δ, мм
     * @param blades         число лопаток (0 — нет)
     * @param gearTeeth      число зубьев шестерни на валу (0 — нет)
     */
    public RotatingMachine(MachineClass machineClass, BearingGeometry geometry, double nominalRpm, int blades, int gearTeeth,
            double imbalance, double misalignmentMm, RollingBearing bearing) {
        if (machineClass == null || geometry == null || bearing == null || !(nominalRpm > 0) || blades < 0 || gearTeeth < 0
                || !(imbalance >= 0) || !(misalignmentMm >= 0))
            throw new IllegalArgumentException("machine");
        this.machineClass = machineClass;
        this.geometry = geometry;
        this.nominalRpm = nominalRpm;
        this.blades = blades;
        this.gearTeeth = gearTeeth;
        this.imbalance = imbalance;
        this.misalignmentMm = misalignmentMm;
        this.bearing = bearing;
    }

    /** Новая машина: монтажный разброс дисбаланса 0,6–1,4·u₀ и центровки 0,03–0,08 мм [BALANCE], свой u_b. */
    public static RotatingMachine installed(MachineClass c, BearingGeometry g, double nominalRpm, int blades, int gearTeeth,
            SplittableRandom rnd) {
        return new RotatingMachine(c, g, nominalRpm, blades, gearTeeth, 0.6 + 0.8 * rnd.nextDouble(),
                0.03 + 0.05 * rnd.nextDouble(), RollingBearing.install(rnd));
    }

    private static double response(double r) {
        double a = 1 - r * r, b = 2 * ROTOR_DAMPING * r;
        return 1 / Math.sqrt(a * a + b * b);
    }

    /** Масштаб амплитуд класса относительно III. */
    public double classScale() {
        return machineClass.ab / MachineClass.III.ab;
    }

    /** Эффективный дисбаланс: на стадии 4 разбитый подшипник даёт рост 1x (04a §4a.7.3). */
    private double effectiveImbalance() {
        return imbalance * (1 + 2 * bearing.h(4));
    }

    /** СКЗ 1x, м/с. */
    public double v1x(double rpm) {
        double s = Math.abs(rpm) / nominalRpm;
        double rc = s / CRITICAL_SPEED_RATIO, rc0 = 1 / CRITICAL_SPEED_RATIO;
        return V1X0 * classScale() * effectiveImbalance() * s * s * s * response(rc) / response(rc0);
    }

    /**
     * Спектр на скорости вала.
     *
     * @param rpm          скорость вала, об/мин (знак не важен)
     * @param relativeFlow q = Q/Q_bep для лопаточных машин (1 — номинал)
     */
    public VibrationSpectrum spectrum(double rpm, double relativeFlow) {
        double n = Math.abs(rpm);
        if (!(n > 0)) return new VibrationSpectrum(0, List.of(), 0, 0, AMBIENT_K, machineClass);
        double s = n / nominalRpm, fr = n / 60, k = classScale();
        List<Line> out = new ArrayList<>(8);
        out.add(new Line(Band.X1, fr, v1x(n)));
        out.add(new Line(Band.X2, 2 * fr, V2X0 * k * misalignmentMm / DELTA0_MM * s));
        if (blades > 0) out.add(new Line(Band.BPF, blades * fr, VBPF0 * k * (1 + 3 * Math.abs(relativeFlow - 1)) * s * s));
        double g = 0.75 * bearing.h(2) + 1.5 * bearing.h(3) + 8 * bearing.h(4);
        double vb = VBRG0 / 0.75 * k * g * s;
        out.add(new Line(Band.BPFO, geometry.bpfo(fr), vb));
        out.add(new Line(Band.BPFI, geometry.bpfi(fr), 0.8 * vb));
        out.add(new Line(Band.BSF, geometry.bsf(fr), 0.5 * vb));
        out.add(new Line(Band.FTF, geometry.ftf(fr), 0.3 * vb));
        if (gearTeeth > 0) out.add(new Line(Band.GMF, gearTeeth * fr, VGMF0 * k * s));
        double bg = VBG0 * k * s * (1 + 5 * bearing.h(4));
        double hf = AHF0 * (1 + 20 * bearing.h(1)) * s;
        return new VibrationSpectrum(fr, out, bg, hf, bearing.housingTempK(AMBIENT_K, s), machineClass);
    }

    /**
     * Нагрузка на подшипник P/P₀ = 1 + 0,5·(v_1x/v⁰_1x − 1)⁺ (04a §4a.7.1): дисбаланс нагружает опоры.
     */
    public double loadRatio(double rpm) {
        double r = v1x(rpm) / (V1X0 * classScale());
        return 1 + 0.5 * Math.max(0, r - 1);
    }

    /**
     * Шаг износа подшипника.
     *
     * @return true, если подшипник заклинило
     */
    public boolean stepWear(double dtPhysS, double rpm, double wearMultiplier) {
        double s = Math.abs(rpm) / nominalRpm;
        return bearing.step(dtPhysS, s, loadRatio(rpm), wearMultiplier, 1);
    }

    /** Замена подшипника (новый u_b). */
    public void replaceBearing(SplittableRandom rnd) {
        bearing.replace(dev.denfry.promptcritical.sim.ops.Weibull.bearingLifeFactor(rnd));
    }

    /** Балансировка: u → 0,2·u. */
    public void balance() {
        imbalance *= BALANCING_RESIDUAL;
    }

    /** Центровка: δ → δ₀. */
    public void align() {
        misalignmentMm = DELTA0_MM;
    }

    public MachineClass machineClass() {
        return machineClass;
    }

    public BearingGeometry geometry() {
        return geometry;
    }

    public double nominalRpm() {
        return nominalRpm;
    }

    public int blades() {
        return blades;
    }

    public int gearTeeth() {
        return gearTeeth;
    }

    public double imbalance() {
        return imbalance;
    }

    public double misalignmentMm() {
        return misalignmentMm;
    }

    public RollingBearing bearing() {
        return bearing;
    }
}
