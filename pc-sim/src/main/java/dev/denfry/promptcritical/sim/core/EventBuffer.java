package dev.denfry.promptcritical.sim.core;

/**
 * Предвыделенный буфер событий одного тика (05 §5.2.6): параллельные примитивные массивы, запись без аллокаций.
 * Ёмкость — {@code perf.event_buffer_capacity}. Переполнение не теряет сведений молча: при доставке приходит
 * {@link SimEvent.Journal} уровня {@link Severity#WARNING}, растёт счётчик {@link #overflowTotal()} (T-05-07).
 */
public final class EventBuffer {
    private static final byte COMMAND_RESULT = 1;
    private static final byte JOURNAL = 2;
    private static final byte DAMAGE_REQUESTED = 3;
    private static final byte MILESTONE = 4;

    private static final CommandStatus[] STATUSES = CommandStatus.values();
    private static final Severity[] SEVERITIES = Severity.values();

    private final int capacity;
    private final byte[] type;
    private final long[] tick;
    private final long[] source;
    private final long[] l1;
    private final int[] i1;
    private final int[] i2;
    private final String[] text;
    private final String[] text2;
    private final double[] d1;
    private int size;
    private int droppedThisTick;
    private long lastTick;
    private long overflowTotal;

    public EventBuffer(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity < 1");
        this.capacity = capacity;
        type = new byte[capacity];
        tick = new long[capacity];
        source = new long[capacity];
        l1 = new long[capacity];
        i1 = new int[capacity];
        i2 = new int[capacity];
        text = new String[capacity];
        text2 = new String[capacity];
        d1 = new double[capacity];
    }

    public void commandResult(long t, SimId target, long ticket, CommandStatus status) {
        int s = slot(t);
        if (s < 0) return;
        type[s] = COMMAND_RESULT;
        source[s] = target.raw();
        l1[s] = ticket;
        i1[s] = status.ordinal();
    }

    /** {@code message} — по возможности константа: строки не копируются. */
    public void journal(long t, SimId src, Severity severity, String message) {
        int s = slot(t);
        if (s < 0) return;
        type[s] = JOURNAL;
        source[s] = src.raw();
        i1[s] = severity.ordinal();
        text[s] = message;
    }

    public void damageRequested(long t, SimId src, long requestId, int granted, int denied) {
        int s = slot(t);
        if (s < 0) return;
        type[s] = DAMAGE_REQUESTED;
        source[s] = src.raw();
        l1[s] = requestId;
        i1[s] = granted;
        i2[s] = denied;
    }

    /** Событие домена для достижений (11 §11.2.1); {@code event} и {@code tag} — константы. */
    public void milestone(long t, SimId src, String event, String tag, double value) {
        int s = slot(t);
        if (s < 0) return;
        type[s] = MILESTONE;
        source[s] = src.raw();
        text[s] = event;
        text2[s] = tag;
        d1[s] = value;
    }

    private int slot(long t) {
        lastTick = t;
        if (size == capacity) {
            droppedThisTick++;
            overflowTotal++;
            return -1;
        }
        tick[size] = t;
        return size++;
    }

    public int size() {
        return size;
    }

    /** Событий потеряно из-за переполнения за всё время (счётчик профайлера). */
    public long overflowTotal() {
        return overflowTotal;
    }

    /** Отдать события потребителю и очистить буфер. Создаёт записи — вызывать вне горячего цикла. */
    public void drain(SimEventSink sink) {
        for (int i = 0; i < size; i++) sink.accept(event(i));
        if (droppedThisTick > 0) {
            sink.accept(new SimEvent.Journal(lastTick, SimId.NONE, Severity.WARNING,
                    "event buffer overflow: " + droppedThisTick + " events dropped (perf.event_buffer_capacity = "
                            + capacity + ")"));
        }
        clear();
    }

    public void clear() {
        for (int i = 0; i < size; i++) {
            text[i] = null;
            text2[i] = null;
        }
        size = 0;
        droppedThisTick = 0;
    }

    private SimEvent event(int i) {
        SimId src = new SimId(source[i]);
        return switch (type[i]) {
            case COMMAND_RESULT -> new SimEvent.CommandResult(tick[i], src, l1[i], STATUSES[i1[i]]);
            case JOURNAL -> new SimEvent.Journal(tick[i], src, SEVERITIES[i1[i]], text[i]);
            case DAMAGE_REQUESTED -> new SimEvent.DamageRequested(tick[i], src, l1[i], i1[i], i2[i]);
            case MILESTONE -> new SimEvent.Milestone(tick[i], src, text[i], text2[i], d1[i]);
            default -> throw new IllegalStateException("event type " + type[i]);
        };
    }
}
