package dev.denfry.promptcritical.sim.grid;

import java.util.Objects;

/**
 * Паспорт выключателя (07 §7.4.6).
 *
 * @param openTimeS             t_откл — от команды до погасания дуги, с
 * @param closeTimeS            t_вкл — от команды до касания контактов, с
 * @param interruptKa           номинальный ток отключения, кА; больше — дуга не гаснет
 * @param faultOpsBeforeService отключений КЗ до ревизии; 0 — износ не ведётся
 */
public record BreakerSpec(BreakerKind kind, double openTimeS, double closeTimeS, double interruptKa,
        int faultOpsBeforeService) {
    /** Отключение тока выше этой доли I_откл,ном считается отключением КЗ и изнашивает контакты. */
    public static final double FAULT_CURRENT_FRACTION = 0.1;

    public BreakerSpec {
        Objects.requireNonNull(kind, "kind");
        if (!(openTimeS >= 0 && closeTimeS >= 0)) throw new IllegalArgumentException("times");
        if (!(interruptKa > 0)) throw new IllegalArgumentException("interruptKa");
        if (faultOpsBeforeService < 0) throw new IllegalArgumentException("faultOpsBeforeService");
    }

    /** Масляный, I1: 0,10 / 0,45 с, 20 кА, 15 отключений КЗ до ревизии. */
    public static BreakerSpec oil() {
        return new BreakerSpec(BreakerKind.OIL, 0.10, 0.45, 20, 15);
    }

    /** Воздушный, I2. */
    public static BreakerSpec airBlast() {
        return new BreakerSpec(BreakerKind.AIR_BLAST, 0.065, 0.2, 31.5, 0);
    }

    /** Вакуумный 6–35 кВ, I3. */
    public static BreakerSpec vacuum() {
        return new BreakerSpec(BreakerKind.VACUUM, 0.05, 0.07, 25, 60);
    }

    /** Элегазовый 110–220 кВ, I3. */
    public static BreakerSpec sf6() {
        return new BreakerSpec(BreakerKind.SF6, 0.04, 0.08, 40, 0);
    }

    /** Автомат 0,4 кВ в щите; времена [сверить] — в таблице §7.4.6 их нет. */
    public static BreakerSpec lvAir() {
        return new BreakerSpec(BreakerKind.LV_AIR, 0.02, 0.05, 25, 0);
    }
}
