package dev.denfry.promptcritical.sim.automation.plc;

import static dev.denfry.promptcritical.sim.automation.plc.PlcCpuTest.DT;
import static dev.denfry.promptcritical.sim.automation.plc.PlcCpuTest.NO_AI;
import static dev.denfry.promptcritical.sim.automation.plc.PlcCpuTest.di;
import static dev.denfry.promptcritical.sim.automation.plc.PlcCpuTest.running;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.IntPredicate;
import org.junit.jupiter.api.Test;

/**
 * LD, FBD и SFC (IEC 61131-3, M6: «программа ведёт себя одинаково на любом языке»): пуск-стоп с самоподхватом и реле
 * времени дают те же выходы, что на IL; SFC проходит цикл шагов по переходам.
 */
class GraphicalLanguagesTest {
    private static final String IL = """
            LD   %IX0.0
            OR   %QX0.0
            AND  %IX0.1
            ST   %QX0.0
            CAL  TON0(IN := %QX0.0, PT := T#1S)
            LD   TON0.Q
            ST   %QX0.1
            """;
    private static final String LD = """
            (*LD*)
            BST XIC(%IX0.0) NXB XIC(%QX0.0) BND XIC(%IX0.1) OTE(%QX0.0)   // самоподхват
            XIC(%QX0.0) TON(T1, T#1S) OTE(%QX0.1)                                   // реле времени
            """;
    private static final String FBD = """
            (*FBD*)
            %QX0.0 := AND(OR(%IX0.0, %QX0.0), %IX0.1)
            T1 := TON(%QX0.0, T#1S)
            %QX0.1 := T1.Q
            """;

    private static IntPredicate[] steps() {
        IntPredicate[] s = new IntPredicate[80];
        for (int k = 0; k < s.length; k++) {
            int kk = k;
            // стоп (НЗ) нажат; пуск на 3 цикла; работа; стоп на 60-м цикле
            s[k] = i -> i == 1 && kk < 60 || i == 0 && kk >= 2 && kk < 5;
        }
        return s;
    }

    private static void same(String other, PlcLanguage lang) {
        assertThat(PlcLanguage.of(other)).isEqualTo(lang);
        assertThat(PlcLanguage.compile(other).errors()).as(lang + " compiles").isEmpty();
        PlcCpu a = running(IL), b = running(other);
        IntPredicate[] st = steps();
        for (int k = 0; k < st.length; k++) {
            boolean[] in = di(st[k]);
            a.scan(DT, true, in, NO_AI);
            b.scan(DT, true, in, NO_AI);
            for (int i = 0; i < 2; i++) assertThat(b.output(i)).as("%s step %d QX%d", lang, k, i).isEqualTo(a.output(i));
        }
    }

    @Test
    void ladderMatchesInstructionList() {
        same(LD, PlcLanguage.LD);
    }

    @Test
    void functionBlockDiagramMatchesInstructionList() {
        same(FBD, PlcLanguage.FBD);
    }

    @Test
    void sequenceStepsThroughItsTransitions() {
        String sfc = """
                (*SFC*)
                INITIAL_STEP Idle: END_STEP
                STEP Fill: %QX0.0(N); END_STEP
                STEP Heat: Heater(N); %QX0.2(S); END_STEP
                TRANSITION FROM Idle TO Fill := %IX0.0; END_TRANSITION
                TRANSITION FROM Fill TO Heat := %IX0.1; END_TRANSITION
                TRANSITION FROM Heat TO Idle := %IX0.2; END_TRANSITION
                ACTION Heater: %QX0.1 := TRUE; END_ACTION
                """;
        assertThat(PlcLanguage.of(sfc)).isEqualTo(PlcLanguage.SFC);
        assertThat(PlcLanguage.compile(sfc).errors()).isEmpty();
        PlcCpu cpu = running(sfc);
        cpu.scan(DT, true, di(i -> false), NO_AI);
        assertThat(cpu.output(0)).as("idle").isFalse();
        cpu.scan(DT, true, di(i -> i == 0), NO_AI);
        cpu.scan(DT, true, di(i -> false), NO_AI);
        assertThat(cpu.output(0)).as("filling").isTrue();
        cpu.scan(DT, true, di(i -> i == 1), NO_AI);
        cpu.scan(DT, true, di(i -> false), NO_AI);
        assertThat(cpu.output(0)).as("fill done").isFalse();
        assertThat(cpu.output(1)).as("heater action").isTrue();
        assertThat(cpu.output(2)).as("alarm set").isTrue();
        cpu.scan(DT, true, di(i -> i == 2), NO_AI);
        cpu.scan(DT, true, di(i -> false), NO_AI);
        assertThat(cpu.output(2)).as("S stays set after the step").isTrue();
        cpu.scan(DT, true, di(i -> i == 0), NO_AI);
        cpu.scan(DT, true, di(i -> false), NO_AI);
        assertThat(cpu.output(0)).as("next cycle").isTrue();
    }

    @Test
    void errorsPointAtTheSourceLine() {
        var ld = PlcLanguage.compile("(*LD*)\nXIC(%IX0.0) OTE(%QX0.0)\nXIC(%IX0.1) FOO(%QX0.1)\n");
        assertThat(ld.errors()).hasSize(1);
        assertThat(ld.errors().get(0).line()).isEqualTo(3);
        var sfc = PlcLanguage.compile("(*SFC*)\nSTEP A: END_STEP\n");
        assertThat(sfc.errors()).isNotEmpty();
    }
}
