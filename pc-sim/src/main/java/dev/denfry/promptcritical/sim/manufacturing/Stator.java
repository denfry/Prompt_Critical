package dev.denfry.promptcritical.sim.manufacturing;

import java.util.Objects;

/**
 * Намотанный статор эталонного двигателя 5,5 кВт (18 §18.6.8–18.6.9, §18.8.3): пакет, класс изоляции, заполнение паза,
 * сопротивление фазы при рабочей температуре, Ом, и тяжесть дефекта изоляции 0..1. Из него — потери в стали и меди,
 * КПД двигателя при номинале и начальное сопротивление изоляции для модели обслуживания 13. Ротор, подшипники и
 * вентилятор до MF5 номинальные.
 */
public record Stator(CoreStack core, InsulationClass insulationClass, double fillFactor, double phaseResistanceOhm,
                     double flawSeverity) {
    /** Номинал эталонного двигателя: 5,5 кВт на валу, фазный ток 11,5 А при 380 В, 50 Гц [сверить: 4А112М4]. */
    public static final double RATED_W = 5500, PHASE_CURRENT_A = 11.5, FREQUENCY_HZ = 50;
    /** Амплитуда индукции в спинке и зубцах, Тл [сверить: 1,5–1,7]. */
    public static final double CORE_B_T = 1.5;
    /** Рабочая температура обмотки для потерь, К (75 °C по ГОСТ Р 52776). */
    public static final double WINDING_K = 348.15;
    /** Потери ротора, механические и добавочные (доля мощности) номинального двигателя [сверить: 4А112М4]. */
    public static final double ROTOR_LOSS_W = 250, MECHANICAL_LOSS_W = 55, ADDITIONAL_FRAC = 0.005;
    /** Тяжесть дефекта снижает сопротивление изоляции на столько порядков при тяжести 1 [BALANCE]. */
    public static final double FLAW_DECADES = 2;

    public Stator {
        Objects.requireNonNull(core, "core");
        Objects.requireNonNull(insulationClass, "insulationClass");
        if (!(fillFactor > 0) || !(phaseResistanceOhm > 0) || !(flawSeverity >= 0) || !(flawSeverity <= 1))
            throw new IllegalArgumentException("fill and resistance must be positive, severity in 0..1");
    }

    /** Потери в стали при номинале, Вт. */
    public double coreLossW() {
        return core.coreLossW(CORE_B_T, FREQUENCY_HZ);
    }

    /** Потери в меди статора при номинале, Вт. */
    public double copperLossW() {
        return 3 * PHASE_CURRENT_A * PHASE_CURRENT_A * phaseResistanceOhm;
    }

    /** КПД двигателя при номинале. */
    public double efficiency() {
        double losses = coreLossW() + copperLossW() + ROTOR_LOSS_W + MECHANICAL_LOSS_W + ADDITIONAL_FRAC * RATED_W;
        return RATED_W / (RATED_W + losses);
    }

    /** Начальное сопротивление изоляции, МОм: значение {@code INSULATION_AGING} при установке (MF5). */
    public double insulationStartMOhm() {
        return insulationClass.nominalMOhm * Math.pow(10, -FLAW_DECADES * flawSeverity);
    }

    /** Уровень дефекта изоляции для паспорта детали, 0..3. */
    public int flawLevel() {
        return PartSpec.defectLevel(flawSeverity);
    }
}
