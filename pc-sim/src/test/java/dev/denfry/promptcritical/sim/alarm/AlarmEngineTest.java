package dev.denfry.promptcritical.sim.alarm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;
import org.junit.jupiter.api.Test;

/** Движок сигнализации ISA-18.2: тест-кейсы 03c §3c.10 (T-3c-01…07) и остальной автомат §3c.6.2–§3c.6.5. */
class AlarmEngineTest {
    static final double DT = 0.05;

    static AlarmDefinition high(String tag, int priority, double limit, double db, double on, double off) {
        return new AlarmDefinition(0, tag, AlarmKind.HIGH, priority, limit, db, on, off, false, "", "high " + tag, "reduce");
    }

    /** Прогон от {@code t0} до {@code t1} с шагом 0,05 с; значение тега — функция времени. */
    static double run(AlarmEngine e, double t0, double t1, String tag, DoubleUnaryOperator value, List<double[]> trace, int id) {
        long k0 = Math.round(t0 / DT), k1 = Math.round(t1 / DT);
        for (long k = k0; k <= k1; k++) {
            double t = k * DT;
            e.step(t, g -> g.equals(tag) ? value.applyAsDouble(t) : Double.NaN);
            if (trace != null) trace.add(new double[] {t, e.get(id).state().ordinal()});
        }
        return k1 * DT;
    }

    @Test
    void t3c01OnDelayIsNotReachedAndTheValueInsideTheDeadbandDoesNotRaise() {
        AlarmEngine e = new AlarmEngine();
        int id = e.define(high("x", 2, 10, 0.02, 15, 15), false);
        List<double[]> trace = new ArrayList<>();
        run(e, 0, 14.85, "x", t -> 10.5, trace, id);
        run(e, 14.9, 60, "x", t -> 9.9, trace, id);
        assertThat(trace).allMatch(p -> p[1] == AlarmState.NORMAL.ordinal());
    }

    @Test
    void t3c02RaisesExactlyAtTheEndOfTheOnDelay() {
        AlarmEngine e = new AlarmEngine();
        int id = e.define(high("x", 2, 10, 0.02, 15, 15), false);
        List<double[]> trace = new ArrayList<>();
        run(e, 0, 20, "x", t -> 10.5, trace, id);
        double first = trace.stream().filter(p -> p[1] == AlarmState.UNACK.ordinal()).mapToDouble(p -> p[0]).min().orElseThrow();
        assertThat(first).isCloseTo(15.0, within(DT + 1e-9));
        assertThat(e.get(id).tRaise()).isCloseTo(15.0, within(DT + 1e-9));
    }

    @Test
    void t3c03ReturnsUnacknowledgedAfterTheOffDelayAndAckClearsIt() {
        AlarmEngine e = new AlarmEngine();
        int id = e.define(high("x", 2, 10, 0.02, 15, 15), false);
        run(e, 0, 20, "x", t -> 10.5, null, id);
        assertThat(e.get(id).state()).isEqualTo(AlarmState.UNACK);
        List<double[]> trace = new ArrayList<>();
        run(e, 20.05, 40, "x", t -> 9.7, trace, id);
        double rtn = trace.stream().filter(p -> p[1] == AlarmState.RTNUN.ordinal()).mapToDouble(p -> p[0]).min().orElseThrow();
        assertThat(rtn).isCloseTo(35.05, within(DT + 1e-9));
        assertThat(e.acknowledge(id, 40)).isTrue();
        assertThat(e.get(id).state()).isEqualTo(AlarmState.NORMAL);
    }

    @Test
    void t3c04ValueInsideTheDeadbandKeepsTheAlarm() {
        AlarmEngine e = new AlarmEngine();
        int id = e.define(high("x", 2, 10, 0.02, 15, 15), false);
        run(e, 0, 20, "x", t -> 10.5, null, id);
        run(e, 20.05, 120, "x", t -> 9.85, null, id);
        assertThat(e.get(id).state()).isEqualTo(AlarmState.UNACK);
    }

    @Test
    void t3c05ElevenAlarmsInTenMinutesAreAFloodAndItEndsBelowFive() {
        AlarmEngine e = new AlarmEngine();
        List<AlarmEvent> events = new ArrayList<>();
        e.setListener(events::add);
        Map<String, Double> v = new HashMap<>();
        for (int i = 0; i < 11; i++) e.define(new AlarmDefinition(0, "d" + i, AlarmKind.DISCRETE, 2, 0, 0, 0, 0, false, "", "", ""), false);
        // 11 возникновений за 9 мин 50 с: по одному каждые 59 с
        for (int i = 0; i < 11; i++) {
            v.put("d" + i, 1.0);
            e.step(i * 59.0, t -> v.getOrDefault(t, 0.0));
            v.put("d" + i, 0.0);
            e.step(i * 59.0 + 1, t -> v.getOrDefault(t, 0.0));
        }
        assertThat(e.flood()).isTrue();
        assertThat(events).anyMatch(ev -> ev.type() == AlarmEvent.Type.FLOOD_ON);
        assertThat(e.statistics().rate10min()).isEqualTo(11);
        // через 10 мин после последнего — меньше 5 за окно: лавина кончилась
        e.step(590 + 600, t -> 0.0);
        assertThat(e.flood()).isFalse();
        assertThat(events).anyMatch(ev -> ev.type() == AlarmEvent.Type.FLOOD_OFF);
    }

    @Test
    void floodHasHysteresisBetweenTenAndFive() {
        AlarmEngine e = new AlarmEngine();
        Map<String, Double> v = new HashMap<>();
        for (int i = 0; i < 11; i++) e.define(new AlarmDefinition(0, "d" + i, AlarmKind.DISCRETE, 3, 0, 0, 0, 0, false, "", "", ""), false);
        for (int i = 0; i < 11; i++) {
            v.put("d" + i, 1.0);
            e.step(i, t -> v.getOrDefault(t, 0.0));
        }
        assertThat(e.flood()).isTrue();
        // первые 6 ушли из окна (в нём 5) — ещё лавина; ушла 7-я (в окне 4) — нет
        e.step(605.5, t -> 1.0);
        assertThat(e.statistics().rate10min()).isEqualTo(5);
        assertThat(e.flood()).isTrue();
        e.step(606.5, t -> 1.0);
        assertThat(e.flood()).isFalse();
    }

    @Test
    void t3c06PostTripSuppressesTheGroupAfterTwoSecondsForHalfAnHour() {
        AlarmEngine e = new AlarmEngine();
        int id = e.define(new AlarmDefinition(0, "power", AlarmKind.LOW, 3, 0.1, 0.02, 0, 0, false, AlarmEngine.POST_TRIP,
                "power low", "check"), false);
        double t = 0;
        e.trip(0);
        for (; t <= 1.95 + 1e-9; t += DT) e.step(t, g -> 1.0);
        assertThat(e.get(id).state()).isEqualTo(AlarmState.NORMAL);
        e.step(2.0, g -> 1.0);
        assertThat(e.get(id).state()).isEqualTo(AlarmState.SUPPRESSED);
        // мощность упала (следствие АЗ) — сигнализация скрыта
        e.step(10, g -> 0.0);
        e.step(11, g -> 0.0);
        assertThat(e.get(id).state()).isEqualTo(AlarmState.SUPPRESSED);
        assertThat(e.get(id).active()).isTrue();
        e.step(1801.9, g -> 0.0);
        assertThat(e.get(id).state()).isEqualTo(AlarmState.SUPPRESSED);
        e.step(1802.0, g -> 0.0);
        assertThat(e.suppressed(AlarmEngine.POST_TRIP)).isFalse();
        assertThat(e.get(id).state()).isEqualTo(AlarmState.UNACK);
    }

    @Test
    void t3c07PriorityOneCannotBeShelved() {
        AlarmEngine e = new AlarmEngine();
        int id = e.define(high("x", 1, 10, 0, 0, 0), false);
        e.step(0, g -> 11.0);
        assertThat(e.get(id).state()).isEqualTo(AlarmState.UNACK);
        assertThatThrownBy(() -> e.shelve(id, 60, 0)).isInstanceOf(AlarmAccessException.class).hasMessageContaining("E_ACCESS");
        assertThat(e.get(id).state()).isEqualTo(AlarmState.UNACK);
    }

    @Test
    void shelvingHidesTheAlarmAndItComesBackWhenTheShelfExpires() {
        AlarmEngine e = new AlarmEngine();
        int id = e.define(high("x", 2, 10, 0, 0, 0), false);
        e.step(0, g -> 11.0);
        assertThat(e.shelve(id, 100, 0)).isTrue();
        assertThat(e.get(id).state()).isEqualTo(AlarmState.SHELVED);
        assertThat(e.hornPriority()).isZero();
        assertThat(e.statistics().shelved()).isEqualTo(1);
        e.step(50, g -> 11.0);
        assertThat(e.get(id).state()).isEqualTo(AlarmState.SHELVED);
        e.step(100, g -> 11.0);
        assertThat(e.get(id).state()).isEqualTo(AlarmState.UNACK);
        assertThatThrownBy(() -> e.shelve(id, AlarmEngine.MAX_SHELVE_S + 1, 100)).isInstanceOf(IllegalArgumentException.class);
        // шельф кончился без условия — NORMAL
        e.shelve(id, 10, 100);
        e.step(105, g -> 5.0);
        e.step(110, g -> 5.0);
        assertThat(e.get(id).state()).isEqualTo(AlarmState.NORMAL);
    }

    @Test
    void latchingAlarmWaitsForResetAfterAcknowledgement() {
        AlarmEngine e = new AlarmEngine();
        int id = e.define(new AlarmDefinition(0, "x", AlarmKind.HIGH, 2, 10, 0, 0, 0, true, "", "", ""), false);
        e.step(0, g -> 11.0);
        e.step(1, g -> 5.0);
        assertThat(e.get(id).state()).isEqualTo(AlarmState.UNACK);
        e.acknowledge(id, 2);
        e.step(3, g -> 5.0);
        assertThat(e.get(id).state()).isEqualTo(AlarmState.ACKED);
        assertThat(e.reset(id, 4)).isTrue();
        assertThat(e.get(id).state()).isEqualTo(AlarmState.NORMAL);
    }

    @Test
    void acknowledgedPriorityOneReAlarmsAfterHalfAnHour() {
        AlarmEngine e = new AlarmEngine();
        int id = e.define(high("x", 1, 10, 0, 0, 0), false);
        e.step(0, g -> 11.0);
        e.acknowledge(id, 10);
        e.step(1809, g -> 11.0);
        assertThat(e.get(id).state()).isEqualTo(AlarmState.ACKED);
        e.step(1810, g -> 11.0);
        assertThat(e.get(id).state()).isEqualTo(AlarmState.UNACK);
        assertThat(e.statistics().rate10min()).isEqualTo(0);
    }

    @Test
    void chatteringIsCountedAndTheAutoDelayDoubles() {
        AlarmEngine e = new AlarmEngine(true);
        int id = e.define(new AlarmDefinition(0, "d", AlarmKind.DISCRETE, 3, 0, 0, 2, 0, false, "", "", ""), false);
        double t = 0;
        for (int i = 0; i < 3; i++) {
            for (int k = 0; k <= 3; k++) e.step(t += 1, g -> 1.0);
            e.step(t += 1, g -> 0.0);
            e.acknowledge(id, t);
        }
        assertThat(e.get(id).chattering()).isTrue();
        assertThat(e.statistics().chattering()).containsKey(id);
        assertThat(e.get(id).onDelayS()).isEqualTo(4.0);
        e.step(t + 120, g -> 0.0);
        assertThat(e.get(id).chattering()).isFalse();
    }

    @Test
    void firstOutMarksTheFirstAlarmOfABurstInAGroup() {
        AlarmEngine e = new AlarmEngine();
        int a = e.define(new AlarmDefinition(0, "a", AlarmKind.DISCRETE, 2, 0, 0, 0, 0, false, "trip", "", ""), false);
        int b = e.define(new AlarmDefinition(0, "b", AlarmKind.DISCRETE, 2, 0, 0, 0, 0, false, "trip", "", ""), false);
        e.step(0, g -> g.equals("a") ? 1.0 : 0.0);
        e.step(1, g -> 1.0);
        assertThat(e.get(a).firstOut()).isTrue();
        assertThat(e.get(b).firstOut()).isFalse();
        e.acknowledge(a, 2);
        assertThat(e.get(a).firstOut()).isFalse();
    }

    @Test
    void silenceStopsTheHornUntilANewAlarm() {
        AlarmEngine e = new AlarmEngine();
        int x = e.define(high("x", 2, 10, 0, 0, 0), false);
        int y = e.define(high("y", 1, 10, 0, 0, 0), false);
        e.step(0, g -> g.equals("x") ? 11.0 : 0.0);
        assertThat(e.hornPriority()).isEqualTo(2);
        e.silence(1);
        assertThat(e.hornPriority()).isZero();
        assertThat(e.get(x).state()).isEqualTo(AlarmState.UNACK);
        e.step(2, g -> 11.0);
        assertThat(e.get(y).state()).isEqualTo(AlarmState.UNACK);
        assertThat(e.hornPriority()).isEqualTo(1);
    }

    @Test
    void badQualityAndHeldConditionOnMissingValue() {
        AlarmEngine e = new AlarmEngine();
        int bad = e.define(new AlarmDefinition(0, "x", AlarmKind.BAD_QUALITY, 2, 0, 0, 0, 0, false, "", "", ""), false);
        int hi = e.define(high("x", 2, 10, 0, 0, 0), false);
        e.step(0, g -> 11.0);
        e.step(1, g -> Double.NaN);
        assertThat(e.get(bad).state()).isEqualTo(AlarmState.UNACK);
        assertThat(e.get(hi).state()).isEqualTo(AlarmState.UNACK);
        assertThat(e.statistics().stale()).isEqualTo(2);
    }

    @Test
    void softAlarmsAreRaisedAndClearedByTheProgramAndOnlyTheyCanBeDeleted() {
        AlarmEngine e = new AlarmEngine();
        int soft = e.define(new AlarmDefinition(0, "lua", AlarmKind.SOFT, 3, Double.NaN, 0, 0, 0, false, "", "lua says", ""), true);
        int built = e.upsert(high("x", 2, 10, 0, 0, 0));
        e.raiseSoft(soft, 42);
        e.step(0, g -> 0.0);
        assertThat(e.get(soft).state()).isEqualTo(AlarmState.UNACK);
        e.clearSoft(soft);
        e.step(1, g -> 0.0);
        assertThat(e.get(soft).state()).isEqualTo(AlarmState.RTNUN);
        assertThatThrownBy(() -> e.deleteProgram(built)).isInstanceOf(AlarmAccessException.class);
        assertThat(e.deleteProgram(soft)).isTrue();
        assertThat(e.upsert(high("x", 3, 12, 0, 0, 0))).isEqualTo(built);
        assertThat(e.get(built).definition().priority()).isEqualTo(3);
    }

    @Test
    void outOfServiceHidesAndReturnsLikeSuppression() {
        AlarmEngine e = new AlarmEngine();
        int id = e.define(high("x", 2, 10, 0, 0, 0), false);
        e.setOutOfService(id, true, 0);
        e.step(1, g -> 11.0);
        assertThat(e.get(id).state()).isEqualTo(AlarmState.OOS);
        e.setOutOfService(id, false, 2);
        assertThat(e.get(id).state()).isEqualTo(AlarmState.UNACK);
    }

    @Test
    void stateSurvivesSaveAndLoad() {
        AlarmEngine e = new AlarmEngine();
        int id = e.define(high("x", 2, 10, 0.02, 0, 0), false);
        e.define(new AlarmDefinition(0, "s", AlarmKind.SOFT, 3, Double.NaN, 0, 0, 0, false, "g", "msg", "act"), true);
        e.step(0, g -> 11.0);
        e.acknowledge(id, 1);
        e.setSuppression("g", true);
        MutableStateTree tree = new MutableStateTree();
        e.save(new TreeStateWriter(tree));
        AlarmEngine f = new AlarmEngine();
        f.load(new TreeStateReader(tree));
        assertThat(f.size()).isEqualTo(2);
        assertThat(f.get(id).state()).isEqualTo(AlarmState.ACKED);
        assertThat(f.get(id).tRaise()).isEqualTo(0.0);
        assertThat(f.suppressed("g")).isTrue();
        assertThat(f.get(id + 1).definition().message()).isEqualTo("msg");
        assertThat(f.find(high("x", 2, 10, 0, 0, 0).key())).isSameAs(f.get(id));
        int next = f.define(high("y", 2, 1, 0, 0, 0), false);
        assertThat(next).isEqualTo(id + 2);
        // после загрузки условие продолжает отслеживаться
        f.step(5, g -> 5.0);
        assertThat(f.get(id).state()).isEqualTo(AlarmState.NORMAL);
    }

    @Test
    void lowAlarmUsesTheDeadbandAboveTheLimit() {
        AlarmEngine e = new AlarmEngine();
        int id = e.define(new AlarmDefinition(0, "l", AlarmKind.LOW, 2, 0.35, 0.1, 0, 0, false, "", "", ""), false);
        e.step(0, g -> 0.3);
        assertThat(e.get(id).state()).isEqualTo(AlarmState.UNACK);
        e.step(1, g -> 0.38);
        assertThat(e.get(id).state()).isEqualTo(AlarmState.UNACK);
        e.step(2, g -> 0.39);
        assertThat(e.get(id).state()).isEqualTo(AlarmState.RTNUN);
    }
}
