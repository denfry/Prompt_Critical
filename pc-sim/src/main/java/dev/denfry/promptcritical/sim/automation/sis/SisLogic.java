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
    // периодическая проверка (03d §3d.6.8)
    private double sinceTestH;
    private int proofTests, proofTestsOnTime, testedMask, failedMask;
    private boolean testing;

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

    // ---------------------------------------------------------------- периодическая проверка

    /** Итог шага проверки. */
    public enum ProofResult {
        /** Шаг сделан. */
        OK,
        /** Проверка подписана в срок: t_test обнулено. */
        PASSED_ON_TIME,
        /** Подписана, но после просрочки. */
        PASSED_LATE,
        /** Без байпаса имитация на датчике вызовет настоящий останов (шаг 1). */
        NEEDS_BYPASS,
        /** Подписывают после снятия байпаса (шаг 5). */
        BYPASS_STILL_ON,
        /** Проверка не начата. */
        NOT_TESTING,
        /** Ещё не все каналы схемы проверены имитацией. */
        CHANNELS_UNTESTED,
        /** Имитация не прошла: канал в отказе по NE 43 — найден отказ, функция деградирована до ремонта. */
        CHANNEL_FAILED,
        /** Каналы проверены, но голосов имитации меньше k — логика не сработала бы. */
        LOGIC_NOT_SEEN
    }

    /** Время физики с последней полной проверки, ч (растёт в SLOW: k_slow ч физики на час игры). */
    public double sinceTestH() {
        return sinceTestH;
    }

    /** Состарить функцию на {@code dtPhysH} часов физики. */
    public void ageProofTest(double dtPhysH) {
        if (dtPhysH > 0) sinceTestH += dtPhysH;
    }

    /** Проверка просрочена: t − t_test &gt; T (сигнализация P3 «ПРОВЕРКА ПАЗ ПРОСРОЧЕНА»). */
    public boolean proofTestOverdue(double intervalH) {
        return sinceTestH > intervalH;
    }

    public boolean testing() {
        return testing;
    }

    /** Битовая маска каналов, проверенных имитацией в текущей проверке. */
    public int testedMask() {
        return testedMask;
    }

    /** Битовая маска каналов, на которых проверка нашла отказ. */
    public int failedMask() {
        return failedMask;
    }

    public int proofTests() {
        return proofTests;
    }

    public int proofTestsOnTime() {
        return proofTestsOnTime;
    }

    /** Шаг 1: начать проверку — функция должна быть в байпасе, иначе имитация остановит процесс. */
    public ProofResult startProofTest() {
        if (!bypass) return ProofResult.NEEDS_BYPASS;
        testing = true;
        testedMask = 0;
        failedMask = 0;
        return ProofResult.OK;
    }

    /**
     * Шаги 2–3: имитация срабатывания на канале {@code ch} (тест петли коммуникатором). Исправный канал отзывается
     * голосом за останов; канал в отказе по NE 43 (обрыв, КЗ, выход за шкалу) — найденный отказ.
     */
    public ProofResult testChannel(int ch, Voting v) {
        if (!testing) return ProofResult.NOT_TESTING;
        if (!bypass) return ProofResult.NEEDS_BYPASS;
        if (ch < 0 || ch >= v.n) return ProofResult.OK;
        if (channelFault[ch]) {
            failedMask |= 1 << ch;
            return ProofResult.CHANNEL_FAILED;
        }
        testedMask |= 1 << ch;
        return ProofResult.OK;
    }

    /**
     * Шаг 5: байпас снят, результат подписан. Нужны все n каналов схемы без отказов; t_test обнуляется.
     *
     * @param intervalH интервал проверки T, ч физики — для отметки «в срок»
     */
    public ProofResult signProofTest(Voting v, double intervalH) {
        if (!testing) return ProofResult.NOT_TESTING;
        if (bypass) return ProofResult.BYPASS_STILL_ON;
        if (failedMask != 0) return ProofResult.CHANNEL_FAILED;
        int all = (1 << v.n) - 1;
        if ((testedMask & all) != all) return ProofResult.CHANNELS_UNTESTED;
        if (Integer.bitCount(testedMask & all) < v.k) return ProofResult.LOGIC_NOT_SEEN;
        boolean onTime = !proofTestOverdue(intervalH);
        testing = false;
        testedMask = 0;
        sinceTestH = 0;
        proofTests++;
        if (onTime) proofTestsOnTime++;
        return onTime ? ProofResult.PASSED_ON_TIME : ProofResult.PASSED_LATE;
    }

    /** Прервать проверку без подписи. */
    public void abortProofTest() {
        testing = false;
        testedMask = 0;
        failedMask = 0;
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

    /**
     * Текущая (мгновенная) вероятность отказа по требованию PFD(t) через {@code tH} часов после проверки — пилообразная
     * кривая 03d §3d.6.7, растущая от последней проверки. Канал: q = 1 − e^(−(1−β)λt); схема MooN отказывает, когда
     * отказали n − k + 1 каналов (2oo3: 3q² − 2q³); отказ по общей причине q_c = 1 − e^(−βλt) (у 1oo1 и 2oo2 — полная λ без β, как в их PFDavg). Среднее этой кривой за интервал T при λT ≪ 1 даёт
     * формулы {@link #pfdAvg}: ⟨3q²⟩ = ((1−β)λT)², ⟨q²⟩ = ((1−β)λT)²/3, ⟨βλt⟩ = βλT/2. Монотонна по t и не больше 1.
     */
    public static double pfdInstant(Voting v, double lambdaDuPerH, double tH, double beta) {
        double t = Math.max(0, tH);
        double q = -Math.expm1(-(1 - beta) * lambdaDuPerH * t), qc = -Math.expm1(-beta * lambdaDuPerH * t);
        double sys = switch (v) {
            case ONE_OO_ONE -> -Math.expm1(-lambdaDuPerH * t);
            case ONE_OO_TWO -> q * q;
            case TWO_OO_TWO -> {
                double q1 = -Math.expm1(-lambdaDuPerH * t);
                yield 1 - (1 - q1) * (1 - q1);
            }
            case TWO_OO_THREE -> 3 * q * q - 2 * q * q * q;
        };
        if (v == Voting.ONE_OO_ONE || v == Voting.TWO_OO_TWO) return sys; // последовательные каналы: β не меняет λT
        return 1 - (1 - sys) * (1 - qc);
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
        w.putDouble("since_test_h", sinceTestH);
        w.putInt("proof_tests", proofTests);
        w.putInt("proof_on_time", proofTestsOnTime);
        w.putBoolean("testing", testing);
        w.putInt("tested_mask", testedMask);
        w.putInt("failed_mask", failedMask);
    }

    public void load(StateReader r) {
        tripped = r.getBoolean("tripped");
        bypass = r.getBoolean("bypass");
        bypassS = r.optDouble("bypass_s", 0);
        trips = r.optInt("trips", 0);
        sinceTestH = r.optDouble("since_test_h", 0);
        proofTests = r.optInt("proof_tests", 0);
        proofTestsOnTime = r.optInt("proof_on_time", 0);
        testing = r.optInt("testing", 0) != 0;
        testedMask = r.optInt("tested_mask", 0);
        failedMask = r.optInt("failed_mask", 0);
    }
}
