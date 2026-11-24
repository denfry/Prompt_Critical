package dev.denfry.promptcritical.sim.manufacturing.assembly;

import dev.denfry.promptcritical.sim.manufacturing.PartDefect;
import dev.denfry.promptcritical.sim.manufacturing.PartSpec;
import dev.denfry.promptcritical.sim.manufacturing.assembly.PartRequirement.Aspect;
import java.util.List;
import java.util.Objects;

/**
 * Оценка сборки (18 §18.15.2, таблица §18.8.3): детали, прошедшие ворота, дают начальные значения отказов модели 13.
 * Коэффициенты помечены [BALANCE] в GDD; их подбирает плейтест MF8.
 */
public final class AssemblyScorer {
    /** Рост износа подшипника на каждый квалитет грубее требуемого. [BALANCE] */
    public static final double WEAR_PER_IT = 0.3;
    /** Рост износа на каждые 5 HRC недостающей твёрдости шейки. [BALANCE] */
    public static final double WEAR_PER_HRC_STEP = 0.15;
    /** Рост износа на ступень дефекта посадочной детали или подшипника. [BALANCE] */
    public static final double WEAR_PER_DEFECT_LEVEL = 0.25;
    /** Виброскорость на корпусе от класса балансировки: v ≈ k·G, мм/с. [BALANCE] */
    public static final double VIBRATION_PER_G = 0.12;
    /** Несоосность: база и прибавки на квалитет и ступень коробления станины, мм. [BALANCE] */
    public static final double MISALIGN_BASE_MM = 0.01, MISALIGN_PER_IT_MM = 0.004, MISALIGN_PER_BUCKLE_MM = 0.04;
    /** Течь уплотнения на ступень дефекта в квадрате и течь прокладки на ступень. [BALANCE] */
    public static final double SEAL_LEAK_PER_LEVEL2 = 0.004, FLANGE_LEAK_PER_LEVEL = 0.08;

    /** Деталь в позиции стенда; {@code spec} null — номинальная деталь без паспорта. */
    public record Placed(String role, PartSpec spec) {
        public Placed {
            Objects.requireNonNull(role, "role");
        }
    }

    /** Качество обмотки из пакета статора (MF4): {@code Stator.insulationStartMOhm()} и {@code efficiency()}. */
    public record Winding(double insulationMOhm, double efficiency, double nominalEfficiency) {
        public Winding {
            if (!(insulationMOhm > 0) || !(efficiency > 0 && efficiency < 1) || !(nominalEfficiency > 0
                    && nominalEfficiency < 1)) {
                throw new IllegalArgumentException("winding");
            }
        }
    }

    /** Чем стенд балансирует ротор (ISO 21940-11, класс G, мм/с). */
    public enum Balancing {
        /** Без балансировки. */
        NONE(40),
        /** Статическая на ножах, руками (I0). */
        STATIC(16),
        /** Динамическая на стенде с приводом от вала. */
        DYNAMIC(6.3),
        /** Балансировочный станок с измерением (I2+). */
        PRECISION(2.5);

        public final double gradeMmS;

        Balancing(double g) {
            this.gradeMmS = g;
        }
    }

    private AssemblyScorer() {
    }

    /**
     * @param parts   детали по позициям (все уже прошли {@link AssemblyGate})
     * @param winding качество обмотки, если в спецификации есть позиция {@link Aspect#WINDING}; иначе null
     */
    public static MachinePassport score(MachineSpec spec, List<Placed> parts, Balancing balancing, Winding winding) {
        Objects.requireNonNull(balancing, "balancing");
        double wear = 1, imbalance = 0, misalign = 0, seal = 0, flange = 0;
        boolean rotor = false, alignment = false;
        for (Placed p : parts) {
            PartRequirement req = spec.requirement(p.role());
            PartSpec s = p.spec();
            if (req.has(Aspect.BEARING_SEAT)) wear += seatPenalty(req, s);
            if (req.has(Aspect.BEARING)) wear += WEAR_PER_DEFECT_LEVEL * defectLevels(s);
            if (req.has(Aspect.ROTOR)) {
                rotor = true;
                // пористость и включения колеса дают неуравновешенность сверх класса балансировки
                imbalance = Math.max(imbalance, VIBRATION_PER_G * balancing.gradeMmS * (1 + 0.5 * defectLevels(s)));
            }
            if (req.has(Aspect.ALIGNMENT)) {
                alignment = true;
                int it = s == null ? req.targetIt() == 0 ? 7 : req.targetIt() : IsoFits.effective(s.itGrade());
                misalign += MISALIGN_PER_IT_MM * Math.max(0, it - 7) + MISALIGN_PER_BUCKLE_MM * level(s, PartDefect.BUCKLED);
            }
            if (req.has(Aspect.SEAL)) {
                int l = defectLevels(s);
                seal = Math.max(seal, SEAL_LEAK_PER_LEVEL2 * l * l);
            }
            if (req.has(Aspect.GASKET)) flange = Math.max(flange, FLANGE_LEAK_PER_LEVEL * defectLevels(s));
        }
        if (alignment) misalign += MISALIGN_BASE_MM;
        if (!rotor) imbalance = 0;
        double insulation = Double.NaN, eff = 1;
        if (winding != null) {
            insulation = winding.insulationMOhm();
            eff = Math.min(1.05, winding.efficiency() / winding.nominalEfficiency());
        }
        return new MachinePassport(wear, imbalance, misalign, seal, flange, insulation, eff,
                score(wear, imbalance, misalign, seal, flange, eff));
    }

    /** Посадка подшипника: квалитет шейки грубее требуемого, недостаток твёрдости, дефекты шейки. */
    static double seatPenalty(PartRequirement req, PartSpec s) {
        if (s == null) return 0;
        double p = 0;
        if (req.targetIt() > 0) p += WEAR_PER_IT * Math.max(0, IsoFits.effective(s.itGrade()) - req.targetIt());
        if (req.minHrc() > 0) p += WEAR_PER_HRC_STEP * Math.max(0, req.minHrc() - s.hardnessHrc()) / PartSpec.HRC_STEP;
        if (!req.heatTreat().isEmpty() && !req.heatTreat().contains(s.heatTreat())) p += WEAR_PER_HRC_STEP;
        return p + WEAR_PER_DEFECT_LEVEL * defectLevels(s);
    }

    private static int defectLevels(PartSpec s) {
        if (s == null) return 0;
        int sum = 0;
        for (int l : s.defects().values()) sum += l;
        return sum;
    }

    private static int level(PartSpec s, PartDefect d) {
        return s == null ? 0 : s.defects().getOrDefault(d, 0);
    }

    /** Общая оценка: 100 минус доли пути к третьей ступени каждого отказа. [BALANCE] */
    private static int score(double wear, double imbalance, double misalign, double seal, double flange, double eff) {
        double loss = 25 * Math.min(1, (wear - 1) / 3) + 20 * Math.min(1, imbalance / 4.5)
                + 20 * Math.min(1, misalign / 0.2) + 15 * Math.min(1, seal / 0.1) + 10 * Math.min(1, flange / 0.6)
                + 10 * Math.min(1, Math.max(0, 1 - eff) / 0.1);
        return (int) Math.round(Math.clamp(100 - loss, 0, 100));
    }
}
