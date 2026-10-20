package dev.denfry.promptcritical.sim.metallurgy;

import java.util.Objects;

/**
 * Заготовка на рольганге (08 §8.7.2): сечение h×w, длина, температура, марка и унаследованные дефекты.
 *
 * @param cracked трещины: прокатка ниже Ar₃, пережог в печи — идут в трубы как {@link DefectKind#LAMINATION}
 * @param porous  осевая пористость нераскисленного слитка — плены труб
 */
public record StockState(StockShape shape, double thicknessM, double widthM, double lengthM, double tempK,
        SteelGrade grade, boolean cracked, boolean porous) {
    public static final double DENSITY = 7_850;
    /** Угар в нагревательной печи: 1 % при 1150 °C, 2 % при 1250 °C (08 §8.7.2); ниже 900 °C — 0,5 % [моё]. */
    public static final double SCALE_MIN = 0.01, SCALE_MAX = 0.02, SCALE_LOW = 0.005;
    /** Пережог выше 1300 °C — трещины [моё]. */
    public static final double BURNING_K = 1573.15;

    public StockState {
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(grade, "grade");
        if (!(thicknessM > 0 && widthM > 0 && lengthM > 0 && tempK > 0)) throw new IllegalArgumentException("geometry");
    }

    /** Слиток квадратного сечения высотой в три стороны. */
    public static StockState fromIngot(SteelIngot ingot, SteelGrade grade, double tempK) {
        double side = Math.cbrt(ingot.massKg() / DENSITY / 3);
        return new StockState(StockShape.INGOT, side, side, 3 * side, tempK, grade, false, ingot.porous());
    }

    public double massKg() {
        return DENSITY * thicknessM * widthM * lengthM;
    }

    /** После нагревательной печи ({@code reheating_furnace}): температура, угар по длине, пережог. */
    public StockState reheated(double toK) {
        double loss = toK < 1173.15 ? SCALE_LOW
                : SCALE_MIN + (SCALE_MAX - SCALE_MIN) * Math.max(0, Math.min(1, (toK - 1423.15) / 100));
        return new StockState(shape, thicknessM, widthM, lengthM * (1 - loss), toK, grade,
                cracked || toK > BURNING_K, porous);
    }

    public StockState withTemp(double t) {
        return new StockState(shape, thicknessM, widthM, lengthM, t, grade, cracked, porous);
    }
}
