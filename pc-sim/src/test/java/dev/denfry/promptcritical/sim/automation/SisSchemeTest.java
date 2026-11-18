package dev.denfry.promptcritical.sim.automation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.denfry.promptcritical.sim.automation.sis.SisLogic;
import dev.denfry.promptcritical.sim.core.ExternalInputs;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.MigrationRegistry;
import dev.denfry.promptcritical.sim.core.SimConfig;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.SimulationWorld;
import dev.denfry.promptcritical.sim.core.exec.WorkerPool;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * ПАЗ в схеме мира (A3): три газоанализатора 4–20 мА (OUT = 4 + 0,16·%НКПР) на AI1…AI3, голосование 2oo3 по уставке
 * 12 мА (50 % НКПР), контакт 11-14 логического блока держит лампу «работа разрешена». Обрыв линии, квитирование,
 * пропадание питания и сохранение.
 */
class SisSchemeTest {
    private static final long P = 301, Z = 302, A1 = 303, A2 = 304, A3 = 305, L = 306;
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

    private record Scheme(SimulationWorld w, AutomationUnit psu, AutomationUnit[] gas, AutomationUnit sis, AutomationUnit lamp) {
        void lel(double a, double b, double c) {
            gas[0].setInput(AutomationUnit.IN_MAIN, a);
            gas[1].setInput(AutomationUnit.IN_MAIN, b);
            gas[2].setInput(AutomationUnit.IN_MAIN, c);
        }

        boolean permitted() {
            return lamp.loadEnergized(0);
        }

        void reset() {
            sis.setInput(AutomationUnit.IN_MAIN, 1);
            run(w, 2);
            sis.setInput(AutomationUnit.IN_MAIN, 0);
            run(w, 2);
        }
    }

    private Scheme scheme() {
        SimulationWorld w = world();
        AutomationUnit psu = unit(w, AutomationKind.POWER_SUPPLY, P, Z);
        psu.setInput(AutomationUnit.IN_MAIN, 1);
        AutomationUnit[] gas = new AutomationUnit[3];
        long[] outs = {A1, A2, A3};
        for (int i = 0; i < 3; i++) {
            long[] n = new long[AutomationKind.GAS_DETECTOR.terminalCount()];
            n[AutomationKind.GAS_DETECTOR.indexOf("OUT")] = outs[i];
            gas[i] = unit(w, AutomationKind.GAS_DETECTOR, n);
        }
        long[] n = new long[AutomationKind.SIS_LOGIC.terminalCount()];
        n[AutomationKind.SIS_LOGIC.indexOf("+24")] = P;
        n[AutomationKind.SIS_LOGIC.indexOf("0")] = Z;
        n[AutomationKind.SIS_LOGIC.indexOf("AI1")] = A1;
        n[AutomationKind.SIS_LOGIC.indexOf("AI2")] = A2;
        n[AutomationKind.SIS_LOGIC.indexOf("AI3")] = A3;
        n[AutomationKind.SIS_LOGIC.indexOf("11")] = P;
        n[AutomationKind.SIS_LOGIC.indexOf("14")] = L;
        AutomationUnit sis = unit(w, AutomationKind.SIS_LOGIC, n);
        sis.setParam(AutomationUnit.D_SET, 12);
        AutomationUnit lamp = unit(w, AutomationKind.INDICATOR_LAMP, L, Z);
        Scheme s = new Scheme(w, psu, gas, sis, lamp);
        s.lel(10, 10, 10);
        run(w, 4);
        return s;
    }

    @Test
    void twoOutOfThreeDetectorsShutDownAndResetNeedsClearAir() {
        Scheme s = scheme();
        assertThat(s.permitted()).isTrue();
        s.lel(70, 10, 10);
        run(s.w, 3);
        assertThat(s.permitted()).as("one detector alone does not trip 2oo3").isTrue();
        s.lel(70, 60, 10);
        run(s.w, 3);
        assertThat(s.permitted()).as("two of three trip").isFalse();
        assertThat(s.sis.sis().state(true)).isEqualTo(SisLogic.State.TRIPPED);
        s.lel(10, 10, 10);
        run(s.w, 3);
        assertThat(s.permitted()).as("latched: no restart by itself").isFalse();
        s.reset();
        assertThat(s.permitted()).isTrue();
    }

    @Test
    void lossOfTwentyFourVoltsTripsTheFunction() {
        Scheme s = scheme();
        s.psu.setInput(AutomationUnit.IN_MAIN, 0);
        run(s.w, 3);
        s.psu.setInput(AutomationUnit.IN_MAIN, 1);
        run(s.w, 3);
        assertThat(s.permitted()).isFalse();
        s.reset();
        assertThat(s.permitted()).isTrue();
    }

    @Test
    void tripStateSurvivesSaveAndLoad() {
        Scheme s = scheme();
        s.lel(70, 60, 10);
        run(s.w, 3);
        SimulationWorld c = world();
        c.load(SimulationWorld.upgrade(s.w.saveTree(), new MigrationRegistry()));
        assertThat(c.stateHash()).isEqualTo(s.w.stateHash());
        AutomationUnit sis = auto(c).unit(s.sis.id());
        assertThat(sis.sis().tripped()).isTrue();
        assertThat(sis.sis().trips()).isEqualTo(1);
    }
}
