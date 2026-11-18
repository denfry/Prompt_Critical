package dev.denfry.promptcritical.sim.alarm;

/**
 * Сигнализация в движке: описание и живое состояние. Наружу — только чтение; меняет состояние {@link AlarmEngine}.
 */
public final class Alarm {
    AlarmDefinition def;
    /** Объявлена программой (Lua {@code defineAlarm}): только такие можно удалить. */
    final boolean program;
    AlarmState state = AlarmState.NORMAL;
    /** Условие после задержек. */
    boolean cond;
    /** Начало непрерывного «сырого» перехода (условие за пределом при {@code !cond} или в норме при {@code cond}). */
    double pendingSince = Double.NaN;
    double value = Double.NaN, prevValue = Double.NaN, prevT = Double.NaN;
    /** Скорость изменения, ед./с (для RATE_OF_CHANGE). */
    double rate;
    boolean softActive;
    double tRaise = Double.NaN, tAck = Double.NaN, condSince = Double.NaN, shelvedUntil = Double.NaN;
    int count;
    boolean firstOut, chattering;
    /** Последние три возникновения — для дребезга. */
    final double[] raises = {Double.NaN, Double.NaN, Double.NaN};
    /** Задержка возникновения сейчас: удваивается у дребезжащей при {@code auto_chatter_delay}. */
    double onDelayS;

    Alarm(AlarmDefinition def, boolean program) {
        this.def = def;
        this.program = program;
        this.onDelayS = def.onDelayS();
    }

    public int id() {
        return def.id();
    }

    public AlarmDefinition definition() {
        return def;
    }

    public boolean program() {
        return program;
    }

    public AlarmState state() {
        return state;
    }

    /** Условие есть (после задержек), в том числе в скрытых состояниях. */
    public boolean active() {
        return cond;
    }

    /** Последнее измеренное значение; NaN — нет (датчик {@code BAD}). */
    public double value() {
        return value;
    }

    public double tRaise() {
        return tRaise;
    }

    public double tAck() {
        return tAck;
    }

    /** Возникновений за всё время. */
    public int count() {
        return count;
    }

    /** Первопричина: первое возникновение в группе после ≥ 2 с затишья (03c §3c.2.7); снимается квитированием. */
    public boolean firstOut() {
        return firstOut;
    }

    public boolean chattering() {
        return chattering;
    }

    public double shelvedUntil() {
        return shelvedUntil;
    }

    public double onDelayS() {
        return onDelayS;
    }
}
