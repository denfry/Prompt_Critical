package dev.denfry.promptcritical.sim.refining;

/**
 * Замедленное коксование гудрона / мазута (GDD 09 §9.9.8; Gary, Handwerk, «Petroleum Refining», гл. 5, табл. 5.3).
 *
 * <p><b>Выходы по коксуемости по Конрадсону</b> (CCR, % мас. сырья) — корреляции Gary &amp; Handwerk для
 * замедленного коксования: кокс = 1,6·CCR, газ C₄− = 7,8 + 0,144·CCR, бензин C₅–200 °C = 11,29 + 0,343·CCR,
 * остальное — газойль коксования. Гудрон с CCR 20 % даёт 32 % кокса, 10,7 % газа, 18,1 % бензина и 39,2 % газойля.
 *
 * <p><b>Печь</b>: температура на выходе змеевика {@link #COIL_MIN_C}–{@link #COIL_MAX_C} °C. Холоднее — реакции не
 * успевают закончиться в камере, кокс мягкий и летучий (выход кокса растёт, жидких падает, {@link #SOFT_COKE_PER_K});
 * горячее — коксуются трубы печи, пробег змеевика сокращается вдвое на каждые {@link #TUBE_COKING_DOUBLING_K} K.
 *
 * <p><b>Цикл камеры</b> (две камеры попеременно): заполнение {@link #FILL_H} ч, пропарка 2 ч, охлаждение водой 4 ч,
 * слив и гидрорезка 6 ч — всего 28 ч на камеру при 16 ч на заполнение (типично 16–24 ч заполнения, Gary гл. 5).
 *
 * <p><b>Сера</b>: [BALANCE] {@link #SULFUR_TO_GAS} серы сырья уходит в газ как H₂S (на аминовую очистку и
 * {@link ClausUnit}), остальная делится между коксом и жидкими продуктами.
 */
public final class DelayedCoking {
    public static final double COIL_MIN_C = 495, COIL_MAX_C = 505;
    /** Доля мягкого кокса: +0,6 % мас. кокса на каждый кельвин ниже нижней границы [BALANCE]. */
    public static final double SOFT_COKE_PER_K = 0.006;
    public static final double TUBE_COKING_DOUBLING_K = 10;
    public static final double FILL_H = 16, STEAM_H = 2, QUENCH_H = 4, DECOKE_H = 6;
    public static final double SULFUR_TO_GAS = 0.30, M_H2S = 34.08, M_S = 32.06;
    /** Насыпная плотность сырого («зелёного») кокса, кг/м³. */
    public static final double COKE_BULK_DENSITY = 850;

    public enum Phase { FILL, STEAM, QUENCH, DECOKE }

    /** Выходы, доли массы сырья (сумма 1), и H₂S в газе, кг на кг сырья. */
    public record Yields(double coke, double gas, double naphtha, double gasOil, double h2sKgPerKg, boolean softCoke) {
    }

    private DelayedCoking() {
    }

    /**
     * @param ccr      коксуемость по Конрадсону, доля массы (0,20 = 20 %)
     * @param sulfur   сера сырья, доля массы
     * @param coilOutC температура на выходе печи, °C
     */
    public static Yields yields(double ccr, double sulfur, double coilOutC) {
        double c = Math.max(0, Math.min(0.40, ccr)) * 100;
        double coke = 1.6 * c / 100, gas = (7.8 + 0.144 * c) / 100, naphtha = (11.29 + 0.343 * c) / 100;
        double cold = Math.max(0, COIL_MIN_C - coilOutC);
        double extra = Math.min(cold * SOFT_COKE_PER_K, 1 - coke - gas - naphtha);
        coke += extra;
        double gasOil = Math.max(0, 1 - coke - gas - naphtha);
        double h2s = Math.max(0, sulfur) * SULFUR_TO_GAS * M_H2S / M_S;
        return new Yields(coke, gas, naphtha, gasOil, h2s, cold > 0);
    }

    /** Множитель скорости закоксовывания труб печи: 1 в норме, вдвое на каждые 10 K выше верхней границы. */
    public static double tubeCokingFactor(double coilOutC) {
        return Math.pow(2, Math.max(0, coilOutC - COIL_MAX_C) / TUBE_COKING_DOUBLING_K);
    }

    public static double cycleHours() {
        return FILL_H + STEAM_H + QUENCH_H + DECOKE_H;
    }

    /** Фаза камеры через {@code hours} от начала заполнения. */
    public static Phase phaseAt(double hours) {
        double t = ((hours % cycleHours()) + cycleHours()) % cycleHours();
        if (t < FILL_H) return Phase.FILL;
        if (t < FILL_H + STEAM_H) return Phase.STEAM;
        if (t < FILL_H + STEAM_H + QUENCH_H) return Phase.QUENCH;
        return Phase.DECOKE;
    }

    /** Объём кокса, м³, набранный в камеру за заполнение при расходе сырья feedKgS. */
    public static double cokeVolumePerFill(double feedKgS, Yields y) {
        return feedKgS * FILL_H * 3600 * y.coke() / COKE_BULK_DENSITY;
    }
}
