package dev.denfry.promptcritical.sim.metallurgy;

/**
 * Выпуск за шаг (08 §8.5.5).
 *
 * @param ironKg       чугун {@code pig_iron}, кг
 * @param slagKg       шлак, кг
 * @param ironTempK    температура чугуна, К
 * @param carbonFrac   C чугуна
 * @param siliconFrac  Si
 * @param sulfurFrac   S
 * @param slagBasicity CaO/SiO₂ шлака
 * @param blowing      жидкое ниже лётки — идёт газ, лётку пора забивать
 */
public record TapResult(double ironKg, double slagKg, double ironTempK, double carbonFrac, double siliconFrac,
        double sulfurFrac, double slagBasicity, boolean blowing) {
    public static final TapResult NONE = new TapResult(0, 0, 0, 0, 0, 0, 0, false);
}
