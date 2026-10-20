package dev.denfry.promptcritical.sim.metallurgy;

import java.util.Map;
import java.util.Objects;

/**
 * Слиток из изложницы после обрези головной части.
 *
 * @param massKg годная масса, кг
 * @param cropKg обрезь (усадочная раковина), кг — в лом
 * @param chem   химия долями массы
 * @param porous нераскисленная сталь: осевая пористость — плены труб, расслоения листа
 * @param laps   холодная разливка: поверхностные плены
 */
public record SteelIngot(double massKg, double cropKg, Map<AlloyElement, Double> chem, boolean porous, boolean laps) {
    public SteelIngot {
        if (!(massKg > 0) || !(cropKg >= 0)) throw new IllegalArgumentException("mass");
        chem = Map.copyOf(Objects.requireNonNull(chem, "chem"));
    }
}
