package dev.denfry.promptcritical.sim.automation.sis;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Логический блок ПАЗ (противоаварийной автоматической защиты, SIS по IEC 61511): три аналоговых датчика 4–20 мА,
 * голосование MooN, выход «снятием питания» (de-energize to trip), диагностика линии, байпас и квитирование.
 *
 * <p>Принципы, которые и делают систему защитной, а не просто ещё одним регулятором:
 * <ul>
 *   <li>Отказ в безопасную сторону: пропало 24 В — реле отпадает и защита срабатывает; канал с током вне
 *       3,6…20,8 мА (NAMUR NE 43: обрыв, КЗ, отказ датчика) голосует «за останов».</li>
 *   <li>Срабатывание защёлкивается: снять его может только квитирование, и только когда все каналы снова исправны и
 *       ниже уставки с гистерезисом — защита не перезапускает процесс сама.</li>
 *   <li>Байпас (на время проверки датчика) держит выход во взведённом состоянии, но зажигает сигнализацию и
 *       копит своё время: это записывается и видно на щите.</li>
 * </ul>
 *
 * <p>Голосование k-из-n по исправным и неисправным каналам: неисправный канал считается голосом за останов, поэтому
 * 2oo3 с одним отказавшим датчиком работает как 1oo2 из оставшихся (типовая деградация).
 */
public final class SisLogic {
    /** Пределы исправной линии по NAMUR NE 43, мА. */
    public static final double FAULT_LOW_MA = 3.6, FAULT_HIGH_MA = 20.8;
    /** Гистерезис возврата, мА. */
    public static final double RETURN_MA = 0.2;
    public static final int CHANNELS = 3;

    /** Схема голосования: сколько голосов нужно ({@code k}) из скольких каналов ({@code n}). */
    public enum Voting {
        ONE_OO_ONE(1, 1), ONE_OO_TWO(1, 2), TWO_OO_TWO(2, 2), TWO_OO_THREE(2, 3);

        public final int k, n;

        Voting(int k, int n) {
            this.k = k;
            this.n = n;
        }
    }

    /** Состояние защиты для щита и журнала. */
    public enum State { NORMAL, TRIPPED, BYPASSED, OFF }

    private boolean tripped, bypass;
    private double bypassS;
    private int trips;
    private final boolean[] channelFault = new boolean[CHANNELS], channelDemand = new boolean[CHANNELS];

    /**
     * Шаг логики. {@code ma} — токи каналов (используются первые {@code voting.n}); {@code high} — защита по
     * превышению (иначе по понижению); {@code reset} — квитирование. Возвращает {@code true}, если выходное реле
     * должно быть под током (процесс разрешён).
     */
    public boolean step(double dtS, boolean powered, double[] ma, Voting voting, boolean high, double setpointMa, boolean reset) {
        if (!powered) {
            tripped = true; // питание пропало — защита сработала и останется сработавшей до квитирования
            return false;
        }
        int votes = 0;
        boolean allClear = true;
        for (int i = 0; i < CHANNELS; i++) {
            double x = ma[i];
            boolean fault = !(x >= FAULT_LOW_MA && x <= FAULT_HIGH_MA);
            boolean demand = high ? x >= setpointMa : x <= setpointMa;
            boolean clear = high ? x < setpointMa - RETURN_MA : x > setpointMa + RETURN_MA;
            channelFault[i] = fault;
            channelDemand[i] = demand;
            if (i < voting.n) {
                if (fault || demand) votes++;
                if (fault || !clear) allClear = false;
            }
        }
        if (votes >= voting.k && !tripped) {
            tripped = true;
            if (!bypass) trips++;
        }
        if (reset && tripped && allClear) tripped = false;
        if (bypass) bypassS += dtS;
        return !tripped || bypass;
    }

    public void setBypass(boolean on) {
        bypass = on;
    }

    public boolean bypass() {
        return bypass;
    }

    public boolean tripped() {
        return tripped;
    }

    public State state(boolean powered) {
        if (!powered) return State.OFF;
        if (bypass) return State.BYPASSED;
        return tripped ? State.TRIPPED : State.NORMAL;
    }

    public boolean channelFault(int i) {
        return channelFault[i];
    }

    public boolean channelDemand(int i) {
        return channelDemand[i];
    }

    /** Время в байпасе с последнего сброса счётчика, с. */
    public double bypassSeconds() {
        return bypassS;
    }

    /** Число срабатываний (без срабатываний в байпасе). */
    public int trips() {
        return trips;
    }

    // ---------------------------------------------------------------- надёжность

    /**
     * Средняя вероятность отказа по требованию PFDavg по упрощённым формулам IEC 61508-6, прил. B (без времени
     * восстановления и диагностического покрытия): λ_DU — интенсивность опасных необнаруживаемых отказов одного
     * канала, 1/ч; {@code tiH} — интервал контрольных испытаний, ч; β — доля отказов по общей причине.
     * <ul>
     *   <li>1oo1: λT/2</li>
     *   <li>1oo2: ((1−β)λT)²/3 + βλT/2</li>
     *   <li>2oo2: λT (два канала последовательно)</li>
     *   <li>2oo3: ((1−β)λT)² + βλT/2</li>
     * </ul>
     */
    public static double pfdAvg(Voting v, double lambdaDuPerH, double tiH, double beta) {
        double lt = lambdaDuPerH * tiH, ind = (1 - beta) * lt, ccf = beta * lt / 2;
        return switch (v) {
            case ONE_OO_ONE -> lt / 2;
            case ONE_OO_TWO -> ind * ind / 3 + ccf;
            case TWO_OO_TWO -> lt;
            case TWO_OO_THREE -> ind * ind + ccf;
        };
    }

    /** Уровень полноты безопасности по PFDavg (IEC 61508-1, табл. 2, режим с низкой частотой запросов); 0 — ниже SIL 1. */
    public static int sil(double pfdAvg) {
        if (pfdAvg < 1e-4) return 4;
        if (pfdAvg < 1e-3) return 3;
        if (pfdAvg < 1e-2) return 2;
        if (pfdAvg < 1e-1) return 1;
        return 0;
    }

    // ---------------------------------------------------------------- сохранение

    public void save(StateWriter w) {
        w.putBoolean("tripped", tripped);
        w.putBoolean("bypass", bypass);
        w.putDouble("bypass_s", bypassS);
        w.putInt("trips", trips);
    }

    public void load(StateReader r) {
        tripped = r.getBoolean("tripped");
        bypass = r.getBoolean("bypass");
        bypassS = r.optDouble("bypass_s", 0);
        trips = r.optInt("trips", 0);
    }
}
