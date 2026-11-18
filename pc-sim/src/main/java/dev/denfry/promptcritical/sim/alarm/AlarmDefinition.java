package dev.denfry.promptcritical.sim.alarm;

/**
 * Описание сигнализации (03c §3c.6.2). Приоритет 1–3 — сигнализация, требующая действия оператора; 4 — журнальная (в
 * списке, без звука, вне метрик лавины, 03c §3c.6.1).
 *
 * <p>Зона нечувствительности {@code deadband} — доля |{@code limit}| (тест-кейс T-3c-03: предел 10, deadband 2 % →
 * условие HIGH уходит ниже 9,8); при нулевом пределе — абсолютная величина. Она действует только на снятие уже
 * возникшего условия: возникает условие строго за пределом (T-3c-01). Задержки {@code onDelayS}/{@code offDelayS} —
 * сколько условие должно непрерывно держаться (отсутствовать), прежде чем сигнализация возникнет (уйдёт).
 *
 * @param id             номер в движке; при {@link AlarmEngine#define} игнорируется — номер выдаёт движок
 * @param tag            тег измеренного значения ({@code SOFT} — любая метка)
 * @param latching       квитированная сигнализация остаётся до сброса оператором, даже если условие ушло
 * @param group          группа подавления и первопричины (пусто — без группы)
 * @param operatorAction что должен сделать оператор (обязательная часть сигнализации по ISA-18.2)
 */
public record AlarmDefinition(int id, String tag, AlarmKind kind, int priority, double limit, double deadband, double onDelayS,
        double offDelayS, boolean latching, String group, String message, String operatorAction) {

    public AlarmDefinition {
        if (tag == null || tag.isEmpty()) throw new IllegalArgumentException("alarm tag");
        if (kind == null) throw new IllegalArgumentException("alarm kind");
        if (priority < 1 || priority > 4) throw new IllegalArgumentException("priority must be 1..4");
        if (kind.analog() && !Double.isFinite(limit)) throw new IllegalArgumentException("analog alarm needs a finite limit");
        if (!(deadband >= 0) || !(onDelayS >= 0) || !(offDelayS >= 0)) throw new IllegalArgumentException("deadband and delays must be >= 0");
        group = group == null ? "" : group;
        message = message == null ? "" : message;
        operatorAction = operatorAction == null ? "" : operatorAction;
    }

    /** То же описание с другим номером. */
    public AlarmDefinition withId(int newId) {
        return new AlarmDefinition(newId, tag, kind, priority, limit, deadband, onDelayS, offDelayS, latching, group, message, operatorAction);
    }

    /** Ширина зоны нечувствительности в единицах тега. */
    public double deadbandAbs() {
        return limit == 0 ? deadband : deadband * Math.abs(limit);
    }

    /** Ключ сигнализации по тегу и виду: у тега не больше одной сигнализации каждого вида. */
    public String key() {
        return tag + "|" + kind.name();
    }
}
