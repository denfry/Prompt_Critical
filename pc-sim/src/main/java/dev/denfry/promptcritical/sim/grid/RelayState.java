package dev.denfry.promptcritical.sim.grid;

/**
 * Память реле между тиками: выдержки, счётчики, защёлки. Уставки — в записи {@link ProtectionRelay},
 * состояние — здесь, поэтому записи остаются неизменяемыми (07 §7.19.1).
 */
public final class RelayState {
    /** Выдержка времени или интеграл 1/t(M) обратнозависимой характеристики. */
    double timer;
    /** Выдержки ступеней дистанционной защиты. */
    final double[] zones;
    /** Смещение уставки частоты (погрешность поколения) или отсчёт проворотов при установке. */
    double bias;
    /** АПВ: фаза цикла и использованные циклы. */
    int phase, count;
    /** Выход реле на прошлом тике. */
    boolean output;
    /** АЧР: нагрузка отключена этим реле и ждёт ЧАПВ. */
    boolean latched;

    public RelayState() {
        this(0);
    }

    RelayState(int zones) {
        this.zones = new double[zones];
    }

    /**
     * Память реле из прежней сети при её пересборке: выдержка, ступени, цикл АПВ, выход, защёлка АЧР. Смещение
     * уставки задаётся заново при установке.
     */
    void carryFrom(RelayState o) {
        timer = o.timer;
        if (o.zones.length == zones.length) System.arraycopy(o.zones, 0, zones, 0, zones.length);
        phase = o.phase;
        count = o.count;
        output = o.output;
        latched = o.latched;
    }

    /** Выход реле (команда отключения, у АЧР — нагрузка отключена) на последнем тике. */
    public boolean operated() {
        return output;
    }
}
