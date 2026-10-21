package dev.denfry.promptcritical.sim.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.core.ExternalInputs;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.MigrationRegistry;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.Quality;
import dev.denfry.promptcritical.sim.core.SimConfig;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.SimulationWorld;
import dev.denfry.promptcritical.sim.core.exec.WorkerPool;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Автоматика мира (этап 9 M3): блоки по цепям проводов собираются в банки A1/A2 — лестница пуск/стоп с самоподхватом
 * (03d §3d.3.2), перенос памяти реле при пересборке и сохранении, провал питания, мосты A2 → A1 (компаратор) и
 * A1 → A2 (режим интегратора), конфликт двух выходов на цепи.
 */
class AutomationSubsystemTest {
    private static final long P = 101, Z = 102, S1 = 103, S2 = 104, L = 105, H = 106, SP = 107, N = 108, C = 109;
    private final WorkerPool pool = WorkerPool.direct();
    private final List<SimulationWorld> worlds = new ArrayList<>();
    private int x;

    @AfterEach
    void close() {
        for (SimulationWorld w : worlds) w.close();
        pool.close();
    }

    private SimulationWorld world() {
        SimulationWorld w = new SimulationWorld(SimConfig.defaults(), 7, 0, pool);
        w.register(new AutomationSubsystem(AutomationSubsystem.worldConfig(AutomationConfig.defaults())));
        worlds.add(w);
        return w;
    }

    private static AutomationSubsystem auto(SimulationWorld w) {
        return w.subsystem(AutomationSubsystem.class);
    }

    private AutomationUnit unit(SimulationWorld w, AutomationKind kind, long... nets) {
        SimId id = w.topology().addDevice("promptcritical:" + kind.name().toLowerCase(java.util.Locale.ROOT), new GridPos(x++, 64, 0));
        AutomationUnit u = auto(w).unit(id, kind);
        u.setNets(nets);
        return u;
    }

    private static void run(SimulationWorld w, int ticks) {
        for (int i = 0; i < ticks; i++) w.tick(ExternalInputs.NONE);
    }

    /** Блок питания, СТОП (НЗ 11-12), ПУСК (НО 13-14), K1 с самоподхватом 11-14, лампа «РАБОТА» через 21-24. */
    private record Ladder(SimulationWorld w, AutomationUnit psu, AutomationUnit stop, AutomationUnit start, AutomationUnit k1,
            AutomationUnit lamp) {
    }

    private Ladder ladder() {
        SimulationWorld w = world();
        AutomationUnit psu = unit(w, AutomationKind.POWER_SUPPLY, P, Z);
        psu.setInput(AutomationUnit.IN_MAIN, 1);
        AutomationUnit stop = unit(w, AutomationKind.PUSHBUTTON, 0, 0, P, S1);
        AutomationUnit start = unit(w, AutomationKind.PUSHBUTTON, S1, S2, 0, 0);
        AutomationUnit k1 = unit(w, AutomationKind.RELAY, S2, Z, S1, 0, S2, P, 0, L);
        AutomationUnit lamp = unit(w, AutomationKind.INDICATOR_LAMP, L, Z);
        return new Ladder(w, psu, stop, start, k1, lamp);
    }

    private static void press(SimulationWorld w, AutomationUnit button) {
        button.setInput(AutomationUnit.IN_MAIN, 1);
        run(w, 3);
        button.setInput(AutomationUnit.IN_MAIN, 0);
        run(w, 3);
    }

    @Test
    void startStopLadderSealsInAndDropsOut() {
        Ladder l = ladder();
        run(l.w, 5);
        assertThat(l.k1.relayPicked()).isFalse();
        assertThat(l.psu.supplyVoltage()).isCloseTo(24, within(1e-9));
        press(l.w, l.start);
        run(l.w, 20);
        assertThat(l.k1.relayPicked()).as("seal-in holds after START is released").isTrue();
        assertThat(l.lamp.loadEnergized(0)).isTrue();
        assertThat(l.k1.relayOps()).isEqualTo(1);
        press(l.w, l.stop);
        run(l.w, 20);
        assertThat(l.k1.relayPicked()).isFalse();
        assertThat(l.lamp.loadEnergized(0)).isFalse();
    }

    @Test
    void rebuildKeepsRelayStateAndWear() {
        Ladder l = ladder();
        run(l.w, 2);
        press(l.w, l.start);
        int before = auto(l.w).rebuilds();
        unit(l.w, AutomationKind.INDICATOR_LAMP, 0, 0); // новый блок — схема пересобирается
        run(l.w, 5);
        assertThat(auto(l.w).rebuilds()).isGreaterThan(before);
        assertThat(l.k1.relayPicked()).isTrue();
        assertThat(l.k1.relayOps()).isEqualTo(1);
        assertThat(l.lamp.loadEnergized(0)).isTrue();
    }

    @Test
    void supplyLossDropsTheSealIn() {
        Ladder l = ladder();
        run(l.w, 2);
        press(l.w, l.start);
        l.psu.setInput(AutomationUnit.IN_MAIN, 0);
        run(l.w, 3);
        assertThat(l.k1.relayPicked()).isFalse();
        l.psu.setInput(AutomationUnit.IN_MAIN, 1);
        run(l.w, 10);
        assertThat(l.k1.relayPicked()).as("power return does not restart the drive").isFalse();
    }

    @Test
    void comparatorDrivesARelayAndSummerInvertsTheSetpoint() {
        SimulationWorld w = world();
        AutomationUnit psu = unit(w, AutomationKind.POWER_SUPPLY, P, Z);
        psu.setInput(AutomationUnit.IN_MAIN, 1);
        AutomationUnit station = unit(w, AutomationKind.MANUAL_STATION, 0, 0, 0, 0, SP, 0, 0, 0, 0);
        station.setParam(AutomationUnit.D_SET, 5);
        AutomationUnit summer = unit(w, AutomationKind.ANALOG_SUMMER, SP, 0, 0, 0, N);
        AutomationUnit cmp = unit(w, AutomationKind.ANALOG_COMPARATOR, SP, 0, P, 0, C);
        AutomationUnit k = unit(w, AutomationKind.RELAY, C, Z, 0, 0, 0, 0, 0, 0);
        run(w, 10);
        assertThat(summer.output()).isCloseTo(-5, within(0.15));
        assertThat(summer.quality()).isEqualTo(Quality.GOOD);
        assertThat(cmp.output()).isEqualTo(1);
        assertThat(k.relayPicked()).isTrue();
        AutomationUnit ref = unit(w, AutomationKind.MANUAL_STATION, 0, 0, 0, 0, H, 0, 0, 0, 0);
        ref.setParam(AutomationUnit.D_SET, 5.05);
        cmp.setNets(SP, H, P, 0, C);
        run(w, 10);
        assertThat(k.relayPicked()).as("x1 − x2 = −0,05 В — внутри зоны возврата 0,1 В").isTrue();
        ref.setParam(AutomationUnit.D_SET, 7);
        run(w, 10);
        assertThat(cmp.output()).isEqualTo(0);
        assertThat(k.relayPicked()).isFalse();
    }

    @Test
    void holdInputFreezesTheIntegrator() {
        SimulationWorld w = world();
        AutomationUnit psu = unit(w, AutomationKind.POWER_SUPPLY, P, Z);
        psu.setInput(AutomationUnit.IN_MAIN, 1);
        AutomationUnit station = unit(w, AutomationKind.MANUAL_STATION, 0, 0, 0, 0, SP, 0, 0, 0, 0);
        station.setParam(AutomationUnit.D_SET, 1);
        AutomationUnit integ = unit(w, AutomationKind.ANALOG_INTEGRATOR, SP, 0, 0, 0, N, 0, H, Z);
        AutomationUnit hold = unit(w, AutomationKind.PUSHBUTTON, P, H, 0, 0);
        run(w, 20); // 1 с: ẏ = −1 В/с
        double y1 = integ.output();
        assertThat(y1).isCloseTo(-1, within(0.1));
        hold.setInput(AutomationUnit.IN_MAIN, 1);
        run(w, 2);
        double y2 = integ.output();
        run(w, 40);
        assertThat(integ.output()).as("HOLD").isCloseTo(y2, within(0.02));
        hold.setInput(AutomationUnit.IN_MAIN, 0);
        run(w, 20);
        assertThat(integ.output()).isCloseTo(y2 - 1, within(0.1));
    }

    @Test
    void twoOutputsOnOneNetAreADriverConflict() {
        SimulationWorld w = world();
        AutomationUnit station = unit(w, AutomationKind.MANUAL_STATION, 0, 0, 0, 0, SP, 0, 0, 0, 0);
        station.setParam(AutomationUnit.D_SET, 4);
        AutomationUnit a = unit(w, AutomationKind.ANALOG_SUMMER, SP, 0, 0, 0, N);
        AutomationUnit b = unit(w, AutomationKind.ANALOG_SUMMER, SP, 0, 0, 0, N);
        AutomationUnit reader = unit(w, AutomationKind.ANALOG_SUMMER, N, 0, 0, 0, 0);
        run(w, 5);
        assertThat(a.conflict()).isTrue();
        assertThat(b.conflict()).isTrue();
        assertThat(reader.conflict()).isTrue();
        assertThat(reader.quality()).isEqualTo(Quality.BAD);
        assertThat(reader.output()).as("input reads 0 V").isCloseTo(0, within(0.1));
        b.setNets(SP, 0, 0, 0, 0);
        run(w, 5);
        assertThat(reader.conflict()).isFalse();
        assertThat(reader.output()).isCloseTo(4, within(0.2));
    }

    @Test
    void stateSurvivesSaveAndLoad() {
        Ladder l = ladder();
        run(l.w, 2);
        press(l.w, l.start);
        MutableStateTree saved = l.w.saveTree();
        SimulationWorld c = world();
        c.load(SimulationWorld.upgrade(saved, new MigrationRegistry()));
        assertThat(c.stateHash()).isEqualTo(l.w.stateHash());
        AutomationUnit k1 = auto(c).unit(l.k1.id());
        run(c, 5);
        assertThat(k1.relayPicked()).isTrue();
        assertThat(k1.relayOps()).isEqualTo(1);
    }
}
