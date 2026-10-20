package dev.denfry.promptcritical.sim.metallurgy;

import java.util.random.RandomGenerator;

/**
 * Трубный передел (08 §8.7.3). Цепочка Маннесмана — {@code piercing_mill} → {@code pilger_mill} (горячий) →
 * калибровка {@code sizing} — сведена в одну операцию {@link #seamless}: дефекты появляются на прошивке, калибровка
 * держит наружный диаметр ±0,75 %. Сварные — {@link #welded} ({@code pipe_welder}, ТВЧ).
 *
 * <p>Прошивка: окно 1200–1250 °C; разностенность U(0, 8 %) в окне и U(0, 20 %) вне окна или с трещиноватой
 * заготовкой [BALANCE]; больше нормы API 5CT (−12,5 %) — дефект {@link DefectKind#ECCENTRICITY} на всю длину.
 * Трещины заготовки → {@link DefectKind#LAMINATION}, пористость → {@link DefectKind#INCLUSION_LAP} (плены).
 * Холоднее {@link #PIERCE_MIN_K} оправка не идёт — {@code null}.
 *
 * <p>Шов: p_def = p₀(1 + 4|Δv/v_opt| + 4|ΔP/P_opt|)·k_CE на метр, k_CE = 3 при C_экв &gt; 0,43. Вид: при
 * C_экв &gt; 0,43 — {@link DefectKind#HOOK_CRACK}, при перегреве (P &gt; P_opt и v &lt; v_opt) — поры, иначе —
 * непровар; размеры d/t U(0,2; 0,8), L U(0,02; 0,2) м [моё]. Пористый лист даёт расслоение основного металла.
 */
public final class PipeMill {
    public static final double PIERCE_MIN_K = 1423.15, PIERCE_LOW_K = 1473.15, PIERCE_HIGH_K = 1523.15;
    public static final double ECC_NORMAL = 0.08, ECC_BAD = 0.20, ECC_LIMIT = 0.125, SIZING_TOL = 0.0075;
    public static final double CE_LIMIT = 0.43, CE_FACTOR = 3, WELD_SPEED_GAIN = 4, WELD_POWER_GAIN = 4;
    /** Плены и расслоения: d/t и длина [BALANCE]. */
    public static final double LAP_DEPTH_MIN = 0.1, LAP_DEPTH_MAX = 0.35, LAP_LEN_MIN = 0.05, LAP_LEN_MAX = 0.3;
    /** Длина дефекта «на всю трубу», м (1 предмет — 20 м). */
    public static final double FULL_LENGTH_M = 20;

    private PipeMill() {}

    /** Бесшовная труба из нагретой заготовки; {@code null}, если заготовка слишком холодная для прошивки. */
    public static PipeSpec seamless(StockState billet, PipeKind kind, double odM, double wallM, long heatNo,
            SteelmakingConfig cfg, RandomGenerator rng) {
        double t = billet.tempK();
        if (t < PIERCE_MIN_K) return null;
        boolean window = t >= PIERCE_LOW_K && t <= PIERCE_HIGH_K && !billet.cracked();
        double ecc = rng.nextDouble() * (window ? ECC_NORMAL : ECC_BAD);
        PipeDefect d = PipeDefect.NONE;
        if (billet.cracked()) d = lap(DefectKind.LAMINATION, rng);
        else if (billet.porous()) d = lap(DefectKind.INCLUSION_LAP, rng);
        if (ecc > ECC_LIMIT && worse(new PipeDefect(DefectKind.ECCENTRICITY, ecc, FULL_LENGTH_M), d, odM, wallM))
            d = new PipeDefect(DefectKind.ECCENTRICITY, ecc, FULL_LENGTH_M);
        double od = odM * (1 + (2 * rng.nextDouble() - 1) * SIZING_TOL);
        return PipeSpec.of(billet.grade(), kind, od, wallM, true, d, heatNo, cfg);
    }

    /** Вероятность скрытого дефекта шва на метр. */
    public static double weldDefectPerM(double speed, double speedOpt, double power, double powerOpt, double ce,
            double base) {
        double p = base * (1 + WELD_SPEED_GAIN * Math.abs((speed - speedOpt) / speedOpt)
                + WELD_POWER_GAIN * Math.abs((power - powerOpt) / powerOpt)) * (ce > CE_LIMIT ? CE_FACTOR : 1);
        return Math.min(1, p);
    }

    /**
     * Сварная прямошовная труба из листа: стенка — толщина листа; шов длиной {@code lengthM} проверяется
     * разыгрышем 1 − (1 − p)^L.
     */
    public static PipeSpec welded(StockState plate, PipeKind kind, double odM, double lengthM, double speed,
            double speedOpt, double powerW, double powerOptW, long heatNo, SteelmakingConfig cfg,
            RandomGenerator rng) {
        SteelGrade g = plate.grade();
        double ce = g.carbonEq();
        double p = weldDefectPerM(speed, speedOpt, powerW, powerOptW, ce, cfg.weldDefectBasePerM());
        PipeDefect d = PipeDefect.NONE;
        if (rng.nextDouble() < 1 - Math.pow(1 - p, lengthM)) {
            DefectKind k = ce > CE_LIMIT ? DefectKind.HOOK_CRACK
                    : powerW > powerOptW && speed < speedOpt ? DefectKind.POROSITY : DefectKind.LACK_OF_FUSION;
            d = new PipeDefect(k, 0.2 + 0.6 * rng.nextDouble(), 0.02 + 0.18 * rng.nextDouble());
        }
        double wall = plate.thicknessM();
        if (plate.porous() || plate.cracked()) {
            PipeDefect lam = lap(DefectKind.LAMINATION, rng);
            if (worse(lam, d, odM, wall)) d = lam;
        }
        return PipeSpec.of(g, kind, odM, wall, false, d, heatNo, cfg);
    }

    private static PipeDefect lap(DefectKind kind, RandomGenerator rng) {
        return new PipeDefect(kind, LAP_DEPTH_MIN + (LAP_DEPTH_MAX - LAP_DEPTH_MIN) * rng.nextDouble(),
                LAP_LEN_MIN + (LAP_LEN_MAX - LAP_LEN_MIN) * rng.nextDouble());
    }

    /** {@code a} опаснее {@code b} по B31G. */
    private static boolean worse(PipeDefect a, PipeDefect b, double od, double wall) {
        return PipeSpec.b31gPa(1, od, wall, a.depthFrac(), a.lengthM())
                < PipeSpec.b31gPa(1, od, wall, b.depthFrac(), b.lengthM());
    }
}
