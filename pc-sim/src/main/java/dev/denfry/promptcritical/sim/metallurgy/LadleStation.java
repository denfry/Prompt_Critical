package dev.denfry.promptcritical.sim.metallurgy;

import java.util.EnumMap;
import java.util.Map;

/**
 * Внепечная обработка {@code ladle_station} (08 §8.6.2): раскисление и легирование добавками, продувка аргоном.
 * Раскисление: усвоенный Al связывает O (2Al + 3O → Al₂O₃) до {@link #O_EQ_AL}, Si (Si + 2O → SiO₂) — до
 * {@link #O_EQ_SI}; остаток раскислителя растворяется. Вакуумирование (B) не моделируется.
 */
public final class LadleStation {
    /** Равновесный O после Al и после Si [аппрокс.]. */
    public static final double O_EQ_AL = 5e-6, O_EQ_SI = 4e-5;
    /** Нагрев и растворение холодной добавки, Дж/кг [BALANCE]. */
    public static final double ALLOY_CHILL_J_PER_KG = 1.4e6;
    /** Остывание ковша при продувке аргоном, К/с физ. (1,5 К/мин) [моё]. */
    public static final double ARGON_COOLING_K_S = 1.5 / 60;
    static final double M_O = 15.999, M_AL = 26.982, M_SI = 28.086;

    private LadleStation() {}

    /** Сталь после присадки {@code kg} добавки. */
    public static SteelMelt addAlloy(SteelMelt melt, Ferroalloy alloy, double kg) {
        if (!(kg >= 0)) throw new IllegalArgumentException("kg: " + kg);
        double m = melt.massKg();
        Map<AlloyElement, Double> mass = new EnumMap<>(AlloyElement.class);
        for (AlloyElement e : AlloyElement.values()) mass.put(e, melt.frac(e) * m);
        double elem = kg * alloy.content * alloy.recovery;
        double fe = kg * Math.max(0, 1 - alloy.content - alloy.carbon);
        double mNew = m + elem + kg * alloy.carbon + fe;
        mass.merge(AlloyElement.C, kg * alloy.carbon, Double::sum);
        double o = mass.get(AlloyElement.O);
        if (alloy.element == AlloyElement.AL || alloy.element == AlloyElement.SI) {
            boolean al = alloy.element == AlloyElement.AL;
            double oPerKg = al ? 3 * M_O / (2 * M_AL) : 2 * M_O / M_SI;
            double removable = Math.max(0, o - (al ? O_EQ_AL : O_EQ_SI) * mNew);
            double oRemoved = Math.min(removable, elem * oPerKg);
            double consumed = oRemoved / oPerKg;
            elem -= consumed;
            mass.put(AlloyElement.O, o - oRemoved);
            mNew -= consumed;
        }
        mass.merge(alloy.element, elem, Double::sum);
        Map<AlloyElement, Double> chem = new EnumMap<>(AlloyElement.class);
        for (Map.Entry<AlloyElement, Double> e : mass.entrySet()) chem.put(e.getKey(), e.getValue() / mNew);
        double t = melt.tempK() - kg * ALLOY_CHILL_J_PER_KG / (mNew * Converter.STEEL_CP);
        return new SteelMelt(mNew, t, chem);
    }

    /** Продувка аргоном через пробку {@code seconds} с физ.: усредняет ванну, ковш остывает. */
    public static SteelMelt argonStir(SteelMelt melt, double seconds) {
        if (!(seconds >= 0)) throw new IllegalArgumentException("seconds");
        return melt.withTemp(melt.tempK() - ARGON_COOLING_K_S * seconds);
    }
}
