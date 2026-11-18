package dev.denfry.promptcritical.sim.telemetry;

/**
 * Адресный доступ в духе Modbus (03b §3b.4.5). Input registers: 0…999 — измерение {@code i}-го тега, масштабированное
 * в 16 бит по шкале прибора, {@code r = round(65535·(y − lo)/(hi − lo))} с насыщением; 1000…2999 — тот же тег в
 * float32, два регистра на тег, старшее слово первым. Нумерация с нуля.
 */
public final class ModbusMap {
    public static final int SCALED_BASE = 0, SCALED_COUNT = 1000, FLOAT_BASE = 1000, FLOAT_COUNT = 2000;
    /** Регистров за один запрос (как у Modbus: 125 на чтение). */
    public static final int MAX_REGISTERS = 125;
    /** Дискретов за один запрос (Modbus: 2000). */
    public static final int MAX_BITS = 2000;
    /** Признак качества тега {@code i} — дискретный вход 1000 + i ({@code true} = не GOOD). */
    public static final int QUALITY_BASE = 1000;

    private ModbusMap() {
    }

    /** Масштабирование в 16 бит; NaN — 0, шкала без размаха — 0. */
    public static int scale16(double y, double lo, double hi) {
        if (Double.isNaN(y) || !(hi > lo)) return 0;
        double r = Math.rint(65535 * (y - lo) / (hi - lo));
        return (int) Math.max(0, Math.min(65535, r));
    }

    /** Слово float32: {@code high = true} — старшие 16 бит. */
    public static int floatWord(double y, boolean high) {
        int bits = Float.floatToIntBits((float) y);
        return high ? bits >>> 16 : bits & 0xFFFF;
    }

    /** Значение регистра {@code addr} по тегам {@code values} со шкалами {@code lo}/{@code hi}; вне карты — 0. */
    public static int inputRegister(int addr, double[] values, double[] lo, double[] hi) {
        if (addr >= SCALED_BASE && addr < SCALED_BASE + SCALED_COUNT) {
            int i = addr - SCALED_BASE;
            return i < values.length ? scale16(values[i], lo[i], hi[i]) : 0;
        }
        if (addr >= FLOAT_BASE && addr < FLOAT_BASE + FLOAT_COUNT) {
            int i = (addr - FLOAT_BASE) / 2;
            return i < values.length ? floatWord(values[i], (addr - FLOAT_BASE) % 2 == 0) : 0;
        }
        return 0;
    }
}
