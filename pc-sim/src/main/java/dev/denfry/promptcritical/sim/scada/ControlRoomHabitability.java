package dev.denfry.promptcritical.sim.scada;

/**
 * Обитаемость блочного щита управления (03c §3c.9.1): по мощности дозы, температуре воздуха, дыму и питанию приборов.
 * Худший из параметров задаёт состояние. Обитаемость пульт не выключает: он работает, но люди в нём получают дозу и
 * тепловой урон — ради этого и существует резервный щит.
 *
 * <p>Пороги 03c: доза DEGRADED — {@value #DOSE_DEGRADED_SV_S} Зв/с (≈ 69 мкЗв/ч: 50 мЗв за 30 сут аварии, 10 CFR 50
 * прил. A GDC 19, {@code scada.mcr_dose_degraded_sv_s}), UNINHABITABLE — {@value #DOSE_UNINHABITABLE_SV_S} Зв/с
 * (10 мЗв/ч, [BALANCE] {@code scada.mcr_dose_uninhabitable_sv_s}); воздух — 323 и 333 K; дым — 1 и 4 очага огня в
 * объёме или на его границе; питание — часть и все каналы потеряны.
 */
public final class ControlRoomHabitability {
    public static final double DOSE_DEGRADED_SV_S = 1.93e-8, DOSE_UNINHABITABLE_SV_S = 2.78e-6, TEMP_DEGRADED_K = 323,
            TEMP_UNINHABITABLE_K = 333;
    public static final int FIRES_DEGRADED = 1, FIRES_UNINHABITABLE = 4;

    public enum State { NORMAL, DEGRADED, UNINHABITABLE }

    /** Что сделало щит необитаемым или ухудшило его: для подписи на пульте. */
    public enum Cause { NONE, DOSE, TEMPERATURE, SMOKE, POWER }

    /** Итог: состояние и худший параметр. */
    public record Result(State state, Cause cause) {
    }

    private ControlRoomHabitability() {
    }

    /**
     * @param doseSvPerS   мощность дозы, среднее по 9 точкам объёма, Зв/с
     * @param airK         температура воздуха, K (NaN — неизвестна, не учитывается)
     * @param fires        очагов огня в объёме и на границе
     * @param channelsLost доля потерянных каналов питания приборов 0–1 (NaN — не учитывается)
     */
    public static Result assess(double doseSvPerS, double airK, int fires, double channelsLost) {
        State s = State.NORMAL;
        Cause c = Cause.NONE;
        Result r;
        r = worse(s, c, level(doseSvPerS, DOSE_DEGRADED_SV_S, DOSE_UNINHABITABLE_SV_S), Cause.DOSE);
        s = r.state;
        c = r.cause;
        r = worse(s, c, level(airK, TEMP_DEGRADED_K, TEMP_UNINHABITABLE_K), Cause.TEMPERATURE);
        s = r.state;
        c = r.cause;
        r = worse(s, c, fires >= FIRES_UNINHABITABLE ? State.UNINHABITABLE : fires >= FIRES_DEGRADED ? State.DEGRADED : State.NORMAL, Cause.SMOKE);
        s = r.state;
        c = r.cause;
        State power = Double.isNaN(channelsLost) || channelsLost <= 0 ? State.NORMAL : channelsLost >= 1 ? State.UNINHABITABLE : State.DEGRADED;
        return worse(s, c, power, Cause.POWER);
    }

    private static State level(double v, double degraded, double uninhabitable) {
        if (Double.isNaN(v)) return State.NORMAL;
        return v > uninhabitable ? State.UNINHABITABLE : v > degraded ? State.DEGRADED : State.NORMAL;
    }

    private static Result worse(State s, Cause c, State candidate, Cause why) {
        return candidate.ordinal() > s.ordinal() ? new Result(candidate, why) : new Result(s, c);
    }
}
