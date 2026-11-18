package dev.denfry.promptcritical.sim.ops;

/**
 * Уровень принуждения нарядов {@code ops.permit_enforcement} (13 §13.0.3): {@code OFF} — механика выключена,
 * {@code ADVISORY} — предупреждение и запись в журнал, {@code ENFORCED} — действие без наряда не выполняется. Замок LOTO
 * действует всегда, независимо от уровня.
 */
public enum PermitEnforcement { OFF, ADVISORY, ENFORCED }
