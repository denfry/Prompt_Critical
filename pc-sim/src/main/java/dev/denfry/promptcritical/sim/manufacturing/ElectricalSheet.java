package dev.denfry.promptcritical.sim.manufacturing;

import java.util.Objects;

/**
 * Лист электротехнической стали (18 §18.6.8): толщина, м, доля кремния, холодная деформация ε = ln(h₀/h) с последнего
 * отжига и межлистовая изоляция. Удельные потери в стали — по Штейнмецу с вихревой и добавочной составляющими:
 * <pre>
 *   p = k_h·f·B^α + π²·d²·(f·B)²/(6·ρ·γ) + k_exc·(f·B)^1,5,  Вт/кг
 * </pre>
 * Коэффициенты подобраны так, что отожжённый лист 0,5 мм с 3 % Si даёт ≈ 4 Вт/кг при 1,5 Тл и 50 Гц (марки 2212–2312,
 * М470-50А по EN 10106 — до 4,7 Вт/кг), а нелегированное железо той же толщины — вдвое больше [сверить].
 */
public record ElectricalSheet(double thicknessM, double siFrac, double strain, SheetCoating coating) {
    /** Показатель Штейнмеца α [сверить: 1,6 у Штейнмеца, 1,8–2,0 для 1–1,7 Тл]. */
    public static final double STEINMETZ_ALPHA = 1.8;
    /** k_h железа без кремния, Вт·с/кг/Тл^α, и его падение на долю кремния [сверить по 2212 и 08кп]. */
    public static final double KH_IRON = 0.059, KH_PER_SI = 1.1;
    /** Добавочные потери k_exc, Вт/кг/(Тл·Гц)^1,5 [сверить: ≈ 0,6 Вт/кг при 1,5 Тл, 50 Гц]. */
    public static final double K_EXCESS = 0.001;
    /** Наклёп после холодной прокатки поднимает гистерезис на эту долю при ε ≥ 1 [сверить: полуфабрикат — в 1,5–2 раза]. */
    public static final double WORKED_HYSTERESIS = 0.8;
    /** Кремния больше этого лист не катают холодным: хрупок (ГОСТ 21427 — до 3,8 % Si) [сверить]. */
    public static final double SI_MAX = 0.045;
    /** Предельная холодная деформация между отжигами: 1,4 у железа, на 25 меньше на долю Si [моё по пластичности Fe-Si]. */
    public static final double COLD_STRAIN_IRON = 1.4, COLD_STRAIN_PER_SI = 25;
    /** Обжатие по толщине за проход холодной прокатки [сверить: 20–40 %]. */
    public static final double MAX_PASS_REDUCTION = 0.30;
    /** Отжиг: не ниже 1073 К (800 °C) и не меньше 120 с игры [сверить температуру; BALANCE время]. */
    public static final double ANNEAL_K = 1073.15, ANNEAL_S = 120;
    /** Запекание лака: 423–523 К, не меньше 60 с; выше {@link #COATING_BURN_K} органика выгорает [сверить]. */
    public static final double BAKE_MIN_K = 423.15, BAKE_MAX_K = 523.15, BAKE_S = 60, COATING_BURN_K = 573.15;
    /** Стандартный лист: 1,0 × 0,5 м [моё по ГОСТ 21427 — листы 0,5–1,0 × 1,0–2,0 м]. */
    public static final double SHEET_LENGTH_M = 1.0, SHEET_WIDTH_M = 0.5;

    public enum Outcome { OK, NEEDS_ANNEAL, DONE, TOO_BRITTLE }

    /** Итог прохода прокатки: лист, исход и работа деформации, Дж. */
    public record Pass(ElectricalSheet sheet, Outcome outcome, double workJ) {}

    public ElectricalSheet {
        Objects.requireNonNull(coating, "coating");
        if (!(thicknessM > 0) || !Double.isFinite(thicknessM) || !(siFrac >= 0) || !(siFrac <= 0.1)
                || !(strain >= 0) || !Double.isFinite(strain)) {
            throw new IllegalArgumentException("thickness must be positive, Si in 0..0.1, strain non-negative");
        }
    }

    /** Горячекатаный лист под холодную прокатку: без наклёпа и покрытия. */
    public static ElectricalSheet hotBand(double thicknessM, double siFrac) {
        return new ElectricalSheet(thicknessM, siFrac, 0, SheetCoating.NONE);
    }

    /** Удельное электросопротивление, Ом·м: (0,10 + 12·Si)·10⁻⁶ [сверить: 3 % Si — 0,46–0,48 мкОм·м]. */
    public static double resistivityOhmM(double si) {
        return (0.10 + 12 * si) * 1e-6;
    }

    /** Плотность, кг/м³: кремний облегчает железо [сверить: 3 % Si — 7650]. */
    public static double densityKgM3(double si) {
        return 7870 - 6500 * si;
    }

    /** σ_в листа, Па: 300 МПа у железа, +5,7 ГПа на долю Si, наклёп до +30 % [сверить]. */
    public double utsPa() {
        return (300e6 + 5.7e9 * siFrac) * (1 + 0.3 * Math.min(1, strain));
    }

    /** Множитель гистерезиса от наклёпа. */
    public double hysteresisFactor() {
        return 1 + WORKED_HYSTERESIS * Math.min(1, strain);
    }

    /** Предельная холодная деформация между отжигами. */
    public double maxColdStrain() {
        return Math.max(0, COLD_STRAIN_IRON - COLD_STRAIN_PER_SI * siFrac);
    }

    /** Удельные потери листа, Вт/кг, при амплитуде {@code bT} и частоте {@code fHz} с множителями гистерезиса и вихрей. */
    public static double lossWPerKg(double si, double thicknessM, double hysteresisFactor, double eddyFactor, double bT,
            double fHz) {
        if (!(bT >= 0) || !(fHz >= 0) || !(hysteresisFactor >= 1) || !(eddyFactor >= 1))
            throw new IllegalArgumentException("B, f must be >= 0 and factors >= 1");
        double kh = Math.max(0.01, KH_IRON - KH_PER_SI * si) * hysteresisFactor;
        double fb = fHz * bT;
        double hyst = kh * fHz * Math.pow(bT, STEINMETZ_ALPHA);
        double eddy = eddyFactor * Math.PI * Math.PI * thicknessM * thicknessM * fb * fb
                / (6 * resistivityOhmM(si) * densityKgM3(si));
        return hyst + eddy + K_EXCESS * Math.pow(fb, 1.5);
    }

    /** Удельные потери целого листа (без вырубки и сборки), Вт/кг. */
    public double lossWPerKg(double bT, double fHz) {
        return lossWPerKg(siFrac, thicknessM, hysteresisFactor(), 1, bT, fHz);
    }

    /**
     * Проход холодной прокатки к толщине {@code targetM}: обжатие не больше {@link #MAX_PASS_REDUCTION} и не дальше
     * предела наклёпа. Лист с кремнием выше {@link #SI_MAX} не катается; покрытие при прокатке сдирается.
     */
    public Pass roll(double targetM) {
        if (!(targetM > 0)) throw new IllegalArgumentException("target must be positive");
        if (siFrac > SI_MAX) return new Pass(this, Outcome.TOO_BRITTLE, 0);
        if (thicknessM <= targetM * (1 + 1e-9)) return new Pass(this, Outcome.DONE, 0);
        double h1 = Math.max(targetM, thicknessM * (1 - MAX_PASS_REDUCTION));
        double room = maxColdStrain() - strain;
        h1 = Math.max(h1, thicknessM * Math.exp(-Math.max(0, room)));
        if (h1 >= thicknessM * 0.99) return new Pass(this, Outcome.NEEDS_ANNEAL, 0);
        double de = Math.log(thicknessM / h1);
        // работа на 1 м² исходного листа: σ̄·V·Δε, σ̄ — средний предел текучести на участке
        double flow = 0.7 * (utsPa() + new ElectricalSheet(h1, siFrac, strain + de, coating).utsPa()) / 2;
        double work = flow * thicknessM * de;
        return new Pass(new ElectricalSheet(h1, siFrac, strain + de, SheetCoating.NONE), Outcome.OK, work);
    }

    /** Отжиг: наклёп снят, органическое покрытие и бумага выгорают. */
    public ElectricalSheet anneal() {
        return new ElectricalSheet(thicknessM, siFrac, 0, SheetCoating.NONE);
    }

    /** Лакировка или прокладка бумагой. */
    public ElectricalSheet coat(SheetCoating c) {
        return new ElectricalSheet(thicknessM, siFrac, strain, Objects.requireNonNull(c, "coating"));
    }

    /** Масса стандартного листа, кг. */
    public double sheetMassKg() {
        return SHEET_LENGTH_M * SHEET_WIDTH_M * thicknessM * densityKgM3(siFrac);
    }
}
