package dev.denfry.promptcritical.sim.metallurgy;

import java.util.Objects;

/**
 * Компонент {@code pipe_spec} (08 §8.7.4; первые поля — 09 §9.15). Прочность: Барлоу p_y = 2σ₀,₂t/D; с дефектом —
 * ASME B31G: p_f = 1,1σ₀,₂·(2t/D)·(1 − ⅔d/t)/(1 − ⅔(d/t)/M), M = √(1 + 0,8L²/(Dt)); при L²/(Dt) &gt; 20 —
 * p_f = 1,1σ₀,₂·(2t/D)·(1 − d/t). Без дефекта p_f = 1,1·p_y.
 *
 * @param testPressurePa давление гидроиспытания на стенде (при изготовлении — {@code hydrotest_fraction}·p_y), Па
 * @param inspectedMask  биты пройденного контроля {@link #HYDRO}, {@link #UT}, {@link #RT}
 * @param defect         скрытый до контроля дефект
 */
public record PipeSpec(String grade, double odM, double wallM, double smysPa, double testPressurePa,
        boolean seamless, boolean sourService, boolean coated, double corrosionAllowanceM, PipeKind kind,
        double utsPa, int inspectedMask, PipeDefect defect, long heatNo) {
    public static final int HYDRO = 1, UT = 2, RT = 4;
    /** Коэффициент формы поверхностной трещины в ΔK = YΔσ√(πa). */
    public static final double CRACK_Y = 1.12;
    /** Граница «длинного» дефекта B31G, L²/(Dt). */
    public static final double B31G_LONG = 20;

    public PipeSpec {
        Objects.requireNonNull(grade, "grade");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(defect, "defect");
        if (!(odM > 0 && wallM > 0 && wallM < odM / 2)) throw new IllegalArgumentException("od/wall");
        if (!(smysPa > 0 && utsPa >= smysPa && testPressurePa >= 0 && corrosionAllowanceM >= 0))
            throw new IllegalArgumentException("strength");
    }

    /** Новая труба из марки: давление испытания — {@code hydrotest_fraction}·p_y, без контроля. */
    public static PipeSpec of(SteelGrade g, PipeKind kind, double odM, double wallM, boolean seamless,
            PipeDefect defect, long heatNo, SteelmakingConfig cfg) {
        double py = barlowPa(g.yieldPa(), odM, wallM);
        return new PipeSpec(g.designation(), odM, wallM, g.yieldPa(), cfg.hydrotestFractionOfSmys() * py, seamless,
                g.sourCapable(), false, 0, kind, g.utsPa(), 0, defect, heatNo);
    }

    /** p_y по Барлоу, Па. */
    public static double barlowPa(double smysPa, double odM, double wallM) {
        return 2 * smysPa * wallM / odM;
    }

    /** p_f по ASME B31G, Па. */
    public static double b31gPa(double smysPa, double odM, double wallM, double depthFrac, double lengthM) {
        double base = 1.1 * barlowPa(smysPa, odM, wallM);
        if (depthFrac <= 0) return base;
        double z = lengthM * lengthM / (odM * wallM);
        if (z > B31G_LONG) return base * Math.max(0, 1 - depthFrac);
        double m = Math.sqrt(1 + 0.8 * z);
        double a = 2.0 / 3 * depthFrac;
        return base * (1 - a) / (1 - a / m);
    }

    public double yieldPressurePa() {
        return barlowPa(smysPa, odM, wallM);
    }

    public double failurePressurePa() {
        return b31gPa(smysPa, odM, wallM, defect.depthFrac(), defect.lengthM());
    }

    /** В работе: p &gt; p_f — разрыв (01, 04c, {@code PIPE_WHIP}). */
    public boolean rupturesAt(double pressurePa) {
        return pressurePa > failurePressurePa();
    }

    /** Гидроиспытание на {@code hydrotest_bench} при {@link #testPressurePa}: бит HYDRO; p_f &lt; p_исп — лопнула. */
    public HydrotestResult hydrotest() {
        boolean burst = failurePressurePa() < testPressurePa;
        return new HydrotestResult(inspected(HYDRO), burst);
    }

    /** Отметка пройденного контроля. */
    public PipeSpec inspected(int method) {
        return new PipeSpec(grade, odM, wallM, smysPa, testPressurePa, seamless, sourService, coated,
                corrosionAllowanceM, kind, utsPa, inspectedMask | method, defect, heatNo);
    }

    /** Дефект виден: найден пройденным контролем (гидроиспытание — если труба на нём лопнула). */
    public boolean defectFound() {
        if (!defect.present()) return false;
        return (inspectedMask & UT) != 0 && defect.kind().ultrasonic
                || (inspectedMask & RT) != 0 && defect.kind().radiography
                || (inspectedMask & HYDRO) != 0 && failurePressurePa() < testPressurePa;
    }

    /**
     * {@code cycles} циклов давления размаха {@code dpPa}: плоский дефект подрастает по Пэрису
     * da/dN = C·(YΔσ√(πa))³, Δσ = Δp·D/(2t) (интеграл в замкнутом виде при m = 3). Сквозной — d/t = 1.
     */
    public PipeSpec pressureCycles(double dpPa, long cycles, double parisC) {
        if (!defect.present() || !defect.kind().planar || cycles <= 0 || !(dpPa > 0)) return this;
        double a0 = defect.depthFrac() * wallM;
        double ds = dpPa * odM / (2 * wallM) / 1e6;
        double k = parisC * Math.pow(CRACK_Y * ds, 3) * Math.pow(Math.PI, 1.5);
        double inv = 1 / Math.sqrt(a0) - 0.5 * k * cycles;
        double depth = inv <= 0 ? 1 : Math.min(1, 1 / (inv * inv) / wallM);
        return withDefect(new PipeDefect(defect.kind(), depth, defect.lengthM()));
    }

    public PipeSpec withDefect(PipeDefect d) {
        return new PipeSpec(grade, odM, wallM, smysPa, testPressurePa, seamless, sourService, coated,
                corrosionAllowanceM, kind, utsPa, inspectedMask, d, heatNo);
    }

    /** После термообработки: прочность и кислая стойкость по новой марке. */
    public PipeSpec withGrade(SteelGrade g) {
        return new PipeSpec(g.designation(), odM, wallM, g.yieldPa(), testPressurePa, seamless, g.sourCapable(),
                coated, corrosionAllowanceM, kind, g.utsPa(), inspectedMask, defect, heatNo);
    }

    /**
     * Итог гидроиспытания.
     *
     * @param pipe  труба с битом HYDRO
     * @param burst лопнула на стенде (вода, безопасно); дефект теперь виден
     */
    public record HydrotestResult(PipeSpec pipe, boolean burst) {}
}
