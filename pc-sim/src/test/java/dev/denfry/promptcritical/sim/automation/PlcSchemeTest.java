package dev.denfry.promptcritical.sim.automation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.denfry.promptcritical.sim.automation.plc.PlcCpu;
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
 * ПЛК в схеме мира (A3): входы — оптроны от кнопок через блок питания, выход — релейный контакт на лампу, питание —
 * от того же блока; программа самоподхвата заменяет реле K1 из лестницы A1. Цикл, провал питания и сохранение.
 */
class PlcSchemeTest {
    private static final long P = 201, Z = 202, S1 = 203, S2 = 204, L = 205;
    private static final String SELF_HOLD = """
            LD  %IX0.0      (* ПУСК *)
            OR  %QX0.0
            AND %IX0.1      (* СТОП, НЗ *)
            ST  %QX0.0
            """;
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

    /** Цепи клемм ПЛК: L+, M, I0…I7, 1L, Q0…Q7, AI0…AI3, AO0…AO1. */
    private static long[] plcNets() {
        long[] n = new long[AutomationKind.PLC.terminalCount()];
        n[AutomationKind.PLC.indexOf("L+")] = P;
        n[AutomationKind.PLC.indexOf("M")] = Z;
        n[AutomationKind.PLC.indexOf("I0")] = S1;
        n[AutomationKind.PLC.indexOf("I1")] = S2;
        n[AutomationKind.PLC.indexOf("1L")] = P;
        n[AutomationKind.PLC.indexOf("Q0")] = L;
        return n;
    }

    private record Scheme(SimulationWorld w, AutomationUnit psu, AutomationUnit start, AutomationUnit plc, AutomationUnit lamp) {
    }

    private Scheme scheme() {
        SimulationWorld w = world();
        AutomationUnit psu = unit(w, AutomationKind.POWER_SUPPLY, P, Z);
        psu.setInput(AutomationUnit.IN_MAIN, 1);
        AutomationUnit start = unit(w, AutomationKind.PUSHBUTTON, P, S1, 0, 0);
        unit(w, AutomationKind.PUSHBUTTON, 0, 0, P, S2); // СТОП: НЗ 11-12, не нажата
        AutomationUnit plc = unit(w, AutomationKind.PLC, plcNets());
        assertThat(plc.plc().load(SELF_HOLD).ok()).isTrue();
        AutomationUnit lamp = unit(w, AutomationKind.INDICATOR_LAMP, L, Z);
        return new Scheme(w, psu, start, plc, lamp);
    }

    private static void press(SimulationWorld w, AutomationUnit button) {
        button.setInput(AutomationUnit.IN_MAIN, 1);
        run(w, 3);
        button.setInput(AutomationUnit.IN_MAIN, 0);
        run(w, 3);
    }

    @Test
    void plcLatchesTheLampFromThePushbuttons() {
        Scheme s = scheme();
        run(s.w, 3);
        assertThat(s.plc.plc().mode()).isEqualTo(PlcCpu.Mode.RUN);
        assertThat(s.lamp.loadEnergized(0)).isFalse();
        press(s.w, s.start);
        assertThat(s.lamp.loadEnergized(0)).as("held by the program after the button is released").isTrue();
    }

    @Test
    void lossOfTwentyFourVoltsDropsOutputsAndTheLatch() {
        Scheme s = scheme();
        run(s.w, 3);
        press(s.w, s.start);
        s.psu.setInput(AutomationUnit.IN_MAIN, 0);
        run(s.w, 3);
        assertThat(s.plc.plc().mode()).isEqualTo(PlcCpu.Mode.OFF);
        s.psu.setInput(AutomationUnit.IN_MAIN, 1);
        run(s.w, 5);
        assertThat(s.plc.plc().mode()).isEqualTo(PlcCpu.Mode.RUN);
        assertThat(s.lamp.loadEnergized(0)).as("self-hold does not survive a power cut").isFalse();
    }

    @Test
    void programAndStateSurviveSaveAndLoad() {
        Scheme s = scheme();
        run(s.w, 3);
        press(s.w, s.start);
        SimulationWorld c = world();
        c.load(SimulationWorld.upgrade(s.w.saveTree(), new MigrationRegistry()));
        assertThat(c.stateHash()).isEqualTo(s.w.stateHash());
        AutomationUnit plc = auto(c).unit(s.plc.id());
        AutomationUnit lamp = auto(c).unit(s.lamp.id());
        assertThat(plc.plc().program().source()).isEqualTo(SELF_HOLD);
        run(c, 5);
        assertThat(lamp.loadEnergized(0)).isTrue();
    }
}
