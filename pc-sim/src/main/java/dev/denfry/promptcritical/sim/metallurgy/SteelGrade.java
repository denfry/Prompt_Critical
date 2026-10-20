package dev.denfry.promptcritical.sim.metallurgy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Компонент {@code steel_grade} (08 §8.6.4, §8.18). Химия квантуется: легирующие — шагом 0,1 %, не больше 4
 * (крупнейших); C — 0,01 %, S и P — 0,001 % (у GDD шаг 0,1 % для всего — C и S так не различить) [моё].
 * Недобор легирования и лишняя сера линейно снижают прочность ({@link #STRENGTH_PER_SHORTFALL},
 * {@link #STRENGTH_PER_SULFUR}), но не ниже половины.
 *
 * <p>Твёрдость: после закалки ≈ 20 + 60√C HRC (08 §8.7.5), иначе по σ_в: HRC ≈ 2,8 + 0,0246·σ_в[МПа]
 * (780 МПа — 22 HRC, 2000 МПа — 52 HRC) [аппрокс.]. C_экв по МИС: C + Mn/6 + (Cr+Mo+V)/5 + (Ni+Cu)/15, %.
 *
 * @param chemPct химия, % по символам {@link AlloyElement#symbol}
 */
public record SteelGrade(SteelFamily family, String designation, Map<String, Float> chemPct, HeatTreatState state,
        double yieldPa, double utsPa, double hardnessHrc, double carbonEq) {
    public static final double STRENGTH_PER_SHORTFALL = 0.5, STRENGTH_PER_SULFUR = 0.1, MIN_STRENGTH_FACTOR = 0.5;
    /** Микролегирование Nb + V низколегированной: +140/+80 МПа при 0,1 % (X70 против 09Г2С) [аппрокс.]. */
    public static final double MICROALLOY_FULL_PCT = 0.1, MICROALLOY_YIELD_PA = 140e6, MICROALLOY_UTS_PA = 80e6;
    /** Предел твёрдости для H₂S (NACE MR0175), HRC. */
    public static final double SOUR_MAX_HRC = 22;
    public static final int MAX_ALLOYING = 4;
    /** Порядок проверки семейств: от самого требовательного. */
    private static final SteelFamily[] CLASSIFY_ORDER = {SteelFamily.MARAGING, SteelFamily.INCONEL,
        SteelFamily.STAINLESS_316, SteelFamily.STAINLESS_304, SteelFamily.TOOL_HSS, SteelFamily.HEAT_RESISTANT,
        SteelFamily.SOUR_SERVICE, SteelFamily.RAIL_WIRE, SteelFamily.LOW_ALLOY, SteelFamily.CARBON};

    public SteelGrade {
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(designation, "designation");
        Objects.requireNonNull(state, "state");
        chemPct = Collections.unmodifiableMap(new LinkedHashMap<>(chemPct));
    }

    /** Марка семейства с химией (будет квантована) в состоянии {@code state}; свойства — расчётные. */
    public static SteelGrade of(SteelFamily family, String designation, Map<String, Float> chemPct,
            HeatTreatState state) {
        Map<String, Float> q = quantize(chemPct);
        double f = strengthFactor(family, q);
        double[] sf = family.stateFactor(state);
        double y = family.yieldPa, u = family.utsPa;
        if (family == SteelFamily.LOW_ALLOY) {
            double micro = Math.min(1, (pct(q, AlloyElement.NB) + pct(q, AlloyElement.V)) / MICROALLOY_FULL_PCT);
            y += micro * MICROALLOY_YIELD_PA;
            u += micro * MICROALLOY_UTS_PA;
        }
        y *= f * sf[0];
        u *= f * sf[1];
        double hrc;
        if (state == HeatTreatState.QUENCHED
                && (family.hardening == SteelFamily.Hardening.FERRITIC || family.hardening == SteelFamily.Hardening.TOOL))
            hrc = Math.min(66, 20 + 60 * Math.sqrt(pct(q, AlloyElement.C)));
        else hrc = Math.max(0, 2.8 + 0.0246 * u / 1e6);
        return new SteelGrade(family, designation, q, state, y, u, hrc, carbonEquivalent(q));
    }

    /** Марка по химии расплава: семейство — самое требовательное, чьи требования выполнены. */
    public static SteelGrade classify(Map<AlloyElement, Double> chem, HeatTreatState state) {
        Map<String, Float> pctMap = new LinkedHashMap<>();
        for (Map.Entry<AlloyElement, Double> e : chem.entrySet())
            if (e.getKey() != AlloyElement.O) pctMap.put(e.getKey().symbol, (float) (e.getValue() * 100));
        Map<String, Float> q = quantize(pctMap);
        for (SteelFamily f : CLASSIFY_ORDER) {
            if (f == SteelFamily.SOUR_SERVICE && (state != HeatTreatState.QUENCHED_TEMPERED
                    || pct(q, AlloyElement.S) / 100 > f.maxSulfur)) continue;
            if (shortfall(f, q) == 0) return of(f, f.designation, q, state);
        }
        return of(SteelFamily.CARBON, SteelFamily.CARBON.designation, q, state);
    }

    /** Та же химия в другом состоянии. */
    public SteelGrade withState(HeatTreatState s) {
        return of(family, designation, chemPct, s);
    }

    /** Доля элемента. */
    public double frac(AlloyElement e) {
        return pct(chemPct, e) / 100;
    }

    /** Химия, требования семейства и (для кислой) состояние и твёрдость выполнены. */
    public boolean meetsSpec() {
        return shortfall(family, chemPct) == 0 && frac(AlloyElement.S) <= family.maxSulfur
                && (family != SteelFamily.SOUR_SERVICE
                        || state == HeatTreatState.QUENCHED_TEMPERED && hardnessHrc <= SOUR_MAX_HRC);
    }

    /** Годна для H₂S: S ≤ 0,005 %, ≤ 22 HRC, не сырая закалка. */
    public boolean sourCapable() {
        return frac(AlloyElement.S) <= SteelFamily.SOUR_SERVICE.maxSulfur && hardnessHrc <= SOUR_MAX_HRC
                && state != HeatTreatState.QUENCHED;
    }

    /** C_экв по МИС, %. */
    public static double carbonEquivalent(Map<String, Float> chemPct) {
        return pct(chemPct, AlloyElement.C) + pct(chemPct, AlloyElement.MN) / 6
                + (pct(chemPct, AlloyElement.CR) + pct(chemPct, AlloyElement.MO) + pct(chemPct, AlloyElement.V)) / 5
                + (pct(chemPct, AlloyElement.NI) + pct(chemPct, AlloyElement.CU)) / 15;
    }

    private static double pct(Map<String, Float> m, AlloyElement e) {
        Float v = m.get(e.symbol);
        return v == null ? 0 : v;
    }

    /** Σ относительного недобора требований семейства. */
    private static double shortfall(SteelFamily f, Map<String, Float> q) {
        double s = 0;
        for (Map.Entry<AlloyElement, Double> r : f.required.entrySet()) {
            double need = r.getValue();
            s += Math.max(0, need - pct(q, r.getKey()) / 100 - 1e-9) / need;
        }
        return s;
    }

    private static double strengthFactor(SteelFamily f, Map<String, Float> q) {
        double sulfur = pct(q, AlloyElement.S) / 100;
        double k = 1 - STRENGTH_PER_SHORTFALL * shortfall(f, q)
                - STRENGTH_PER_SULFUR * Math.max(0, sulfur / f.maxSulfur - 1);
        return Math.max(MIN_STRENGTH_FACTOR, Math.min(1, k));
    }

    private static Map<String, Float> quantize(Map<String, Float> chemPct) {
        Map<String, Float> out = new LinkedHashMap<>();
        List<Map.Entry<AlloyElement, Float>> alloying = new ArrayList<>();
        for (Map.Entry<String, Float> e : chemPct.entrySet()) {
            AlloyElement el = AlloyElement.bySymbol(e.getKey());
            if (el == null) throw new IllegalArgumentException("unknown element: " + e.getKey());
            double v = e.getValue();
            if (el == AlloyElement.O) continue;
            if (el.alloying()) {
                float r = (float) (Math.round(v * 10) / 10.0);
                if (r > 0) alloying.add(Map.entry(el, r));
            } else {
                double step = el == AlloyElement.C ? 100 : 1000;
                float r = (float) (Math.round(v * step) / step);
                if (r > 0) out.put(el.symbol, r);
            }
        }
        alloying.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));
        for (int i = 0; i < Math.min(MAX_ALLOYING, alloying.size()); i++)
            out.put(alloying.get(i).getKey().symbol, alloying.get(i).getValue());
        return out;
    }
}
