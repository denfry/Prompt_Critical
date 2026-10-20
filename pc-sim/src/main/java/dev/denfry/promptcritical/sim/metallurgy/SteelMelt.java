package dev.denfry.promptcritical.sim.metallurgy;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Жидкий металл в ковше или конвертере (08 §8.6): масса, температура и химия долями массы. Им же описывается
 * чугун на завалку ({@link #hotMetal}) — в GDD {@code Converter.charge(hmKg, Composition hm, …)}, а отдельного типа
 * {@code Composition} нет.
 *
 * @param chem доли массы; отсутствующий элемент — 0. Кислород — растворённый: нераскисленная сталь
 *             ({@link #killed()} = false) даёт пористый слиток
 */
public record SteelMelt(double massKg, double tempK, Map<AlloyElement, Double> chem) {
    /** Раскисленная сталь: O ≤ 50 ppm (Si-раскисление даёт ≈ 40, Al — ≈ 5) [моё]. */
    public static final double KILLED_O = 5e-5;
    /** P чугуна: фосфор руды домна не считает — типичный передельный 0,1 % [допущение]. */
    public static final double HOT_METAL_P = 0.001;
    /** Ликвидус чистого железа и эвтектика Fe–C, К. */
    public static final double FE_LIQUIDUS_K = 1811, EUTECTIC_K = 1420;

    public SteelMelt {
        if (!(massKg >= 0) || !(tempK > 0)) throw new IllegalArgumentException("mass/temperature");
        Objects.requireNonNull(chem, "chem");
        EnumMap<AlloyElement, Double> m = new EnumMap<>(AlloyElement.class);
        for (Map.Entry<AlloyElement, Double> e : chem.entrySet()) {
            double v = e.getValue();
            if (!(v >= 0 && v <= 1)) throw new IllegalArgumentException(e.getKey() + ": " + v);
            if (v > 0) m.put(e.getKey(), v);
        }
        chem = Collections.unmodifiableMap(m);
    }

    /** Чугун из выпуска домны: C, Si, S — из {@link TapResult}; Mn — {@link BlastFurnace#HM_MN}, P — {@link #HOT_METAL_P}. */
    public static SteelMelt hotMetal(TapResult tap, double kg) {
        Map<AlloyElement, Double> c = new EnumMap<>(AlloyElement.class);
        c.put(AlloyElement.C, tap.carbonFrac());
        c.put(AlloyElement.SI, tap.siliconFrac());
        c.put(AlloyElement.S, tap.sulfurFrac());
        c.put(AlloyElement.MN, BlastFurnace.HM_MN);
        c.put(AlloyElement.P, HOT_METAL_P);
        return new SteelMelt(kg, tap.ironTempK(), c);
    }

    public double frac(AlloyElement e) {
        Double v = chem.get(e);
        return v == null ? 0 : v;
    }

    public SteelMelt withTemp(double t) {
        return new SteelMelt(massKg, t, chem);
    }

    /** Та же сталь другой массы (разлили часть). */
    public SteelMelt withMass(double kg) {
        return new SteelMelt(kg, tempK, chem);
    }

    /** Раскисленная сталь; нераскисленная кипит в изложнице — пористый слиток, плены труб (08 §8.6.2). */
    public boolean killed() {
        return frac(AlloyElement.O) <= KILLED_O;
    }

    /** Ликвидус, К: Fe–C 90 К на 1 % C до эвтектики, Si + Mn — 10 К на 1 %, Cr + Ni + Mo + Co — 3 К на 1 % [аппрокс.]. */
    public double liquidusK() {
        double t = FE_LIQUIDUS_K - 9_000 * Math.min(frac(AlloyElement.C), 0.043)
                - 1_000 * (frac(AlloyElement.SI) + frac(AlloyElement.MN))
                - 300 * (frac(AlloyElement.CR) + frac(AlloyElement.NI) + frac(AlloyElement.MO) + frac(AlloyElement.CO));
        return Math.max(EUTECTIC_K, t);
    }

    /** Химия в процентах по символам (для {@link SteelGrade}), без кислорода. */
    public Map<String, Float> chemPct() {
        Map<String, Float> m = new LinkedHashMap<>();
        for (Map.Entry<AlloyElement, Double> e : chem.entrySet())
            if (e.getKey() != AlloyElement.O) m.put(e.getKey().symbol, (float) (e.getValue() * 100));
        return m;
    }
}
