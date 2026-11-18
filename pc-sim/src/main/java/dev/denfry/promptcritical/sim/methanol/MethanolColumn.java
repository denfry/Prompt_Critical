package dev.denfry.promptcritical.sim.methanol;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.nitrogen.Species;
import dev.denfry.promptcritical.sim.refining.Fractionation;

/**
 * Ректификация сырого метанола в две колонны: колонна предварительной ректификации (отпарка растворённых газов и
 * лёгких — диметилового эфира, метилформиата; здесь — постоянная потеря {@link #TOPPING_LOSS} метанола) и колонна
 * основной ректификации метанол/вода. Основная колонна — короткий метод
 * {@link Fractionation#effectiveStagesForAlpha} (Фенске–Андервуд–Гиллиленд): {@value #STAGES} теоретических тарелок,
 * флегмовое число R; относительная летучесть метанола к воде при 101,3 кПа — среднее геометрическое верха и низа
 * колонны: α ≈ 2,45 у чистого метанола (x = 0,95, y = 0,979) и ≈ 7,6 в разбавленном (x = 0,02, y = 0,134) по данным
 * равновесия пар–жидкость метанол–вода при 101,3 кПа (Perry's Chemical Engineers' Handbook [сверить]), √(2,45·7,58) ≈ 4,31.
 * Распределение — Фенске при заданном извлечении метанола в дистиллят {@link #RECOVERY}: отношение воды в дистилляте к
 * воде в кубе (r/(1 − r))/α^N_эфф.
 *
 * <p>Кипятильник: (1 + R)·D·λ, λ метанола 35,2 кДж/моль при нормальной температуре кипения (NIST). Тепла меньше —
 * флегмы меньше, и продукт уходит из марки. Марка AA (ASTM D1152 / O-M-232): метанол ≥ 99,85 %, вода ≤ 0,10 % по массе
 * (этанол и ацетон не моделируются).
 */
public final class MethanolColumn {
    public static final int STAGES = 30;
    public static final double ALPHA = Math.sqrt(2.45 * 7.58), RECOVERY = 0.995, TOPPING_LOSS = 0.003;
    public static final double LATENT_J_MOL = 35.2e3, AA_MAX_WATER = 0.0010;

    private double reflux = 1.5;

    /**
     * Итог: метанол и вода в продукте, моль/с; массовая доля воды в продукте; флегма фактическая; тепло кипятильника
     * (потребное при фактической флегме), Вт; вода в кубе, кг/с; продукт в марке AA.
     */
    public record Result(double methanolMolS, double waterMolS, double waterWt, double refluxActual, double reboilerW,
            double bottomsKgS, boolean gradeAa) {
        public static final Result IDLE = new Result(0, 0, 0, 0, 0, 0, false);

        public double productKgS() {
            return methanolMolS * Species.CH3OH.molarMass() + waterMolS * Species.H2O.molarMass();
        }
    }

    public double reflux() {
        return reflux;
    }

    public void setReflux(double r) {
        reflux = Math.max(0.3, Math.min(4, r));
    }

    /** Колонны на сыром метаноле {@code meohMolS} + {@code waterMolS} при тепле кипятильника до {@code heatW}. */
    public Result run(double meohMolS, double waterMolS, double heatW) {
        double m = meohMolS * (1 - TOPPING_LOSS);
        if (m <= 0) return Result.IDLE;
        double dM = m * RECOVERY;
        double need = (1 + reflux) * dM * LATENT_J_MOL;
        double r = need <= heatW ? reflux : Math.max(0, heatW / (dM * LATENT_J_MOL) - 1);
        double nEff = r > 0 ? Fractionation.effectiveStagesForAlpha(STAGES, r, ALPHA) : 0;
        double q = RECOVERY / (1 - RECOVERY) / Math.pow(ALPHA, nEff);
        double dW = waterMolS * q / (1 + q);
        double mass = dM * Species.CH3OH.molarMass() + dW * Species.H2O.molarMass();
        double waterWt = dW * Species.H2O.molarMass() / mass;
        double bottoms = (m - dM) * Species.CH3OH.molarMass() + (waterMolS - dW) * Species.H2O.molarMass();
        return new Result(dM, dW, waterWt, r, (1 + r) * dM * LATENT_J_MOL, bottoms, waterWt <= AA_MAX_WATER);
    }

    public void save(StateWriter out) {
        out.putDouble("reflux", reflux);
    }

    public void load(StateReader in) {
        setReflux(in.optDouble("reflux", 1.5));
    }
}
