package dev.denfry.promptcritical.sim.utilities;

/**
 * Конструкция ВРУ (08 §8.11).
 *
 * @param ratedCompressorW  мощность основного компрессора, Вт
 * @param loxHoldupKg       жидкий кислород в кубе конденсатора-испарителя, кг (в нём копятся углеводороды)
 * @param argonColumn       есть аргонная колонна
 */
public record AsuSpec(double ratedCompressorW, double loxHoldupKg, boolean argonColumn) {
    public AsuSpec {
        if (!(ratedCompressorW > 0 && loxHoldupKg > 0)) throw new IllegalArgumentException("compressor and holdup must be > 0");
    }

    /** Блок под компрессор 2 МВт (≈ 4760 нм³/ч O₂, конвертер при 1,6 т/мин стали), 2 м³ LOX в кубе, с аргоном. */
    public static AsuSpec defaults() {
        return new AsuSpec(2e6, 2_300, true);
    }
}
