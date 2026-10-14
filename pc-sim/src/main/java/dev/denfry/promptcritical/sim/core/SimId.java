package dev.denfry.promptcritical.sim.core;

/**
 * Устойчивый идентификатор доменного объекта (05 §5.2.5): 16 бит — вид, 48 бит — порядковый номер.
 * Номера не переиспользуются; {@link #NONE} (сырое значение 0) — «нет объекта», выдача начинается с 1.
 */
public record SimId(long raw) implements Comparable<SimId> {
    public static final SimId NONE = new SimId(0L);

    private static final long SERIAL_MASK = 0xFFFF_FFFF_FFFFL;
    private static final Kind[] KINDS = Kind.values();

    public static SimId of(Kind kind, long serial) {
        if (serial < 0 || serial > SERIAL_MASK) throw new IllegalArgumentException("serial out of 48 bits: " + serial);
        return new SimId(((long) kind.ordinal() << 48) | serial);
    }

    public Kind kind() {
        return KINDS[(int) (raw >>> 48)];
    }

    public long serial() {
        return raw & SERIAL_MASK;
    }

    public boolean isNone() {
        return raw == 0L;
    }

    public String hex() {
        return String.format("%016x", raw);
    }

    @Override
    public int compareTo(SimId o) {
        return Long.compareUnsigned(raw, o.raw);
    }

    /** Порядок констант — часть формата сохранения: новые виды только дописываются в конец. */
    public enum Kind {
        STATION, REACTOR, FLUID_NODE, FLOW_LINK, HEAT_STRUCTURE, PUMP, VALVE, ROD_BANK,
        SENSOR_CHANNEL, DEVICE, ALARM, EMITTER, CONTAINMENT, CASCADE, PROCESS_UNIT, POOL
    }
}
