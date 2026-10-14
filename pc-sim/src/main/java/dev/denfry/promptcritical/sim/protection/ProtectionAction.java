package dev.denfry.promptcritical.sim.protection;

/** Команда защиты для {@code ReactorCore} (01 §1.13.3–§1.13.4). */
public sealed interface ProtectionAction
        permits ProtectionAction.Trip, ProtectionAction.RodBlock,
                ProtectionAction.Stepback, ProtectionAction.SafetyInjection {
    /** Освобождение стержней (питание приводов снято, t_latch истекло); по записи на каждую причину АЗ, один раз. */
    record Trip(TripReason reason, boolean firstOut) implements ProtectionAction {}

    /** Запрет вывода стержней на этот тик. */
    record RodBlock(InterlockReason reason) implements ProtectionAction {}

    /** Ускоренная предупредительная защита (ПЗ-1): ввод рабочей группы штатным приводом до доли мощности. */
    record Stepback(double targetPowerFrac) implements ProtectionAction {}

    /** Сигнал САОЗ на этот тик; удержание и исполнение — 04c/03a. */
    record SafetyInjection(String cause) implements ProtectionAction {}
}
