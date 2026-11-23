package dev.denfry.promptcritical.sim.grid;

import dev.denfry.promptcritical.sim.core.StateWriter;
import java.util.Objects;

/**
 * Преобразователь сети 07 и Forge Energy другого мода, {@code grid_fe_interface} (07 §7.7.1, R18). Энергия FE-стороны
 * живёт в буфере, Дж (1 с номинальной мощности). IMPORT — нагрузка постоянной мощности из сети в буфер с КПД η;
 * EXPORT — источник, ведомый сетью: отдаёт из буфера только при напряжении и частоте в окне, сеть не формирует,
 * инерции не даёт и при выходе из окна отключается за {@link #ANTI_ISLANDING_S} (защита от островного режима, IEEE
 * 1547); AUTO держит буфер наполовину полным. Вне окна интерфейс не работает ни в одну сторону.
 */
public final class FeInterface {
    /** Отключение при выходе из окна, с (07 §7.7.1). */
    public static final double ANTI_ISLANDING_S = 0.2;
    /** Обратное включение: напряжение и частота держатся в окне столько секунд [моё; IEEE 1547 даёт до 300 с]. */
    public static final double RECONNECT_S = 5;
    /** Уровень буфера, который держит AUTO. */
    public static final double AUTO_LEVEL = 0.5;

    public enum Mode { IMPORT, EXPORT, AUTO }

    /**
     * Паспорт: номинал, КПД и окно работы (ключи {@code grid.fe_interface_*}, {@code grid.fe_window_*}).
     *
     * @param ratedW номинальная мощность, Вт; буфер — одна секунда её
     */
    public record Spec(double ratedW, double efficiency, double fMinHz, double fMaxHz, double vMinPu, double vMaxPu) {
        public Spec {
            if (!(ratedW > 0) || !(efficiency > 0 && efficiency <= 1) || !(fMaxHz > fMinHz) || !(vMaxPu > vMinPu)) {
                throw new IllegalArgumentException("bad fe interface spec");
            }
        }

        /** Значения 07 §7.7.1: КПД 0,97, окно 49–51 Гц и 0,9–1,1 о.е. */
        public static Spec standard(double ratedW) {
            return new Spec(ratedW, 0.97, 49, 51, 0.9, 1.1);
        }

        public double bufferJ() {
            return ratedW;
        }
    }

    private Spec spec;
    private Mode mode = Mode.AUTO;
    private double setpointW;
    private double bufferJ;
    private boolean connected;
    /** Сколько секунд напряжение и частота вне окна (для отключения) или в окне (для обратного включения). */
    private double outS, inS;
    private double gridW;

    public FeInterface(Spec spec) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.setpointW = spec.ratedW();
    }

    public Spec spec() {
        return spec;
    }

    /** Новый паспорт (смена ключей конфигурации): буфер обрезается по новому номиналу. */
    public void respec(Spec s) {
        spec = Objects.requireNonNull(s, "spec");
        bufferJ = Math.min(bufferJ, s.bufferJ());
        setpointW = Math.min(setpointW, s.ratedW());
    }

    public Mode mode() {
        return mode;
    }

    public void setMode(Mode m) {
        mode = Objects.requireNonNull(m, "mode");
    }

    public double setpointW() {
        return setpointW;
    }

    /** Заданная мощность, Вт: от 0 до номинала. */
    public void setSetpointW(double w) {
        setpointW = Double.isFinite(w) ? Math.max(0, Math.min(spec.ratedW(), w)) : 0;
    }

    public double bufferJ() {
        return bufferJ;
    }

    public double fill() {
        return bufferJ / spec.bufferJ();
    }

    /** Интерфейс на сети: напряжение и частота в окне, выдержка обратного включения прошла. */
    public boolean connected() {
        return connected;
    }

    /** Мощность из сети в последний шаг, Вт: плюс — импорт в FE, минус — экспорт в сеть. */
    public double gridW() {
        return gridW;
    }

    /** Принять FE-энергию другого мода в буфер, Дж; возвращает, сколько принято. */
    public double receive(double joules, boolean simulate) {
        if (mode == Mode.IMPORT || !(joules > 0)) return 0;
        double take = Math.min(joules, spec.bufferJ() - bufferJ);
        if (!simulate) bufferJ += take;
        return take;
    }

    /** Отдать FE-энергию из буфера другому моду, Дж; возвращает, сколько отдано. */
    public double extract(double joules, boolean simulate) {
        if (mode == Mode.EXPORT || !(joules > 0)) return 0;
        double give = Math.min(joules, bufferJ);
        if (!simulate) bufferJ -= give;
        return give;
    }

    /**
     * Шаг: окно по напряжению {@code vPu} и частоте {@code fHz} на шине, затем обмен с сетью.
     *
     * @return мощность из сети, Вт (минус — в сеть)
     */
    public double step(double dt, double vPu, double fHz) {
        if (!(dt > 0)) throw new IllegalArgumentException("dt must be positive");
        boolean inWindow = vPu >= spec.vMinPu() && vPu <= spec.vMaxPu() && fHz >= spec.fMinHz() && fHz <= spec.fMaxHz();
        if (inWindow) {
            outS = 0;
            inS += dt;
            if (!connected && inS >= RECONNECT_S) connected = true;
        } else {
            inS = 0;
            outS += dt;
            if (connected && outS >= ANTI_ISLANDING_S) connected = false;
        }
        gridW = connected ? exchange(dt) : 0;
        return gridW;
    }

    private double exchange(double dt) {
        double eta = spec.efficiency();
        double room = spec.bufferJ() - bufferJ;
        boolean importNow = switch (mode) {
            case IMPORT -> true;
            case EXPORT -> false;
            case AUTO -> fill() < AUTO_LEVEL;
        };
        if (importNow) {
            double want = mode == Mode.AUTO ? (AUTO_LEVEL * spec.bufferJ() - bufferJ) : room;
            double p = Math.min(setpointW, want / (eta * dt));
            p = Math.max(0, p);
            bufferJ += p * eta * dt;
            return p;
        }
        double avail = mode == Mode.AUTO ? bufferJ - AUTO_LEVEL * spec.bufferJ() : bufferJ;
        double p = Math.max(0, Math.min(setpointW, avail * eta / dt));
        bufferJ -= p / eta * dt;
        return -p;
    }

    /** Восстановить буфер и состояние подключения после загрузки мира. */
    public void restore(double bufferJ, boolean connected, double inS) {
        this.bufferJ = Double.isFinite(bufferJ) ? Math.max(0, Math.min(spec.bufferJ(), bufferJ)) : 0;
        this.connected = connected;
        this.inS = Double.isFinite(inS) ? Math.max(0, inS) : 0;
    }

    public void save(StateWriter out) {
        out.putString("mode", mode.name());
        out.putDouble("set", setpointW);
        out.putDouble("buffer", bufferJ);
        out.putBoolean("on", connected);
        out.putDouble("in_s", inS);
    }
}
