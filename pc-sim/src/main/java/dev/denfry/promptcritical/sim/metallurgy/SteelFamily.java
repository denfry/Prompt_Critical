package dev.denfry.promptcritical.sim.metallurgy;

import java.util.Map;

/**
 * Семейство стали (08 §8.6.4): прочность в опорном состоянии, требования к химии (минимальные доли), предел серы.
 * У {@code INCONEL} в таблице GDD «240 / 1035 (718)» смешаны два сплава — взят 600: 240/620 [сверить].
 * {@code RAIL_WIRE} — катанка до волочения (проволока 1570–1960 — после {@code wire_drawing_bench}) [сверить].
 */
public enum SteelFamily {
    CARBON("Ст3", 245, 410, HeatTreatState.AS_ROLLED, Hardening.FERRITIC, 0.0005, Map.of(AlloyElement.C, 0.001)),
    LOW_ALLOY("09Г2С", 345, 490, HeatTreatState.AS_ROLLED, Hardening.FERRITIC, 0.0004,
            Map.of(AlloyElement.MN, 0.010)),
    HEAT_RESISTANT("12Х1МФ", 255, 470, HeatTreatState.NORMALIZED, Hardening.FERRITIC, 0.00025,
            Map.of(AlloyElement.CR, 0.010, AlloyElement.MO, 0.0025)),
    STAINLESS_304("08Х18Н10", 205, 515, HeatTreatState.SOLUTION_ANNEALED, Hardening.AUSTENITIC, 0.0003,
            Map.of(AlloyElement.CR, 0.18, AlloyElement.NI, 0.08)),
    STAINLESS_316("08Х17Н13М2", 205, 515, HeatTreatState.SOLUTION_ANNEALED, Hardening.AUSTENITIC, 0.0003,
            Map.of(AlloyElement.CR, 0.165, AlloyElement.NI, 0.10, AlloyElement.MO, 0.02)),
    MARAGING("Н18К9М5Т", 1900, 2000, HeatTreatState.AGED, Hardening.MARAGING, 0.0001,
            Map.of(AlloyElement.NI, 0.18, AlloyElement.CO, 0.09, AlloyElement.MO, 0.05, AlloyElement.TI, 0.007)),
    /** L80/C90: требует Q+T, S ≤ 0,005 % и ≤ 22 HRC (NACE MR0175). */
    SOUR_SERVICE("L80", 552, 655, HeatTreatState.QUENCHED_TEMPERED, Hardening.FERRITIC, 0.00005, Map.of()),
    INCONEL("ХН78Т", 240, 620, HeatTreatState.SOLUTION_ANNEALED, Hardening.AUSTENITIC, 0.00015,
            Map.of(AlloyElement.NI, 0.52, AlloyElement.CR, 0.15)),
    TOOL_HSS("Р6М5", 2000, 2400, HeatTreatState.QUENCHED_TEMPERED, Hardening.TOOL, 0.0003,
            Map.of(AlloyElement.MO, 0.05, AlloyElement.CR, 0.04, AlloyElement.V, 0.02)),
    RAIL_WIRE("65Г", 700, 1000, HeatTreatState.AS_ROLLED, Hardening.FERRITIC, 0.0004, Map.of(AlloyElement.C, 0.006));

    /** Обозначение по умолчанию (первый пример таблицы 08 §8.6.4). */
    public final String designation;
    /** σ₀,₂ и σ_в в {@link #referenceState}, Па. */
    public final double yieldPa, utsPa;
    public final HeatTreatState referenceState;
    final Hardening hardening;
    /** Предел S, доля. */
    public final double maxSulfur;
    /** Минимальные доли легирующих. */
    public final Map<AlloyElement, Double> required;

    SteelFamily(String designation, double yieldMpa, double utsMpa, HeatTreatState referenceState,
            Hardening hardening, double maxSulfur, Map<AlloyElement, Double> required) {
        this.designation = designation;
        this.yieldPa = yieldMpa * 1e6;
        this.utsPa = utsMpa * 1e6;
        this.referenceState = referenceState;
        this.hardening = hardening;
        this.maxSulfur = maxSulfur;
        this.required = required;
    }

    /** Множители σ₀,₂ и σ_в состояния относительно {@link #referenceState}. */
    double[] stateFactor(HeatTreatState state) {
        double[] a = hardening.factor(state), r = hardening.factor(referenceState);
        return new double[] {a[0] / r[0], a[1] / r[1]};
    }

    /** Реакция на термообработку по классу структуры [аппрокс.: Q+T даёт +45 % σ₀,₂, 08 §8.7.5 — +30–60 %]. */
    enum Hardening {
        FERRITIC, AUSTENITIC, MARAGING, TOOL;

        double[] factor(HeatTreatState s) {
            return switch (this) {
                case FERRITIC -> switch (s) {
                    case QUENCHED_TEMPERED -> new double[] {1.45, 1.2};
                    case QUENCHED -> new double[] {1.8, 1.6};
                    case STRESS_RELIEVED -> new double[] {0.97, 0.98};
                    case SOLUTION_ANNEALED -> new double[] {0.9, 0.95};
                    default -> new double[] {1, 1};
                };
                case AUSTENITIC -> s == HeatTreatState.AS_ROLLED ? new double[] {1.2, 1.05} : new double[] {1, 1};
                case MARAGING -> s == HeatTreatState.AGED ? new double[] {1, 1} : new double[] {0.42, 0.5};
                case TOOL -> switch (s) {
                    case QUENCHED_TEMPERED -> new double[] {1, 1};
                    case QUENCHED -> new double[] {1.05, 1};
                    default -> new double[] {0.35, 0.4};
                };
            };
        }
    }
}
