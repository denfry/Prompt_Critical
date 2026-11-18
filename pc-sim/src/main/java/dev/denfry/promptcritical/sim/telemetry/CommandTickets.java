package dev.denfry.promptcritical.sim.telemetry;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Квитанции команд устройства (03b §3b.1.5): каждая команда Lua получает номер, проходит
 * {@code QUEUED → EXECUTING → DONE} или заканчивается {@code REJECTED}, {@code SUPERSEDED} (новая команда того же
 * канала), {@code ABORTED} ({@code cancel}) или {@code FAILED} (отказ механизма). Финальная квитанция хранится
 * {@link #RETENTION_TICKS} тиков, потом забывается; книга ограничена {@link #MAX_OPEN} записями, чтобы программа не
 * выела память сервера.
 *
 * <p>Не потокобезопасна: владелец синхронизирует доступ (вызовы приходят с потоков компьютеров CC, исполнение — на
 * потоке сервера). Время — тики мира.
 */
public final class CommandTickets {
    /** {@code cc.ticket_retention_ticks = 600} [BALANCE] (03b §3b.1.5): 30 с после финального состояния. */
    public static final long RETENTION_TICKS = 600;
    /** Предел записей: 10 команд/с × 30 с хранения ≈ 300 на устройство (03b §3b.1.5), с запасом. */
    public static final int MAX_OPEN = 512;

    public enum State {
        QUEUED, EXECUTING, DONE, REJECTED, SUPERSEDED, ABORTED, FAILED;

        public boolean isFinal() {
            return this != QUEUED && this != EXECUTING;
        }
    }

    /** Квитанция: номер, канал (имя команды), состояние, код ошибки или {@code null}, тик последнего перехода. */
    public record Ticket(long id, String channel, State state, String error, long tick) {
    }

    /** Переход квитанции — повод для события {@code pc_command_result}. */
    public record Transition(Ticket ticket) {
    }

    private final Map<Long, Ticket> tickets = new LinkedHashMap<>();
    private long next = 1;

    /**
     * Принять команду канала {@code channel}: прежние ещё не исполненные команды того же канала становятся
     * {@code SUPERSEDED} и попадают в {@code transitions}. {@code -1} — книга переполнена.
     */
    public long submit(String channel, long tick, List<Transition> transitions) {
        purge(tick);
        if (tickets.size() >= MAX_OPEN) return -1;
        for (Map.Entry<Long, Ticket> e : tickets.entrySet()) {
            Ticket t = e.getValue();
            if (t.state() == State.QUEUED && t.channel().equals(channel)) {
                Ticket s = new Ticket(t.id(), t.channel(), State.SUPERSEDED, null, tick);
                e.setValue(s);
                transitions.add(new Transition(s));
            }
        }
        long id = next++;
        tickets.put(id, new Ticket(id, channel, State.QUEUED, null, tick));
        return id;
    }

    /**
     * Перевести квитанцию в {@code state}. Переход из финального состояния и неизвестный номер игнорируются
     * ({@code null}), как и переход назад в {@code QUEUED}.
     */
    public Transition move(long id, State state, String error, long tick) {
        Ticket t = tickets.get(id);
        if (t == null || t.state().isFinal() || state == State.QUEUED) return null;
        if (t.state() == State.EXECUTING && state == State.REJECTED) return null;
        Ticket n = new Ticket(id, t.channel(), state, state == State.DONE || state == State.EXECUTING ? null : error, tick);
        tickets.put(id, n);
        return new Transition(n);
    }

    /** Отменить; {@code null}, если квитанция уже финальная или неизвестна. */
    public Transition cancel(long id, long tick) {
        return move(id, State.ABORTED, "cancelled", tick);
    }

    /** Квитанция или {@code null}, если номер неизвестен или уже забыт. */
    public Ticket get(long id, long tick) {
        purge(tick);
        return tickets.get(id);
    }

    /** Число хранимых квитанций. */
    public int size() {
        return tickets.size();
    }

    /** Забыть финальные квитанции старше {@link #RETENTION_TICKS}; часы назад (старое сохранение) — тоже забыть. */
    public void purge(long tick) {
        Iterator<Ticket> it = tickets.values().iterator();
        while (it.hasNext()) {
            Ticket t = it.next();
            if (t.state().isFinal() && (tick - t.tick() >= RETENTION_TICKS || tick < t.tick())) it.remove();
        }
    }

    /** Все квитанции в порядке выдачи (для диагностики и тестов). */
    public List<Ticket> all() {
        return new ArrayList<>(tickets.values());
    }
}
