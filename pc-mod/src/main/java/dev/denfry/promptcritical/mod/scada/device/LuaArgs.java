package dev.denfry.promptcritical.mod.scada.device;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Проверка аргументов из Lua (03b §3b.1.7: {@code E_ARG} — неверный тип, {@code E_RANGE} — вне диапазона). Аргументы
 * приходят уже переведёнными CC в Java: числа — {@code Double}, строки, логические, таблицы — {@code Map}. Ни одно
 * значение не используется без проверки типа, конечности и границ.
 */
public final class LuaArgs {
    /** Строки длиннее этого Lua не передаёт: имена тегов и команд короткие. */
    public static final int MAX_STRING = 128;

    private LuaArgs() {
    }

    private static Object at(Object[] a, int i) {
        return i < a.length ? a[i] : null;
    }

    public static DeviceException arg(int i, String expected) {
        return new DeviceException(DeviceException.ARG, "bad argument #" + (i + 1) + " (" + expected + " expected)");
    }

    public static String string(Object[] a, int i) throws DeviceException {
        if (!(at(a, i) instanceof String s)) throw arg(i, "string");
        if (s.length() > MAX_STRING) throw new DeviceException(DeviceException.RANGE, "argument #" + (i + 1) + " longer than " + MAX_STRING);
        return s;
    }

    /** Число, допускающее ±бесконечность, но не NaN. */
    public static double number(Object[] a, int i) throws DeviceException {
        if (!(at(a, i) instanceof Number n)) throw arg(i, "number");
        double v = n.doubleValue();
        if (Double.isNaN(v)) throw new DeviceException(DeviceException.RANGE, "argument #" + (i + 1) + " is NaN");
        return v;
    }

    public static double finite(Object[] a, int i) throws DeviceException {
        double v = number(a, i);
        if (!Double.isFinite(v)) throw new DeviceException(DeviceException.RANGE, "argument #" + (i + 1) + " is not finite");
        return v;
    }

    public static double optFinite(Object[] a, int i, double def) throws DeviceException {
        return at(a, i) == null ? def : finite(a, i);
    }

    public static int integer(Object[] a, int i, int min, int max) throws DeviceException {
        double v = finite(a, i);
        if (v != Math.rint(v)) throw arg(i, "integer");
        if (v < min || v > max) throw new DeviceException(DeviceException.RANGE, "argument #" + (i + 1) + " outside [" + min + ", " + max + "]");
        return (int) v;
    }

    public static long ticket(Object[] a, int i) throws DeviceException {
        double v = finite(a, i);
        if (v != Math.rint(v) || v < 1 || v > 1e15) throw arg(i, "ticket");
        return (long) v;
    }

    public static boolean bool(Object[] a, int i) throws DeviceException {
        if (!(at(a, i) instanceof Boolean b)) throw arg(i, "boolean");
        return b;
    }

    /** Список строк Lua {@code {"a", "b"}} не длиннее {@code max}; ключи — 1…n без дыр. */
    public static List<String> strings(Object[] a, int i, int max) throws DeviceException {
        if (!(at(a, i) instanceof Map<?, ?> m)) throw arg(i, "table");
        if (m.size() > max) throw new DeviceException(DeviceException.RANGE, "more than " + max + " entries");
        List<String> out = new ArrayList<>(m.size());
        for (int k = 1; k <= m.size(); k++) {
            Object v = m.get((double) k);
            if (!(v instanceof String s)) throw arg(i, "list of strings");
            if (s.length() > MAX_STRING) throw new DeviceException(DeviceException.RANGE, "entry " + k + " longer than " + MAX_STRING);
            out.add(s);
        }
        return out;
    }
}
