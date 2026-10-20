package dev.denfry.promptcritical.sim.grid;

import java.util.Objects;

/**
 * Модуль РЗА (07 §7.5.2): запись — уставки, память между тиками — {@link RelayState}. Сеть вызывает
 * {@link #evaluate} каждый тик после измерений; выход {@code true} — команда (отключить, у АПВ —
 * включить, у АЧР — держать нагрузку отключённой). Выдержки квантуются тиком.
 */
public sealed interface ProtectionRelay permits ProtectionRelay.Overcurrent, ProtectionRelay.Differential,
        ProtectionRelay.Distance, ProtectionRelay.Frequency, ProtectionRelay.Voltage, ProtectionRelay.ReversePower,
        ProtectionRelay.LossOfField, ProtectionRelay.OutOfStep, ProtectionRelay.SyncCheck, ProtectionRelay.BreakerFailure,
        ProtectionRelay.Buchholz, ProtectionRelay.Thermal, ProtectionRelay.AutoReclose, ProtectionRelay.Ufls {
    /** Запас на округление при сравнении накопленной выдержки. */
    double EPS = 1e-9;
    /** Реле частоты блокируются ниже этого напряжения: на обесточенной шине частоты нет. */
    double UNDERVOLTAGE_BLOCK_PU = 0.5;

    ProtectionFunction function();

    RelayGeneration generation();

    boolean evaluate(RelayInputs in, RelayState st, double dtS);

    /** Память, которую сеть создаёт при установке реле. */
    default RelayState newState() {
        return new RelayState();
    }

    private static void check(ProtectionFunction f, RelayGeneration g, ProtectionFunction... allowed) {
        Objects.requireNonNull(f, "function");
        Objects.requireNonNull(g, "generation");
        for (ProtectionFunction a : allowed) if (a == f) return;
        throw new IllegalArgumentException(f + " is not a function of this relay");
    }

    private static void needsStatic(RelayGeneration g, String what) {
        if (g == RelayGeneration.A1_ELECTROMECHANICAL) throw new IllegalArgumentException(what + " needs A2 or later");
    }

    /** Выдержка: условие держится непрерывно {@code delayS}; сброс, как только пропало. */
    private static boolean timed(boolean condition, RelayState st, double delayS, double dt) {
        if (!condition) {
            st.timer = 0;
            return false;
        }
        st.timer += dt;
        return st.timer >= delayS - EPS;
    }

    /**
     * МТЗ 51 (IEC 60255) и токовая отсечка 50. {@code F51V} — пуск по напряжению: уставка снижается
     * пропорционально напряжению (не ниже 25 %). После АПВ — ускорение: без выдержки.
     *
     * @param pickupA        I_s, А
     * @param tms            множитель времени; у {@code DEFINITE} — выдержка, с
     * @param instantaneousA уставка отсечки I_>>, А; 0 — без отсечки
     */
    record Overcurrent(ProtectionFunction function, RelayGeneration generation, double pickupA, double tms,
            IdmtCurve curve, double instantaneousA) implements ProtectionRelay {
        public Overcurrent {
            check(function, generation, ProtectionFunction.F50, ProtectionFunction.F51, ProtectionFunction.F51V);
            Objects.requireNonNull(curve, "curve");
            if (!(pickupA > 0 && tms > 0 && instantaneousA >= 0)) throw new IllegalArgumentException("settings");
            if (function == ProtectionFunction.F50 && instantaneousA == 0)
                throw new IllegalArgumentException("50 needs an instantaneous setting");
        }

        public static Overcurrent idmt(RelayGeneration g, double pickupA, double tms, IdmtCurve curve) {
            return new Overcurrent(ProtectionFunction.F51, g, pickupA, tms, curve, 0);
        }

        public static Overcurrent instantaneous(RelayGeneration g, double pickupA) {
            return new Overcurrent(ProtectionFunction.F50, g, pickupA, 1, IdmtCurve.DEFINITE, pickupA);
        }

        @Override
        public boolean evaluate(RelayInputs in, RelayState st, double dtS) {
            double i = in.currentA;
            if (instantaneousA > 0 && i > instantaneousA) return true;
            if (function == ProtectionFunction.F50) return false;
            double pickup = function == ProtectionFunction.F51V ? pickupA * Math.max(0.25, Math.min(1, in.voltagePu))
                    : pickupA;
            if (!(i > pickup)) {
                st.timer = 0;
                return false;
            }
            if (in.accelerated) return true;
            st.timer += dtS / curve.tripTimeS(i / pickup, tms);
            return st.timer >= 1 - EPS;
        }
    }

    /**
     * Дифзащита 87T/87G/87B: |ΣI| > I_d,min + k·I_торм, k = slope1 до 2 о.е. торможения, дальше slope2.
     * При броске намагничивания с h₂ > h2Block защита заблокирована; без блокировки (h2Block = 0, реле A1)
     * бросок отключает трансформатор, если так выпало при включении (§7.5.2).
     */
    record Differential(ProtectionFunction function, RelayGeneration generation, double idMinPu, double slope1,
            double slope2, double h2Block) implements ProtectionRelay {
        static final double SLOPE_BREAK_PU = 2;

        public Differential {
            check(function, generation, ProtectionFunction.F87T, ProtectionFunction.F87G, ProtectionFunction.F87B);
            if (!(idMinPu > 0 && slope1 >= 0 && slope2 >= slope1 && h2Block >= 0 && h2Block < 1))
                throw new IllegalArgumentException("settings");
            if (h2Block > 0 && generation == RelayGeneration.A1_ELECTROMECHANICAL)
                throw new IllegalArgumentException("A1 differential relays have no harmonic blocking");
        }

        /** Уставки §7.5.2: I_d,min 0,3, k 0,3/0,6, блокировка при h₂ > 0,15 (кроме A1). */
        public static Differential standard(ProtectionFunction function, RelayGeneration g) {
            return new Differential(function, g, 0.3, 0.3, 0.6, g == RelayGeneration.A1_ELECTROMECHANICAL ? 0 : 0.15);
        }

        @Override
        public boolean evaluate(RelayInputs in, RelayState st, double dtS) {
            if (in.inrushH2 > 0) {
                if (h2Block > 0) {
                    if (in.inrushH2 > h2Block) return false;
                } else if (in.inrushTrip) {
                    return true;
                }
            }
            double r = in.restraintPu;
            double threshold = r <= SLOPE_BREAK_PU ? idMinPu + slope1 * r
                    : idMinPu + slope1 * SLOPE_BREAK_PU + slope2 * (r - SLOPE_BREAK_PU);
            return in.diffPu > threshold;
        }
    }

    /**
     * Дистанционная защита 21, круговая (mho) характеристика через начало координат: ступень срабатывает,
     * пока V₁/I₁ внутри окружности диаметром {@code reach} под углом {@code angleRad}.
     */
    record Distance(ProtectionFunction function, RelayGeneration generation, double[] zoneReachOhm,
            double[] zoneDelayS, double angleRad) implements ProtectionRelay {
        public Distance {
            check(function, generation, ProtectionFunction.F21);
            needsStatic(generation, "distance protection");
            if (zoneReachOhm.length == 0 || zoneReachOhm.length != zoneDelayS.length)
                throw new IllegalArgumentException("zones");
            zoneReachOhm = zoneReachOhm.clone();
            zoneDelayS = zoneDelayS.clone();
            for (int z = 0; z < zoneReachOhm.length; z++) {
                if (!(zoneReachOhm[z] > 0 && zoneDelayS[z] >= 0)) throw new IllegalArgumentException("zone " + z);
            }
        }

        /** Три ступени §7.5.2: 0,85 Z_л без выдержки, 1,25 Z_л — 0,4 с, 2 Z_л — 1,2 с. */
        public static Distance stepped(RelayGeneration g, double lineOhm, double angleRad) {
            return new Distance(ProtectionFunction.F21, g, new double[] {0.85 * lineOhm, 1.25 * lineOhm, 2 * lineOhm},
                    new double[] {0, 0.4, 1.2}, angleRad);
        }

        @Override
        public double[] zoneReachOhm() {
            return zoneReachOhm.clone();
        }

        @Override
        public double[] zoneDelayS() {
            return zoneDelayS.clone();
        }

        @Override
        public RelayState newState() {
            return new RelayState(zoneReachOhm.length);
        }

        @Override
        public boolean evaluate(RelayInputs in, RelayState st, double dtS) {
            boolean trip = false;
            double c = Math.cos(angleRad), s = Math.sin(angleRad);
            for (int z = 0; z < zoneReachOhm.length; z++) {
                double r = zoneReachOhm[z] / 2;
                boolean inside = Double.isFinite(in.zRe) && Math.hypot(in.zRe - r * c, in.zIm - r * s) <= r;
                if (!inside) {
                    st.zones[z] = 0;
                    continue;
                }
                st.zones[z] += dtS;
                trip |= st.zones[z] >= zoneDelayS[z] - EPS; // ступень 1 — в первый же тик
            }
            return trip;
        }
    }

    /** 81U / 81O; уставка смещена на погрешность поколения, ниже 0,5 о.е. напряжения блокируется. */
    record Frequency(ProtectionFunction function, RelayGeneration generation, double setHz, double delayS)
            implements ProtectionRelay {
        public Frequency {
            check(function, generation, ProtectionFunction.F81U, ProtectionFunction.F81O);
            if (!(setHz > 0 && delayS >= 0)) throw new IllegalArgumentException("settings");
        }

        @Override
        public boolean evaluate(RelayInputs in, RelayState st, double dtS) {
            double set = setHz + st.bias;
            boolean cond = in.voltagePu >= UNDERVOLTAGE_BLOCK_PU
                    && (function == ProtectionFunction.F81U ? in.frequencyHz < set : in.frequencyHz > set);
            return timed(cond, st, delayS, dtS);
        }
    }

    /** 27 / 59 по напряжению прямой последовательности. */
    record Voltage(ProtectionFunction function, RelayGeneration generation, double setPu, double delayS)
            implements ProtectionRelay {
        public Voltage {
            check(function, generation, ProtectionFunction.F27, ProtectionFunction.F59);
            if (!(setPu > 0 && delayS >= 0)) throw new IllegalArgumentException("settings");
        }

        /** Уставки §7.5.2: 27 — 0,7 о.е., 3 с; 59 — 1,15 о.е., 2 с. */
        public static Voltage under(RelayGeneration g) {
            return new Voltage(ProtectionFunction.F27, g, 0.7, 3);
        }

        public static Voltage over(RelayGeneration g) {
            return new Voltage(ProtectionFunction.F59, g, 1.15, 2);
        }

        @Override
        public boolean evaluate(RelayInputs in, RelayState st, double dtS) {
            boolean cond = function == ProtectionFunction.F27 ? in.voltagePu < setPu : in.voltagePu > setPu;
            return timed(cond, st, delayS, dtS);
        }
    }

    /** 32R: P < −setPu·S_ном (паровая турбина в двигательном режиме, §7.5.2). */
    record ReversePower(ProtectionFunction function, RelayGeneration generation, double setPu, double delayS)
            implements ProtectionRelay {
        public ReversePower {
            check(function, generation, ProtectionFunction.F32R);
            if (!(setPu > 0 && delayS >= 0)) throw new IllegalArgumentException("settings");
        }

        /** −2 % P_ном, 3 с. */
        public static ReversePower standard(RelayGeneration g) {
            return new ReversePower(ProtectionFunction.F32R, g, 0.02, 3);
        }

        @Override
        public boolean evaluate(RelayInputs in, RelayState st, double dtS) {
            return timed(in.powerPu < -setPu, st, delayS, dtS);
        }
    }

    /**
     * 40, характеристика Мейсона: окружность диаметром {@code diameterPu}, смещённая вниз по оси −jX на
     * {@code offsetPu} (о.е. на S_ном машины).
     */
    record LossOfField(ProtectionFunction function, RelayGeneration generation, double offsetPu, double diameterPu,
            double delayS) implements ProtectionRelay {
        public LossOfField {
            check(function, generation, ProtectionFunction.F40);
            needsStatic(generation, "loss-of-field protection");
            if (!(offsetPu >= 0 && diameterPu > 0 && delayS >= 0)) throw new IllegalArgumentException("settings");
        }

        /** Смещение x'_d/2, диаметр x_d, выдержка {@code grid.lof_trip_delay_s}. */
        public static LossOfField mason(RelayGeneration g, MachineParams p, GridConfig cfg) {
            return new LossOfField(ProtectionFunction.F40, g, p.xdP() / 2, p.xd(), cfg.lofTripDelayS());
        }

        @Override
        public boolean evaluate(RelayInputs in, RelayState st, double dtS) {
            double r = diameterPu / 2;
            boolean inside = Double.isFinite(in.zRe) && Math.hypot(in.zRe, in.zIm + offsetPu + r) <= r;
            return timed(inside, st, delayS, dtS);
        }
    }

    /** 78: счёт проворотов с момента установки реле. */
    record OutOfStep(ProtectionFunction function, RelayGeneration generation, int slipsToTrip)
            implements ProtectionRelay {
        public OutOfStep {
            check(function, generation, ProtectionFunction.F78);
            needsStatic(generation, "out-of-step protection");
            if (slipsToTrip < 1) throw new IllegalArgumentException("slipsToTrip");
        }

        /** 2 проворота → отключение. */
        public static OutOfStep standard(RelayGeneration g) {
            return new OutOfStep(ProtectionFunction.F78, g, 2);
        }

        @Override
        public boolean evaluate(RelayInputs in, RelayState st, double dtS) {
            return in.poleSlips - st.bias >= slipsToTrip;
        }
    }

    /**
     * Контроль синхронизма 25 (§7.3.5): не отключает, а разрешает включение выключателя — окно IEEE C50.13;
     * на обесточенную сторону — только если {@code allowDeadBus}.
     */
    record SyncCheck(ProtectionFunction function, RelayGeneration generation, double maxAngleRad, double maxDvPu,
            double maxSlipHz, boolean allowDeadBus) implements ProtectionRelay {
        public SyncCheck {
            check(function, generation, ProtectionFunction.F25);
            if (!(maxAngleRad > 0 && maxAngleRad < Math.PI && maxDvPu > 0 && maxSlipHz > 0))
                throw new IllegalArgumentException("window");
        }

        /** Окно {@code grid.sync_max_*}, включение на обесточенную шину разрешено. */
        public static SyncCheck of(RelayGeneration g, GridConfig cfg) {
            return new SyncCheck(ProtectionFunction.F25, g, Math.toRadians(cfg.syncMaxAngleDeg()), cfg.syncMaxDv(),
                    cfg.syncMaxSlipHz(), true);
        }

        public boolean permits(SyncScope scope, boolean deadSide) {
            return deadSide ? allowDeadBus : scope.inWindow(maxAngleRad, maxDvPu, maxSlipHz);
        }

        @Override
        public boolean evaluate(RelayInputs in, RelayState st, double dtS) {
            return false;
        }
    }

    /** УРОВ 50BF (§7.5.5): команда отключения подана, а ток не исчез за выдержку — отключить смежные. */
    record BreakerFailure(ProtectionFunction function, RelayGeneration generation, double delayS)
            implements ProtectionRelay {
        /** Ток, ниже которого выключатель считается отключившим. */
        static final double CURRENT_DETECTOR_A = 1;

        public BreakerFailure {
            check(function, generation, ProtectionFunction.F50BF);
            if (!(delayS >= 0)) throw new IllegalArgumentException("delayS");
        }

        /** t_УРОВ = 0,25 с. */
        public static BreakerFailure standard(RelayGeneration g) {
            return new BreakerFailure(ProtectionFunction.F50BF, g, 0.25);
        }

        @Override
        public boolean evaluate(RelayInputs in, RelayState st, double dtS) {
            return timed(in.tripPending && in.currentA > CURRENT_DETECTOR_A, st, delayS, dtS);
        }
    }

    /**
     * Газовое реле 63 (§7.4.5): дуга внутри бака — ступень 2 без выдержки. Накопление газа и сигнальная
     * ступень ({@code alarmGasMl}) — с диагностикой масла (B).
     */
    record Buchholz(ProtectionFunction function, RelayGeneration generation, double alarmGasMl)
            implements ProtectionRelay {
        public Buchholz {
            check(function, generation, ProtectionFunction.F63);
            if (!(alarmGasMl > 0)) throw new IllegalArgumentException("alarmGasMl");
        }

        public static Buchholz standard() {
            return new Buchholz(ProtectionFunction.F63, RelayGeneration.A1_ELECTROMECHANICAL, 200);
        }

        @Override
        public boolean evaluate(RelayInputs in, RelayState st, double dtS) {
            return in.internalArc;
        }
    }

    /** Тепловая защита 49 по температуре наиболее нагретой точки (модель §7.4.4, провод §7.4.1, двигатель). */
    record Thermal(ProtectionFunction function, RelayGeneration generation, double tripTemperatureC)
            implements ProtectionRelay {
        public Thermal {
            check(function, generation, ProtectionFunction.F49);
            if (!(tripTemperatureC > 0)) throw new IllegalArgumentException("tripTemperatureC");
        }

        /** θ_h > 140 °C — отключение. */
        public static Thermal standard(RelayGeneration g) {
            return new Thermal(ProtectionFunction.F49, g, 140);
        }

        @Override
        public boolean evaluate(RelayInputs in, RelayState st, double dtS) {
            return in.hotSpotC > tripTemperatureC;
        }
    }

    /**
     * АПВ 79 (§7.5.5): после отключения защитой — бестоковая пауза и включение (через реле 25, если оно
     * стоит). Пока идёт время готовности, защиты присоединения ускорены; повторное отключение, когда
     * циклы кончились, — запрет до включения оператором.
     */
    record AutoReclose(ProtectionFunction function, RelayGeneration generation, double deadTimeS, int shots)
            implements ProtectionRelay {
        /** Время готовности после включения [BALANCE]. */
        static final double RECLAIM_S = 10;
        static final int IDLE = 0, DEAD = 1, RECLAIM = 2, LOCKOUT = 3;

        public AutoReclose {
            check(function, generation, ProtectionFunction.F79);
            if (!(deadTimeS > 0) || shots < 1) throw new IllegalArgumentException("settings");
        }

        /** 1 цикл, 1,0 с. */
        public static AutoReclose standard(RelayGeneration g) {
            return new AutoReclose(ProtectionFunction.F79, g, 1.0, 1);
        }

        /** Защиты присоединения ускорены: идёт время готовности после включения от АПВ. */
        static boolean accelerating(RelayState st) {
            return st.phase == RECLAIM;
        }

        /** АПВ израсходовано, ждёт оператора. */
        public static boolean lockedOut(RelayState st) {
            return st.phase == LOCKOUT;
        }

        @Override
        public boolean evaluate(RelayInputs in, RelayState st, double dtS) {
            boolean tripped = in.breakerOpen && in.protectionTripped;
            switch (st.phase) {
                case DEAD -> {
                    if (!in.breakerOpen) {
                        st.phase = IDLE;
                        return false;
                    }
                    st.timer += dtS;
                    if (st.timer < deadTimeS - EPS) return false;
                    st.count++;
                    st.phase = RECLAIM;
                    st.timer = 0;
                    return true;
                }
                case LOCKOUT -> {
                    if (!in.breakerOpen) {
                        st.phase = IDLE;
                        st.count = 0;
                    }
                    return false;
                }
                default -> {
                    if (tripped) {
                        st.timer = 0;
                        st.phase = st.count < shots ? DEAD : LOCKOUT;
                        return false;
                    }
                    if (st.phase == RECLAIM) {
                        st.timer += dtS;
                        if (st.timer >= RECLAIM_S - EPS) {
                            st.phase = IDLE;
                            st.count = 0;
                        }
                    }
                    return false;
                }
            }
        }
    }

    /**
     * Очередь АЧР с ЧАПВ (§7.5.6): f ниже уставки дольше выдержки — нагрузка отключается и остаётся
     * отключённой, пока f не продержится не ниже {@code reconnectHz} {@code reconnectDelayS}; NaN — без
     * ЧАПВ. Выход — «нагрузка отключена».
     */
    record Ufls(ProtectionFunction function, RelayGeneration generation, UflsStage stage, double setHz, double delayS,
            double reconnectHz, double reconnectDelayS) implements ProtectionRelay {
        /** ЧАПВ, Гц (§7.5.6). */
        public static final double RECONNECT_HZ = 49.7;

        public Ufls {
            check(function, generation, ProtectionFunction.UFLS);
            Objects.requireNonNull(stage, "stage");
            if (stage == UflsStage.NONE) throw new IllegalArgumentException("stage");
            if (!(setHz > 0 && delayS >= 0)) throw new IllegalArgumentException("settings");
            if (!Double.isNaN(reconnectHz) && !(reconnectHz > setHz && reconnectDelayS >= 0))
                throw new IllegalArgumentException("reconnect");
        }

        /**
         * Очередь по таблице §7.5.6 [сверить со стандартом СО ЕЭС]; ЧАПВ на 49,7 Гц с выдержками,
         * разнесёнными по очередям от 10 до 60 с.
         */
        public static Ufls standard(UflsStage stage, RelayGeneration g) {
            double set = switch (stage) {
                case ACHR1_1 -> 48.8;
                case ACHR1_2 -> 48.6;
                case ACHR1_3 -> 48.4;
                case ACHR1_4 -> 48.2;
                case ACHR1_5 -> 48.0;
                case ACHR2_1, ACHR2_2 -> 49.0;
                case NONE -> throw new IllegalArgumentException("stage");
            };
            double delay = stage == UflsStage.ACHR2_1 ? 10 : stage == UflsStage.ACHR2_2 ? 20 : 0.2;
            double reconnect = 10 + (stage.ordinal() - 1) * 50.0 / 6;
            return new Ufls(ProtectionFunction.UFLS, g, stage, set, delay, RECONNECT_HZ, reconnect);
        }

        @Override
        public boolean evaluate(RelayInputs in, RelayState st, double dtS) {
            boolean live = in.voltagePu >= UNDERVOLTAGE_BLOCK_PU;
            if (!st.latched) {
                if (timed(live && in.frequencyHz < setHz + st.bias, st, delayS, dtS)) {
                    st.latched = true;
                    st.timer = 0;
                }
            } else if (!Double.isNaN(reconnectHz)
                    && timed(live && in.frequencyHz >= reconnectHz + st.bias, st, reconnectDelayS, dtS)) {
                st.latched = false;
                st.timer = 0;
            }
            return st.latched;
        }
    }
}
