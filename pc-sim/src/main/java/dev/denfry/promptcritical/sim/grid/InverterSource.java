package dev.denfry.promptcritical.sim.grid;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Инверторный источник (15 §15.9): управляемый источник тока, ведомый сетью (grid-following). Им пользуются солнечный
 * и батарейный инверторы. Окно по напряжению и частоте, отключение за {@value FeInterface#ANTI_ISLANDING_S} с и
 * повторное включение через {@value FeInterface#RECONNECT_S} с — как у экспорта {@code grid_fe_interface} (07 §7.7.1).
 *
 * <ul>
 *   <li>Мощность идёт к заданию с постоянной {@value #RESPONSE_S} с, после включения — с набором за
 *       {@value FeInterface#RAMP_S} с.</li>
 *   <li>Ток ограничен {@value #CURRENT_LIMIT_PU} номинала: на просевшей шине мощность меньше.</li>
 *   <li>Синтетическая инерция: ΔP = −2·H·S·df/dt, H = 0–5 с задаёт игрок (§15.9.3). Энергию на неё даёт источник за
 *       инвертором, поэтому вверх она не больше доступной мощности.</li>
 *   <li>Инерции без синтетической нет, остров без синхронных машин он не держит.</li>
 *   <li>Снижение мощности по частоте (LFSM-O/LFSM-U, EN 50549-1 §4.6, статизм 5 %): выше {@value #F_LFSM_O_HZ} Гц
 *       отдача падает на {@value #LFSM_PER_HZ} задания на 1 Гц, ниже {@value #F_LFSM_U_HZ} Гц так же падает заряд.
 *       Без этого разряд на острове разгружает генератор, частота уходит за окно, инвертор отключается и снова
 *       включается по кругу.</li>
 * </ul>
 */
public final class InverterSource {
    public static final double CURRENT_LIMIT_PU = 1.15;
    public static final double RESPONSE_S = 0.2;
    public static final double MAX_SYNTHETIC_H_S = 5;
    public static final double F_MIN_HZ = 49, F_MAX_HZ = 51, V_MIN_PU = 0.9, V_MAX_PU = 1.1;
    /** Пороги и наклон снижения мощности по частоте: статизм 5 % от 50 Гц — 40 % задания на 1 Гц. */
    public static final double F_LFSM_O_HZ = 50.2, F_LFSM_U_HZ = 49.8, LFSM_PER_HZ = 0.4;

    private final double ratedW;
    private final double efficiency;
    private double syntheticH;
    private boolean connected;
    private double inS, outS, onS;
    private double powerW;
    private double lastF = Double.NaN;

    public InverterSource(double ratedW, double efficiency) {
        if (!(ratedW > 0)) throw new IllegalArgumentException("ratedW");
        this.ratedW = ratedW;
        this.efficiency = efficiency;
    }

    public double ratedW() {
        return ratedW;
    }

    public double efficiency() {
        return efficiency;
    }

    /** Сколько секунд шина подряд в окне. */
    public double secondsInWindow() {
        return inS;
    }

    public boolean connected() {
        return connected;
    }

    /** Мощность в сеть на стороне переменного тока, Вт (минус — из сети, заряд батареи). */
    public double powerW() {
        return powerW;
    }

    public double syntheticH() {
        return syntheticH;
    }

    public void setSyntheticH(double h) {
        syntheticH = Double.isFinite(h) ? Math.max(0, Math.min(MAX_SYNTHETIC_H_S, h)) : 0;
    }

    /**
     * Шаг: {@code demandW} — сколько отдать в сеть по заданию (минус — взять из сети), {@code maxOutW} и
     * {@code maxInW} — сколько может отдать и принять источник за инвертором на стороне переменного тока.
     *
     * @return мощность в сеть, Вт
     */
    private int windowResets;
    private double lastOutV = Double.NaN, lastOutF = Double.NaN;

    /** Отладка: сколько раз выдержка в окне сбрасывалась и чем (напряжение, о. е.; частота, Гц). */
    public String windowDebug() {
        return windowResets + " resets, last at " + lastOutV + " pu " + lastOutF + " Hz";
    }

    public double step(double dt, double vPu, double fHz, double demandW, double maxOutW, double maxInW) {
        if (!(dt > 0)) throw new IllegalArgumentException("dt must be positive");
        boolean inWindow = vPu >= V_MIN_PU && vPu <= V_MAX_PU && fHz >= F_MIN_HZ && fHz <= F_MAX_HZ;
        if (inWindow) {
            outS = 0;
            inS += dt;
            if (!connected && inS >= FeInterface.RECONNECT_S) {
                connected = true;
                onS = 0;
            }
        } else {
            if (inS > 0) {
                windowResets++;
                lastOutV = vPu;
                lastOutF = fHz;
            }
            inS = 0;
            outS += dt;
            if (connected && outS >= FeInterface.ANTI_ISLANDING_S) connected = false;
        }
        double dfdt = Double.isFinite(lastF) && fHz > 0 && lastF > 0 ? (fHz - lastF) / dt : 0;
        lastF = fHz;
        if (!connected) {
            powerW = 0;
            return 0;
        }
        onS += dt;
        double target = demandW * frequencyFactor(demandW, fHz) - 2 * syntheticH * ratedW * dfdt / PowerBoard.NOMINAL_HZ;
        double limit = CURRENT_LIMIT_PU * ratedW * Math.max(0, Math.min(1, vPu)) * Math.min(1, onS / FeInterface.RAMP_S);
        target = Math.max(-Math.min(limit, Math.max(0, maxInW)), Math.min(Math.min(limit, Math.max(0, maxOutW)), target));
        powerW += (target - powerW) * Math.min(1, dt / RESPONSE_S);
        return powerW;
    }

    /** Доля задания по частоте: отдачу режет высокая частота, заряд — низкая (LFSM-O/LFSM-U). */
    public static double frequencyFactor(double demandW, double fHz) {
        double over = demandW > 0 ? fHz - F_LFSM_O_HZ : demandW < 0 ? F_LFSM_U_HZ - fHz : 0;
        return over > 0 ? Math.max(0, 1 - LFSM_PER_HZ * over) : 1;
    }

    public void save(StateWriter out) {
        new Carry(syntheticH, connected, inS, onS, powerW).write(out);
    }

    /** Состояние из сохранения: перенос до первого паспорта от мира. */
    public record Carry(double h, boolean on, double inS, double onS, double p) {
        public static Carry read(StateReader in) {
            return new Carry(in.optDouble("h", 0), in.has("on") && in.getBoolean("on"), in.optDouble("in_s", 0),
                    in.optDouble("on_s", 0), in.optDouble("p", 0));
        }

        public void write(StateWriter out) {
            out.putDouble("h", h);
            out.putBoolean("on", on);
            out.putDouble("in_s", inS);
            out.putDouble("on_s", onS);
            out.putDouble("p", p);
        }
    }

    public void restore(Carry c) {
        setSyntheticH(c.h());
        connected = c.on();
        inS = Double.isFinite(c.inS()) ? Math.max(0, c.inS()) : 0;
        onS = Double.isFinite(c.onS()) ? Math.max(0, c.onS()) : 0;
        powerW = Double.isFinite(c.p()) ? c.p() : 0;
        lastF = Double.NaN;
    }
}
