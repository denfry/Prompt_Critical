package dev.denfry.promptcritical.sim.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.denfry.promptcritical.sim.telemetry.CommandTickets.State;
import dev.denfry.promptcritical.sim.telemetry.SetpointWatch.Direction;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Квитанции, подписки, защита от флуда и регистры периферии устройств (03b §3b.1.5, §3b.1.9, §3b.3.2, §3b.4.5). */
class DeviceApiTest {
    @Test
    void ticketGoesQueuedDoneAndIsForgottenAfterRetention() {
        CommandTickets book = new CommandTickets();
        List<CommandTickets.Transition> tr = new ArrayList<>();
        long t = book.submit("scram", 100, tr);
        assertThat(t).isPositive();
        assertThat(tr).isEmpty();
        assertThat(book.get(t, 100).state()).isEqualTo(State.QUEUED);
        assertThat(book.move(t, State.DONE, null, 101).ticket().state()).isEqualTo(State.DONE);
        // финальное состояние не меняется
        assertThat(book.move(t, State.FAILED, "E_FAULT", 102)).isNull();
        assertThat(book.cancel(t, 102)).isNull();
        assertThat(book.get(t, 101 + CommandTickets.RETENTION_TICKS - 1)).isNotNull();
        assertThat(book.get(t, 101 + CommandTickets.RETENTION_TICKS)).isNull();
    }

    @Test
    void newCommandOfTheSameChannelSupersedesTheQueuedOne() {
        CommandTickets book = new CommandTickets();
        List<CommandTickets.Transition> tr = new ArrayList<>();
        long a = book.submit("rods_up", 0, tr);
        long other = book.submit("auto", 0, tr);
        long b = book.submit("rods_up", 0, tr);
        assertThat(tr).hasSize(1);
        assertThat(tr.get(0).ticket().id()).isEqualTo(a);
        assertThat(tr.get(0).ticket().state()).isEqualTo(State.SUPERSEDED);
        assertThat(book.get(other, 0).state()).isEqualTo(State.QUEUED);
        assertThat(book.get(b, 0).state()).isEqualTo(State.QUEUED);
        assertThat(book.cancel(b, 1).ticket().state()).isEqualTo(State.ABORTED);
    }

    @Test
    void ticketBookIsBounded() {
        CommandTickets book = new CommandTickets();
        List<CommandTickets.Transition> tr = new ArrayList<>();
        for (int i = 0; i < CommandTickets.MAX_OPEN; i++) assertThat(book.submit("c" + i, 0, tr)).isPositive();
        assertThat(book.submit("one_more", 0, tr)).isEqualTo(-1);
        assertThat(book.size()).isEqualTo(CommandTickets.MAX_OPEN);
    }

    @Test
    void watchUsesDeadbandAndDelays() {
        // уставка 10…20, зона 1, задержка включения 1 с (20 тиков), снятия 0,5 с
        SetpointWatch w = new SetpointWatch(10, 20, 1, 1, 0.5);
        assertThat(w.update(15, 0)).isNull();
        assertThat(w.update(21, 10)).isNull();
        // короткий выброс не сообщается
        assertThat(w.update(15, 20)).isNull();
        assertThat(w.update(21, 40)).isNull();
        assertThat(w.update(21, 59)).isNull();
        assertThat(w.update(21, 60)).isEqualTo(Direction.HIGH);
        // внутри зоны нечувствительности — всё ещё HIGH
        assertThat(w.update(19.5, 61)).isNull();
        assertThat(w.update(19.5, 200)).isNull();
        assertThat(w.state()).isEqualTo(Direction.HIGH);
        assertThat(w.update(18.9, 201)).isNull();
        assertThat(w.update(18.9, 211)).isEqualTo(Direction.NORMAL);
        assertThat(w.update(Double.NaN, 300)).isNull();
        SetpointWatch now = new SetpointWatch(Double.NEGATIVE_INFINITY, 5, 0, 0, 0);
        assertThat(now.update(6, 0)).isEqualTo(Direction.HIGH);
        assertThat(now.threshold(Direction.HIGH)).isEqualTo(5);
        assertThatThrownBy(() -> new SetpointWatch(5, 5, 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SetpointWatch(0, 1, Double.NaN, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eventGateDropsFloodAndReportsOncePerSecond() {
        EventGate g = new EventGate();
        int passed = 0;
        for (int i = 0; i < 100; i++) if (g.admit("pc_setpoint_crossed", 0)) passed++;
        assertThat(passed).isEqualTo((int) EventGate.BURST);
        Map<String, Integer> o = g.overflow(0);
        assertThat(o).containsEntry("pc_setpoint_crossed", 100 - (int) EventGate.BURST);
        // через тик пополнился один токен: из трёх событий проходит одно
        assertThat(g.admit("pc_command_result", 1)).isTrue();
        assertThat(g.admit("pc_command_result", 1)).isFalse();
        assertThat(g.admit("pc_command_result", 1)).isFalse();
        assertThat(g.overflow(5)).isNull();
        assertThat(g.overflow(20)).containsEntry("pc_command_result", 2);
        assertThat(g.overflow(60)).isNull();
    }

    @Test
    void modbusScalingSaturatesAndSplitsFloats() {
        assertThat(ModbusMap.scale16(0, 0, 25e6)).isZero();
        assertThat(ModbusMap.scale16(12.5e6, 0, 25e6)).isEqualTo(32768);
        assertThat(ModbusMap.scale16(25e6, 0, 25e6)).isEqualTo(65535);
        assertThat(ModbusMap.scale16(30e6, 0, 25e6)).isEqualTo(65535);
        assertThat(ModbusMap.scale16(-1, 0, 25e6)).isZero();
        assertThat(ModbusMap.scale16(Double.NaN, 0, 1)).isZero();
        int hi = ModbusMap.floatWord(1.5, true), lo = ModbusMap.floatWord(1.5, false);
        assertThat(Float.intBitsToFloat(hi << 16 | lo)).isEqualTo(1.5f);
        double[] v = {1.5, 0.25}, l = {0, 0}, h = {3, 1};
        assertThat(ModbusMap.inputRegister(0, v, l, h)).isEqualTo(32768);
        assertThat(ModbusMap.inputRegister(1002, v, l, h)).isEqualTo(ModbusMap.floatWord(0.25, true));
        assertThat(ModbusMap.inputRegister(5, v, l, h)).isZero();
    }
}
