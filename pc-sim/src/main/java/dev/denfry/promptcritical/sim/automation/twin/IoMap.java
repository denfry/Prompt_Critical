package dev.denfry.promptcritical.sim.automation.twin;

import java.util.List;

/**
 * Привязка ввода-вывода ПЛК двойника к установке (03d §3d.9.1, таблица проверки ввода-вывода FAT). Аналоговые
 * каналы — унифицированный сигнал 0–10 В, как у модулей A2 в мире: {@code lo..hi} единиц сигнала ↔ 0..10 В.
 *
 * <p>Дискретный выход управляет механизмом либо командой ({@link DoMode#COMMAND}: включён — {@code on}, выключен —
 * {@code off}; несколько выходов на один механизм складываются), либо разрешением ({@link DoMode#PERMISSIVE}: снятое
 * разрешение переводит механизм в {@code off}, что бы ни командовал оператор). Механизм без команды ПЛК в режиме RUN
 * слушается оператора.
 */
public record IoMap(List<Di> di, List<Ai> ai, List<Do> dout, List<Ao> ao) {
    public static final double SIGNAL_V = 10;
    public static final IoMap NONE = new IoMap(List.of(), List.of(), List.of(), List.of());

    public enum Cmp { GT, LT }

    public enum DoMode { COMMAND, PERMISSIVE }

    /** Дискретный вход {@code %IX0.ch}: сигнал больше (меньше) порога. */
    public record Di(int ch, String signal, Cmp cmp, double threshold) {
        boolean eval(double v) {
            return !Double.isNaN(v) && (cmp == Cmp.GT ? v > threshold : v < threshold);
        }
    }

    /** Аналоговый вход {@code %IWch}: {@code lo..hi} → 0..10 В. Обрыв линии — 0 В. */
    public record Ai(int ch, String signal, double lo, double hi) {
        double volts(double v) {
            if (Double.isNaN(v)) return 0;
            return Math.max(0, Math.min(SIGNAL_V, (v - lo) / (hi - lo) * SIGNAL_V));
        }
    }

    public record Do(int ch, String actuator, DoMode mode, double on, double off) {
    }

    /** Аналоговый выход {@code %QWch}: 0..10 В → {@code lo..hi} положения механизма. */
    public record Ao(int ch, String actuator, double lo, double hi) {
        double position(double volts) {
            return lo + Math.max(0, Math.min(SIGNAL_V, volts)) / SIGNAL_V * (hi - lo);
        }
    }

    /** Механизм получает команду от ПЛК (а не от оператора). */
    public boolean commands(String actuator) {
        for (Do d : dout) if (d.mode == DoMode.COMMAND && d.actuator.equals(actuator)) return true;
        for (Ao a : ao) if (a.actuator.equals(actuator)) return true;
        return false;
    }

    /** Таблица привязок для щита и периферии: «%IX0.0 ← lsh &gt; 0,5». */
    public List<String> describe() {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (Di d : di) out.add("%IX0." + d.ch + " <- " + d.signal + (d.cmp == Cmp.GT ? " > " : " < ") + fmt(d.threshold));
        for (Ai a : ai) out.add("%IW" + a.ch + " <- " + a.signal + " " + fmt(a.lo) + ".." + fmt(a.hi));
        for (Do d : dout) out.add("%QX0." + d.ch + " -> " + d.actuator + (d.mode == DoMode.PERMISSIVE ? " permissive" : " = " + fmt(d.on)));
        for (Ao a : ao) out.add("%QW" + a.ch + " -> " + a.actuator + " " + fmt(a.lo) + ".." + fmt(a.hi));
        return out;
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v);
    }
}
